package com.smarthotel.hotelmanagement.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int LOGIN_LIMIT_PER_MINUTE = 20;
    private static final int PAYMENT_LIMIT_PER_MINUTE = 40;
    private static final long WINDOW_SECONDS = 60;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        int limit = resolveLimit(path, method);
        if (limit <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = path + ":" + clientIp(request);
        long now = Instant.now().getEpochSecond();
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(now, 0));
        synchronized (bucket) {
            if (now - bucket.windowStart >= WINDOW_SECONDS) {
                bucket.windowStart = now;
                bucket.count = 0;
            }
            bucket.count++;
            if (bucket.count > limit) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Too many requests. Please try again shortly.\",\"status\":429}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private int resolveLimit(String path, String method) {
        if ("POST".equalsIgnoreCase(method) && "/api/auth/login".equals(path)) return LOGIN_LIMIT_PER_MINUTE;
        if (path != null && path.startsWith("/api/customer/bookings")) return PAYMENT_LIMIT_PER_MINUTE;
        if ("POST".equalsIgnoreCase(method) && "/api/payments/stripe/webhook".equals(path)) return PAYMENT_LIMIT_PER_MINUTE;
        return 0;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            return parts[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class Bucket {
        long windowStart;
        int count;

        Bucket(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
