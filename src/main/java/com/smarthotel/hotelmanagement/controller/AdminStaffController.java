package com.smarthotel.hotelmanagement.controller;

import com.smarthotel.hotelmanagement.entity.Role;
import com.smarthotel.hotelmanagement.entity.User;
import com.smarthotel.hotelmanagement.repository.RoleRepository;
import com.smarthotel.hotelmanagement.repository.UserRepository;
import com.smarthotel.hotelmanagement.service.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/staff")
public class AdminStaffController {

    private static final String ROLE_STAFF = "ROLE_STAFF";
    private static final String ROLE_MANAGER = "ROLE_MANAGER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AdminStaffController(UserRepository userRepository,
                                RoleRepository roleRepository,
                                PasswordEncoder passwordEncoder,
                                AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public static class CreateStaffRequest {
        @NotBlank(message = "Full name is required")
        private String fullName;

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        @NotBlank(message = "Password is required")
        private String password;

        @NotBlank(message = "Role is required")
        private String role;

        private String phone;

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }
    }

    public static class UpdateStaffRoleRequest {
        @NotBlank(message = "Role is required")
        private String role;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }

    @GetMapping
    public List<Map<String, Object>> listStaffAndManagers() {
        return userRepository.findAll().stream()
                .filter(user -> hasRole(user, ROLE_STAFF) || hasRole(user, ROLE_MANAGER))
                .sorted(Comparator.comparing(User::getId))
                .map(user -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", user.getId());
                    row.put("fullName", user.getFullName());
                    row.put("email", user.getEmail());
                    row.put("phone", user.getPhone());
                    row.put("role", hasRole(user, ROLE_MANAGER) ? "MANAGER" : "STAFF");
                    return row;
                })
                .collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<?> createStaffOrManager(@Valid @RequestBody CreateStaffRequest request, Authentication authentication) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Email already registered"));
        }

        String roleName = normalizeRoleName(request.getRole());
        if (roleName == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Role must be STAFF or MANAGER"));
        }

        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));

        User user = new User(
                normalizedEmail,
                passwordEncoder.encode(request.getPassword()),
                request.getFullName().trim()
        );
        user.setRoles(Set.of(role));
        if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
            user.setPhone(request.getPhone().trim());
        }
        User saved = userRepository.save(user);

        auditService.log(
                authentication != null ? authentication.getName() : "system",
                "CREATE",
                "USER",
                saved.getId(),
                "Created " + (ROLE_MANAGER.equals(roleName) ? "manager" : "staff") + " account: " + saved.getEmail()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toUserResponse(saved, roleName));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<?> deleteStaffOrManager(@PathVariable Long userId, Authentication authentication) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        }

        if (!(hasRole(user, ROLE_STAFF) || hasRole(user, ROLE_MANAGER))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Only staff or manager accounts can be removed here"));
        }

        String removedEmail = user.getEmail();
        userRepository.delete(user);

        auditService.log(
                authentication != null ? authentication.getName() : "system",
                "DELETE",
                "USER",
                userId,
                "Removed staff/manager account: " + removedEmail
        );

        return ResponseEntity.ok(Map.of("message", "User removed successfully"));
    }

    @PatchMapping("/{userId}/role")
    public ResponseEntity<?> updateStaffOrManagerRole(@PathVariable Long userId,
                                                      @Valid @RequestBody UpdateStaffRoleRequest request,
                                                      Authentication authentication) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        }

        if (!(hasRole(user, ROLE_STAFF) || hasRole(user, ROLE_MANAGER))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Only staff or manager accounts can be updated here"));
        }

        String roleName = normalizeRoleName(request.getRole());
        if (roleName == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Role must be STAFF or MANAGER"));
        }

        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));
        user.setRoles(Set.of(role));
        User saved = userRepository.save(user);

        auditService.log(
                authentication != null ? authentication.getName() : "system",
                "UPDATE",
                "USER",
                saved.getId(),
                "Updated role for " + saved.getEmail() + " to " + (ROLE_MANAGER.equals(roleName) ? "MANAGER" : "STAFF")
        );

        return ResponseEntity.ok(toUserResponse(saved, roleName));
    }

    private boolean hasRole(User user, String roleName) {
        return user.getRoles() != null
                && user.getRoles().stream().anyMatch(role -> roleName.equalsIgnoreCase(role.getName()));
    }

    private String normalizeRoleName(String roleInput) {
        if (roleInput == null) {
            return null;
        }
        String clean = roleInput.trim().toUpperCase();
        if ("STAFF".equals(clean) || ROLE_STAFF.equals(clean)) {
            return ROLE_STAFF;
        }
        if ("MANAGER".equals(clean) || ROLE_MANAGER.equals(clean)) {
            return ROLE_MANAGER;
        }
        return null;
    }

    private Map<String, Object> toUserResponse(User user, String roleName) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("fullName", user.getFullName());
        response.put("email", user.getEmail());
        response.put("phone", user.getPhone());
        response.put("role", ROLE_MANAGER.equals(roleName) ? "MANAGER" : "STAFF");
        return response;
    }
}
