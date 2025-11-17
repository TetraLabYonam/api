package com.example.attempt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 설정 클래스
 * 번호표 실시간 업데이트를 위한 WebSocket 통신 설정
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 메시지 브로커 설정 - 클라이언트로 메시지를 전달하는 prefix
        config.enableSimpleBroker("/topic", "/queue");
        // 클라이언트에서 메시지를 보낼 때 사용하는 prefix
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket 엔드포인트 등록
        // Flutter 앱과 실시간 통신을 위한 엔드포인트
        registry.addEndpoint("/ws/queue")
                .setAllowedOriginPatterns("*")  // 모든 출처 허용 (모바일 앱 대응)
                .withSockJS();  // SockJS fallback 지원
    }
}