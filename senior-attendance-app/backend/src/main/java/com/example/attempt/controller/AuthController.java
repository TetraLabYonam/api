package com.example.attempt.controller;

import com.example.attempt.domain.Admin;
import com.example.attempt.repository.AdminRepository;
import com.example.attempt.security.JwtTokenProvider;
import com.example.attempt.service.RefreshTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
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
    private final RefreshTokenService refreshTokenService;

    @Value("${jwt.refresh-exp-ms:1209600000}")
    private long refreshExpMs;

    @Value("${refresh-token.cookie.secure:false}")
    private boolean cookieSecure;

    public AuthController(AdminRepository adminRepository,
                          PasswordEncoder passwordEncoder,
                          JwtTokenProvider jwtTokenProvider,
                          RefreshTokenService refreshTokenService) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
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
        String deviceId = body.get("deviceId");

        Optional<Admin> opt = adminRepository.findByUsername(username);
        if (opt.isEmpty() || !passwordEncoder.matches(password, opt.get().getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }

        Admin admin = opt.get();
        String accessToken = jwtTokenProvider.createAccessToken(
                admin.getUsername(),
                Map.of("roles", new String[] {"ROLE_ADMIN"})
        );

        // Create DB-backed refresh token (opaque) and set as HttpOnly cookie
        String rawRefresh = refreshTokenService.createRefreshToken(admin.getUsername(), deviceId, Duration.ofMillis(refreshExpMs));

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", rawRefresh)
                .httpOnly(true)
                .secure(cookieSecure) // 운영 환경: true (HTTPS 필수)
                .path("/api/auth/refresh")
                .maxAge(refreshExpMs / 1000)
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
        if (refreshToken == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Missing refresh token"));
        }

        Optional<String> optUsername = refreshTokenService.consumeRefreshToken(refreshToken);
        if (optUsername.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired refresh token"));
        }

        String username = optUsername.get();
        if (adminRepository.findByUsername(username).isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired refresh token"));
        }

        String accessToken = jwtTokenProvider.createAccessToken(
                username,
                Map.of("roles", new String[] {"ROLE_ADMIN"})
        );

        // Issue rotated refresh token
        String newRaw = refreshTokenService.createRefreshToken(username, null, Duration.ofMillis(refreshExpMs));
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", newRaw)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/auth/refresh")
                .maxAge(refreshExpMs / 1000)
                .sameSite("Strict")
                .build();

        return ResponseEntity.ok()
                .header("Set-Cookie", refreshCookie.toString())
                .body(Map.of("accessToken", accessToken));
    }

    /**
     * 로그아웃
     * refreshToken 쿠키 삭제
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(name = "refreshToken", required = false) String refreshToken) {
        if (refreshToken != null) {
            refreshTokenService.revokeRefreshToken(refreshToken);
        }

        ResponseCookie deleteCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/auth/refresh")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        return ResponseEntity.ok()
                .header("Set-Cookie", deleteCookie.toString())
                .body(Map.of("message", "logged out"));
    }
}
