package com.example.attempt.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

@Configuration
public class SwaggerConfig {
    //http://localhost:8080/swagger-ui/index.html
    @Bean
    public OpenAPI openAPI() {
        Server server = new Server();   // API 서버 설정
        server.setUrl("/");

        Server prodServer = new Server();  // 운영서버에 따로 띄우기 위해 서버를 추가할 수 있다.
        prodServer.setUrl("운영 URL");

        Info info = new Info()
                .title("Swagger API")       // API 문서 제목
                .version("v1.0.0")          // API 문서 버전
                .description("스웨거 API"); // API 문서 설명

        return new OpenAPI()
                .info(info)
                .servers(List.of(server, prodServer));
    }
}
