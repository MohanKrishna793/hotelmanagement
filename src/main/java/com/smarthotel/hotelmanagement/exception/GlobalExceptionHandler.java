package com.smarthotel.hotelmanagement.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import jakarta.persistence.LockTimeoutException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralized exception handling for REST APIs. Returns consistent JSON error responses
 * and logs for viva/demonstration and production debugging.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        log.warn("API error: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(Map.of(
                        "message", ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString(),
                        "status", ex.getStatusCode().value()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", errors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "message", "Validation failed",
                        "errors", errors,
                        "status", 400
                ));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Login failed: bad credentials");
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "message", "Invalid email or password. Please try again.",
                        "status", 401
                ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "message", "Access denied",
                        "status", 403
                ));
    }

    @ExceptionHandler(LockTimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleLockTimeout(LockTimeoutException ex) {
        log.warn("JPA lock timeout: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "message", "This room is temporarily locked (another booking may be in progress). Please try again in a few seconds.",
                        "status", 409
                ));
    }

    @ExceptionHandler(QueryTimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleQueryTimeout(QueryTimeoutException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String detail = cause != null ? cause.getMessage() : ex.getMessage();
        log.warn("Query timeout: {}", detail);
        boolean lockWait = detail != null && (detail.contains("locking") || detail.contains("lock"));
        HttpStatus status = lockWait ? HttpStatus.CONFLICT : HttpStatus.SERVICE_UNAVAILABLE;
        String message = lockWait
                ? "Could not reserve the room right now (database lock). Please try again in a few seconds."
                : "The database took too long to respond. Please try again.";
        return ResponseEntity
                .status(status)
                .body(Map.of(
                        "message", message,
                        "status", status.value()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled error", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "message", "An unexpected error occurred. Please try again.",
                        "status", 500
                ));
    }
}
