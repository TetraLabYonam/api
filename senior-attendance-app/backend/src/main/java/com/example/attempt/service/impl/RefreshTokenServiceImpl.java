package com.example.attempt.service.impl;

import com.example.attempt.domain.RefreshToken;
import com.example.attempt.repository.RefreshTokenRepository;
import com.example.attempt.service.RefreshTokenService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String hashSecret;

    public RefreshTokenServiceImpl(RefreshTokenRepository repository,
                                   @Value("${refresh-token.hash-secret}") String hashSecret) {
        this.repository = repository;
        this.hashSecret = hashSecret;
        if (this.hashSecret == null || this.hashSecret.isBlank()) {
            throw new IllegalStateException("REFRESH_TOKEN_HASH_SECRET must be set");
        }
    }

    @Override
    public String createRefreshToken(String username, String deviceId, Duration ttl) {
        byte[] raw = new byte[64];
        secureRandom.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        String hash = hmacSha256Base64(token, hashSecret);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expires = now.plusSeconds(ttl.getSeconds());

        RefreshToken entity = new RefreshToken(username, hash, deviceId, now, expires);
        repository.save(entity);
        return token;
    }

    @Override
    @Transactional
    public Optional<String> consumeRefreshToken(String rawToken) {
        String hash = hmacSha256Base64(rawToken, hashSecret);
        LocalDateTime now = LocalDateTime.now();
        int updated = repository.revokeByHash(hash, now);
        if (updated == 1) {
            Optional<RefreshToken> rt = repository.findByTokenHash(hash);
            return rt.map(r -> r.getUsername());
        }
        return Optional.empty();
    }

    @Override
    @Transactional
    public void revokeRefreshToken(String rawToken) {
        String hash = hmacSha256Base64(rawToken, hashSecret);
        repository.revokeByHash(hash, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void revokeAllForUser(String username) {
        repository.deleteByUsernameAndExpiresAtBefore(username, LocalDateTime.now().plusYears(100));
    }

    private String hmacSha256Base64(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
