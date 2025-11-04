package com.example.attempt.config;

import com.corundumstudio.socketio.SocketIOServer;
import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@RequiredArgsConstructor
public class SocketIoConfig {

    private final Environment env;

    @Bean(destroyMethod = "stop")
    public com.corundumstudio.socketio.SocketIOServer socketIOServer() {
        var cfg = new com.corundumstudio.socketio.Configuration();
        cfg.setHostname(env.getProperty("app.socket.host", "0.0.0.0"));
        cfg.setPort(Integer.parseInt(env.getProperty("app.socket.port","9092")));
        // CORS 화이트리스트
        var origins = env.getProperty("app.socket.cors-origins", "");
        cfg.setOrigin(origins); // 콤마 구분 목록

        // 안정화 옵션
        cfg.getSocketConfig().setReuseAddress(true);
        cfg.getSocketConfig().setTcpNoDelay(true);

        var server = new SocketIOServer(cfg);
        server.start();
        return server;
    }
}
