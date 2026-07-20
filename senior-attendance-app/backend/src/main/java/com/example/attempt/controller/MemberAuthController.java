package com.example.attempt.controller;

import com.example.attempt.domain.Member;
import com.example.attempt.dto.memberauth.MemberLoginRequest;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.security.JwtTokenProvider;
import com.example.attempt.service.RefreshTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/member-auth")
@RequiredArgsConstructor
public class MemberAuthController {

    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.refresh-exp-ms:1209600000}")
    private long refreshExpMs;

    @Value("${refresh-token.cookie.secure:false}")
    private boolean cookieSecure;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody MemberLoginRequest request) {
        Optional<Member> opt = memberRepository.findByEmployeeId(request.getEmployeeId());
        if (opt.isEmpty() || !opt.get().isActive()
                || !passwordEncoder.matches(request.getPhoneNumber(), opt.get().getPhoneNumberHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "직번 또는 전화번호가 올바르지 않습니다"));
        }
        Member member = opt.get();
        String subject = member.getEmployeeId().toString();
        String accessToken = jwtTokenProvider.createAccessToken(subject, Map.of("roles", new String[]{"ROLE_MEMBER"}));
        String rawRefresh = refreshTokenService.createRefreshToken(subject, null, Duration.ofMillis(refreshExpMs));
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", rawRefresh)
                .httpOnly(true).secure(cookieSecure).path("/api/v1/member-auth/refresh")
                .maxAge(refreshExpMs / 1000).sameSite("Strict").build();
        return ResponseEntity.ok().header("Set-Cookie", refreshCookie.toString())
                .body(Map.of("accessToken", accessToken, "memberId", member.getId()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = "refreshToken", required = false) String refreshToken) {
        if (refreshToken == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Missing refresh token"));
        }

        Optional<String> optSubject = refreshTokenService.consumeRefreshToken(refreshToken);
        if (optSubject.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired refresh token"));
        }

        String subject = optSubject.get();
        Optional<Member> optMember = memberRepository.findByEmployeeId(Long.parseLong(subject));
        if (optMember.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired refresh token"));
        }
        Member member = optMember.get();

        String accessToken = jwtTokenProvider.createAccessToken(
                member.getEmployeeId().toString(),
                Map.of("roles", new String[]{"ROLE_MEMBER"})
        );

        String newRaw = refreshTokenService.createRefreshToken(subject, null, Duration.ofMillis(refreshExpMs));
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
