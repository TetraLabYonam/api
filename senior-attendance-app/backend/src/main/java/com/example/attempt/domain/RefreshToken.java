package com.example.attempt.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_token")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false)
    private String username;

    @Column(name = "token_hash", length = 128, nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    public RefreshToken(String username, String tokenHash, String deviceId, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        this.username = username;
        this.tokenHash = tokenHash;
        this.deviceId = deviceId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }
}
