package com.ordering.userservice.util;

import com.ordering.userservice.entity.User;
import com.ordering.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() > 0) {
            return;
        }

        List<User> users = List.of(
                createUser(
                        "customer",
                        "customer@example.com",
                        "555-1001",
                        "password123",
                        Role.ROLE_CUSTOMER
                ),
                createUser(
                        "vendor",
                        "vendor@example.com",
                        "555-1002",
                        "password123",
                        Role.ROLE_VENDOR
                ),
                createUser(
                        "admin",
                        "admin@example.com",
                        "555-1003",
                        "password123",
                        Role.ROLE_ADMIN
                )
        );

        userRepository.saveAll(users);

        System.out.println("✅ Demo users initialized: " + users.size());
    }

    private User createUser(
            String username,
            String email,
            String phone,
            String rawPassword,
            Role role) {

        User user = new User();

        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setCreatedAt(LocalDateTime.now());

        return user;
    }

}

