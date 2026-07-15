package com.example.attempt.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void loginAndRefresh_flow() {
        String base = "http://localhost:" + port + "/api/auth";

        // Ensure an admin exists (AdminDataInitializer should create one in tests)
        Map<String, String> body = Map.of("username", "admin@example.com", "password", "1234");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> req = new HttpEntity<>(body, headers);
        ResponseEntity<Map> loginResp = restTemplate.postForEntity(base + "/login", req, Map.class);

        assertEquals(200, loginResp.getStatusCodeValue());
        assertNotNull(loginResp.getBody().get("accessToken"));

        // Extract cookie
        String setCookie = loginResp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("refreshToken="));

        // Call refresh with cookie
        HttpHeaders h2 = new HttpHeaders();
        h2.add(HttpHeaders.COOKIE, setCookie);
        HttpEntity<Void> req2 = new HttpEntity<>(h2);
        ResponseEntity<Map> refreshResp = restTemplate.postForEntity(base + "/refresh", req2, Map.class);

        assertEquals(200, refreshResp.getStatusCodeValue());
        assertNotNull(refreshResp.getBody().get("accessToken"));
        String newSetCookie = refreshResp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(newSetCookie);
        assertTrue(newSetCookie.contains("refreshToken="));
    }
}
