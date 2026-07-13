package com.example.attempt.service;

import com.example.attempt.domain.MemberOtpCode;
import com.example.attempt.repository.MemberOtpCodeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * 참여자(Member) 휴대폰번호 + SMS OTP 인증
 * 코드는 평문 저장하지 않고 HMAC-SHA256 해시로 저장한다 (RefreshTokenServiceImpl과 동일 패턴)
 */
@Service
@Slf4j
public class MemberOtpService {

    private final MemberOtpCodeRepository repository;
    private final SmsService smsService;
    private final String hashSecret;
    private final int ttlSeconds;
    private final int codeLength;
    private final SecureRandom secureRandom = new SecureRandom();

    public MemberOtpService(MemberOtpCodeRepository repository,
                             SmsService smsService,
                             @Value("${member-otp.hash-secret}") String hashSecret,
                             @Value("${member-otp.ttl-seconds:300}") int ttlSeconds,
                             @Value("${member-otp.code-length:6}") int codeLength) {
        this.repository = repository;
        this.smsService = smsService;
        this.hashSecret = hashSecret;
        this.ttlSeconds = ttlSeconds;
        this.codeLength = codeLength;
        if (this.hashSecret == null || this.hashSecret.isBlank()) {
            throw new IllegalStateException("member-otp.hash-secret (MEMBER_OTP_HASH_SECRET) must be set");
        }
    }

    @Transactional
    public void requestOtp(String phoneNumber) {
        String code = generateNumericCode(codeLength);
        String hash = hmacSha256Base64(code);

        MemberOtpCode entity = new MemberOtpCode(phoneNumber, hash, LocalDateTime.now().plusSeconds(ttlSeconds));
        repository.save(entity);

        smsService.sendCustomMessage(phoneNumber, "[시니어일자리] 인증번호는 " + code + " 입니다. " + (ttlSeconds / 60) + "분 이내에 입력해주세요.");
        log.info("OTP 발급: phoneNumber={}", phoneNumber);
    }

    @Transactional(readOnly = true)
    public boolean verifyOtp(String phoneNumber, String code) {
        Optional<MemberOtpCode> latest = repository.findTopByPhoneNumberOrderByCreatedAtDesc(phoneNumber);
        if (latest.isEmpty()) {
            return false;
        }

        MemberOtpCode otp = latest.get();
        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("OTP 만료: phoneNumber={}", phoneNumber);
            return false;
        }

        boolean matches = hmacSha256Base64(code).equals(otp.getCodeHash());
        if (!matches) {
            log.warn("OTP 불일치: phoneNumber={}", phoneNumber);
        }
        return matches;
    }

    private String generateNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(secureRandom.nextInt(10));
        }
        return sb.toString();
    }

    private String hmacSha256Base64(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
