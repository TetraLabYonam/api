package com.example.attempt.controller;

import com.example.attempt.domain.Admin;
import com.example.attempt.repository.AdminRepository;
import com.example.attempt.security.JwtTokenProvider;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * 인증 관련 API 컨트롤러
 * 로그인, 토큰 갱신, 로그아웃 기능 제공
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(AdminRepository adminRepository,
                          PasswordEncoder passwordEncoder,
                          JwtTokenProvider jwtTokenProvider) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 로그인
     * @param body username, password
     * @return accessToken (JSON), refreshToken (HttpOnly Cookie)
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        Optional<Admin> opt = adminRepository.findByUsername(username);
        if (opt.isEmpty() || !passwordEncoder.matches(password, opt.get().getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }

        Admin admin = opt.get();
        String accessToken = jwtTokenProvider.createAccessToken(
                admin.getUsername(),
                Map.of("roles", new String[] {"ROLE_ADMIN"})
        );
        String refreshToken = jwtTokenProvider.createRefreshToken(admin.getUsername());

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(false) // 운영 환경: true (HTTPS 필수)
                .path("/api/auth/refresh")
                .maxAge(60 * 60 * 24 * 30) // 30일
                .sameSite("Strict")
                .build();

        return ResponseEntity.ok()
                .header("Set-Cookie", refreshCookie.toString())
                .body(Map.of("accessToken", accessToken));
    }

    /**
     * Access Token 갱신
     * @param refreshToken HttpOnly Cookie로 전달
     * @return 새로운 accessToken
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = "refreshToken", required = false) String refreshToken) {
        if (refreshToken == null || !jwtTokenProvider.validateToken(refreshToken)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid refresh token"));
        }

        String subject = jwtTokenProvider.getClaims(refreshToken).getSubject();
        String accessToken = jwtTokenProvider.createAccessToken(
                subject,
                Map.of("roles", new String[] {"ROLE_ADMIN"})
        );

        return ResponseEntity.ok(Map.of("accessToken", accessToken));
    }

    /**
     * 로그아웃
     * refreshToken 쿠키 삭제
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        ResponseCookie deleteCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false) // 운영 환경: true
                .path("/api/auth/refresh")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        // TODO: refresh token blacklist 구현 (서버 측 토큰 무효화)

        return ResponseEntity.ok()
                .header("Set-Cookie", deleteCookie.toString())
                .body(Map.of("message", "logged out"));
    }
}
