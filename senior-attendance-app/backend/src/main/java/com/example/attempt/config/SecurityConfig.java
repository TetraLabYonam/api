package com.example.attempt.config;

import com.example.attempt.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 설정
 * JWT 기반 인증, 권한 규칙, CORS 설정 등을 구성
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Value("${app.allowed-origins:}")
    private String allowedOrigins; // CSV from env

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            // REST + JWT 사용 시 CSRF 비활성화 (쿠키 기반 인증 시 재검토 필요)
            .csrf(csrf -> csrf.disable())
            // Stateless 세션 정책 (JWT 사용)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    // 인증 불필요 엔드포인트
                    .requestMatchers("/api/auth/**", "/api/v1/member-auth/**", "/api/devices/register", "/ws/**", "/actuator/health").permitAll()
                    // 읽기 전용 API는 인증 불필요
                    .requestMatchers(HttpMethod.GET, "/api/place/**", "/api/v1/member/**").permitAll()
                    // 회원 본인 서비스 및 장소 검색 API는 MEMBER 권한 필요
                    .requestMatchers("/api/v1/members/me/**", "/api/v1/places/**", "/api/v1/attend/**").hasRole("MEMBER")
                    // 관리자 전용 API
                    .requestMatchers("/api/v1/admin/**", "/api/v1/rooms/**").hasRole("ADMIN")
                    // 그 외 모든 요청은 인증 필요
                    .anyRequest().authenticated()
            )
            // 인증되지 않은 요청은 401(Unauthorized)로 응답한다.
            // (미설정 시 Spring Security 기본 진입점이 403을 반환해 "미인증"과
            //  "인증됐지만 권한 부족"을 구분할 수 없다.)
            .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint((request, response, authException) ->
                            response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
            );

        // JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 추가
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 비밀번호 암호화를 위한 BCrypt 인코더
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * AuthenticationManager Bean 등록
     * 로그인 처리 등에 사용
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
