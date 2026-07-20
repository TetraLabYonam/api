package com.example.attempt.service;

import com.example.attempt.domain.Admin;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.AdminRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentAdminServiceTest {

    private AdminRepository adminRepository;
    private CurrentAdminService service;

    @BeforeEach
    void setup() {
        adminRepository = mock(AdminRepository.class);
        service = new CurrentAdminService(adminRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentAdmin_looksUpByAuthenticatedUsername() {
        Admin admin = new Admin("admin@example.com", "hashed");
        when(adminRepository.findByUsername("admin@example.com")).thenReturn(Optional.of(admin));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@example.com", null, List.of()));

        Admin result = service.getCurrentAdmin();

        assertEquals("admin@example.com", result.getUsername());
    }

    @Test
    void getCurrentAdmin_throws_whenNoAdminForUsername() {
        when(adminRepository.findByUsername("ghost@example.com")).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ghost@example.com", null, List.of()));

        assertThrows(ResourceNotFoundException.class, () -> service.getCurrentAdmin());
    }
}
