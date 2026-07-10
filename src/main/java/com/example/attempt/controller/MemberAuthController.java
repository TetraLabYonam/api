package com.example.attempt.controller;

import com.example.attempt.domain.Member;
import com.example.attempt.dto.memberauth.OtpRequestRequest;
import com.example.attempt.dto.memberauth.OtpVerifyRequest;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.security.JwtTokenProvider;
import com.example.attempt.service.MemberOtpService;
import com.example.attempt.service.RefreshTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/member-auth")
@RequiredArgsConstructor
public class MemberAuthController {

    private final MemberOtpService memberOtpService;
    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Value("${jwt.refresh-exp-ms:1209600000}")
    private long refreshExpMs;

    @Value("${refresh-token.cookie.secure:false}")
    private boolean cookieSecure;

    @PostMapping("/otp/request")
    public ResponseEntity<?> requestOtp(@Valid @RequestBody OtpRequestRequest request) {
        memberOtpService.requestOtp(request.getPhoneNumber());
        return ResponseEntity.ok(Map.of("message", "인증번호가 발송되었습니다."));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        boolean valid = memberOtpService.verifyOtp(request.getPhoneNumber(), request.getCode());
        if (!valid) {
            return ResponseEntity.status(401).body(Map.of("error", "인증번호가 올바르지 않거나 만료되었습니다."));
        }

        Member member = memberRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseGet(() -> memberRepository.save(new Member("참여자", request.getPhoneNumber())));

        // memberId는 토큰 클레임에 넣지 않는다 — CurrentMemberService가 매 요청마다
        // subject(phoneNumber)로 Member를 직접 조회하므로 클레임에 넣어도 쓰이지 않는다.
        // 응답 바디의 memberId는 클라이언트 표시/로깅 편의용이다.
        String accessToken = jwtTokenProvider.createAccessToken(
                member.getPhoneNumber(),
                Map.of("roles", new String[]{"ROLE_MEMBER"})
        );

        String rawRefresh = refreshTokenService.createRefreshToken(member.getPhoneNumber(), null, Duration.ofMillis(refreshExpMs));
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", rawRefresh)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/v1/member-auth/refresh")
                .maxAge(refreshExpMs / 1000)
                .sameSite("Strict")
                .build();

        return ResponseEntity.ok()
                .header("Set-Cookie", refreshCookie.toString())
                .body(Map.of("accessToken", accessToken, "memberId", member.getId()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = "refreshToken", required = false) String refreshToken) {
        if (refreshToken == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Missing refresh token"));
        }

        Optional<String> optPhoneNumber = refreshTokenService.consumeRefreshToken(refreshToken);
        if (optPhoneNumber.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired refresh token"));
        }

        String phoneNumber = optPhoneNumber.get();
        Optional<Member> optMember = memberRepository.findByPhoneNumber(phoneNumber);
        if (optMember.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired refresh token"));
        }
        Member member = optMember.get();

        String accessToken = jwtTokenProvider.createAccessToken(
                member.getPhoneNumber(),
                Map.of("roles", new String[]{"ROLE_MEMBER"})
        );

        String newRaw = refreshTokenService.createRefreshToken(phoneNumber, null, Duration.ofMillis(refreshExpMs));
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", newRaw)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/v1/member-auth/refresh")
                .maxAge(refreshExpMs / 1000)
                .sameSite("Strict")
                .build();

        return ResponseEntity.ok()
                .header("Set-Cookie", refreshCookie.toString())
                .body(Map.of("accessToken", accessToken));
    }
}
