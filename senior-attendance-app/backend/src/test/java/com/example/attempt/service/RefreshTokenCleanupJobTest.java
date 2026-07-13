package com.example.attempt.service;

import com.example.attempt.domain.RefreshToken;
import com.example.attempt.repository.RefreshTokenRepository;
import com.example.attempt.service.impl.RefreshTokenCleanupJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RefreshTokenCleanupJobTest {

    @Autowired
    RefreshTokenRepository repository;

    @Autowired
    RefreshTokenCleanupJob job;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    @Test
    void cleanupDeletesExpiredTokens() {
        RefreshToken expired = new RefreshToken("u1", "hash1", null, LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1));
        RefreshToken valid = new RefreshToken("u2", "hash2", null, LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        repository.save(expired);
        repository.save(valid);

        job.cleanupExpired();

        assertTrue(repository.findByTokenHash("hash1").isEmpty());
        assertTrue(repository.findByTokenHash("hash2").isPresent());
    }
}
