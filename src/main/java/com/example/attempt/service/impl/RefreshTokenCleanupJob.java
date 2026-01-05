package com.example.attempt.service.impl;

import com.example.attempt.repository.RefreshTokenRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository repository;

    public RefreshTokenCleanupJob(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    // Run every day at 03:00
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupExpired() {
        repository.deleteByUsernameAndExpiresAtBefore("__ALL__", java.time.LocalDateTime.now());
    }
}
