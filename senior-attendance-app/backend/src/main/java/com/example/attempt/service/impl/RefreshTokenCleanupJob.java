package com.example.attempt.service.impl;

import com.example.attempt.repository.RefreshTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository repository;

    public RefreshTokenCleanupJob(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    // Run every day at 03:00
    // @Transactional is required here: @Scheduled methods run outside any
    // surrounding transaction, and the @Modifying bulk delete needs one.
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpired() {
        repository.deleteByExpiresAtBefore(java.time.LocalDateTime.now());
    }
}
