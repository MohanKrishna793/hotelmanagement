package com.smarthotel.hotelmanagement.security;

import com.smarthotel.hotelmanagement.entity.Role;
import com.smarthotel.hotelmanagement.entity.User;
import com.smarthotel.hotelmanagement.repository.RoleRepository;
import com.smarthotel.hotelmanagement.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
@Order(1)
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(RoleRepository roleRepository,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN")));
        Role staffRole = roleRepository.findByName("ROLE_STAFF")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_STAFF")));
        Role managerRole = roleRepository.findByName("ROLE_MANAGER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_MANAGER")));
        Role customerRole = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER")));

        if (!userRepository.existsByEmail("admin@smarthotel.com")) {
            User admin = new User("admin@smarthotel.com",
                    passwordEncoder.encode("Admin@123"),
                    "SmartHotel Admin");
            admin.setRoles(Collections.singleton(adminRole));
            userRepository.save(admin);
        }
    }
}

