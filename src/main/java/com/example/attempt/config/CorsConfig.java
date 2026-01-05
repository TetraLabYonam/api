package com.example.attempt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 설정 클래스
 * Flutter 모바일 앱과의 통신을 위한 Cross-Origin Resource Sharing 설정
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.allowed-origins:http://localhost:5173,http://10.0.2.2:8080}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 환경 변수 ALLOWED_ORIGINS 또는 app.allowed-origins 프로퍼티에서 허용 출처 목록 읽기
        String envOrigins = System.getenv("ALLOWED_ORIGINS");
        String[] origins;

        if (envOrigins != null && !envOrigins.trim().isEmpty()) {
            origins = envOrigins.split(",");
        } else if (allowedOrigins != null && !allowedOrigins.trim().isEmpty()) {
            origins = allowedOrigins.split(",");
        } else {
            // 기본값: 개발 환경 출처
            origins = new String[]{"http://localhost:5173", "http://10.0.2.2:8080"};
        }

        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);  // preflight 요청 캐시 시간 (1시간)
    }
}
