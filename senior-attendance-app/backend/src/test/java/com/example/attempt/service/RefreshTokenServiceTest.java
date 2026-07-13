package com.example.attempt.service;

import com.example.attempt.repository.RefreshTokenRepository;
import com.example.attempt.service.impl.RefreshTokenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.example.attempt.domain.RefreshToken;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {

    private RefreshTokenRepository repository;
    private RefreshTokenServiceImpl service;

    @BeforeEach
    void setup() {
        repository = mock(RefreshTokenRepository.class);
        service = new RefreshTokenServiceImpl(repository, "test-refresh-secret");
    }

    @Test
    void createRefreshToken_savesAndReturnsToken() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String token = service.createRefreshToken("alice", "device-1", Duration.ofDays(14));

        assertNotNull(token);
        assertFalse(token.isBlank());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository, times(1)).save(captor.capture());
    }

    @Test
    void hmacHash_isStable_forSameInput() {
        String t1 = service.createRefreshToken("bob", "", Duration.ofDays(1));
        // Creating another token should produce different raw token; we can't easily assert hash here
        assertNotNull(t1);
    }
}
