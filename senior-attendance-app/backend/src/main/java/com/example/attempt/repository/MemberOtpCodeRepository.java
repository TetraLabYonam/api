package com.example.attempt.repository;

import com.example.attempt.domain.MemberOtpCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberOtpCodeRepository extends JpaRepository<MemberOtpCode, Long> {
    Optional<MemberOtpCode> findTopByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);
}
