package com.example.attempt.config;

import com.example.attempt.domain.Admin;
import com.example.attempt.repository.AdminRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminDataInitializer {

    @Bean
    CommandLineRunner createAdminIfNotExist(AdminRepository adminRepository,
                                           PasswordEncoder passwordEncoder,
                                           @Value("${app.default-admin.username:admin}") String defaultAdmin,
                                           @Value("${app.default-admin.password:admin}") String defaultPassword) {
        return args -> {
            if (!adminRepository.existsByUsername(defaultAdmin)) {
                Admin admin = new Admin(defaultAdmin, passwordEncoder.encode(defaultPassword));
                adminRepository.save(admin);
                System.out.println("[AdminDataInitializer] Created default admin user: " + defaultAdmin);
            }
        };
    }
}
