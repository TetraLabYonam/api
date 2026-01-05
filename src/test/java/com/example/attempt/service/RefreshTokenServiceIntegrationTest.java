package com.example.attempt.service;

import com.example.attempt.domain.RefreshToken;
import com.example.attempt.repository.RefreshTokenRepository;
import com.example.attempt.service.impl.RefreshTokenServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RefreshTokenServiceIntegrationTest {

    @Autowired
    RefreshTokenRepository repository;

    @Autowired
    RefreshTokenServiceImpl service;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    @Test
    void createAndConsume_singleUseBehavior() {
        String raw = service.createRefreshToken("tester", "dev", Duration.ofDays(1));
        assertNotNull(raw);

        Optional<String> consumed = service.consumeRefreshToken(raw);
        assertTrue(consumed.isPresent());
        assertEquals("tester", consumed.get());

        // Re-consume should fail (single-use)
        Optional<String> second = service.consumeRefreshToken(raw);
        assertTrue(second.isEmpty());
    }

    @Test
    void revokeRefreshToken_makesTokenInvalid() {
        String raw = service.createRefreshToken("u1", null, Duration.ofDays(1));
        assertNotNull(raw);

        service.revokeRefreshToken(raw);

        Optional<String> consumed = service.consumeRefreshToken(raw);
        assertTrue(consumed.isEmpty());
    }
}
