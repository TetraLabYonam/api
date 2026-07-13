package com.example.attempt.config;

import com.example.attempt.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * WebSocket 인증 인터셉터
 * STOMP CONNECT 시 Authorization 헤더에서 JWT를 추출하고 검증하여 Principal 설정
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    public WebSocketAuthInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        // STOMP CONNECT 명령어 처리
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> auth = accessor.getNativeHeader("Authorization");

            if (auth != null && !auth.isEmpty() && auth.get(0).startsWith("Bearer ")) {
                String token = auth.get(0).substring(7);

                if (jwtTokenProvider.validateToken(token)) {
                    Claims claims = jwtTokenProvider.getClaims(token);
                    String subject = claims.getSubject();

                    // Principal 설정 (WebSocket 세션에 user 정보 바인딩)
                    var authToken = new UsernamePasswordAuthenticationToken(subject, null, List.of());
                    accessor.setUser(authToken);
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        }

        // TODO: SUBSCRIBE / SEND 명령어에 대한 권한 검증 추가
        // - 특정 destination에 대한 접근 권한 확인
        // - userKey 일치 여부 검증 등

        return message;
    }
}
