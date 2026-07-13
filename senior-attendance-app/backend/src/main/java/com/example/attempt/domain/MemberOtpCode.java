package com.example.attempt.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "MEMBER_OTP_CODE")
@Getter
@Setter
@NoArgsConstructor
public class MemberOtpCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "PHONE_NUMBER", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "CODE_HASH", nullable = false, length = 128)
    private String codeHash;

    @Column(name = "EXPIRES_AT", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private int attempts = 0;

    @CreationTimestamp
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public MemberOtpCode(String phoneNumber, String codeHash, LocalDateTime expiresAt) {
        this.phoneNumber = phoneNumber;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }
}
