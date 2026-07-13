package com.example.attempt.service;

import java.time.Duration;
import java.util.Optional;

public interface RefreshTokenService {
    /**
     * Create a raw refresh token and persist its hash.
     * Returns the raw token to be set in the cookie.
     */
    String createRefreshToken(String username, String deviceId, Duration ttl);

    /**
     * Consume a raw refresh token (single-use). If successful returns the username.
     */
    Optional<String> consumeRefreshToken(String rawToken);

    /**
     * Revoke a refresh token by raw value.
     */
    void revokeRefreshToken(String rawToken);

    /**
     * Revoke all tokens for a user.
     */
    void revokeAllForUser(String username);
}
