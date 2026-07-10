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
class MemberAuthControllerIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void requestOtp_returns200() {
        String base = "http://localhost:" + port + "/api/v1/member-auth";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> req = new HttpEntity<>(Map.of("phoneNumber", "01011112222"), headers);

        ResponseEntity<Void> resp = restTemplate.postForEntity(base + "/otp/request", req, Void.class);

        assertEquals(200, resp.getStatusCodeValue());
    }

    @Test
    void verifyOtp_withWrongCode_returns401() {
        String base = "http://localhost:" + port + "/api/v1/member-auth";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.postForEntity(base + "/otp/request",
                new HttpEntity<>(Map.of("phoneNumber", "01033334444"), headers), Void.class);

        HttpEntity<Map<String, String>> verifyReq = new HttpEntity<>(
                Map.of("phoneNumber", "01033334444", "code", "000000"), headers);
        ResponseEntity<Map> resp = restTemplate.postForEntity(base + "/otp/verify", verifyReq, Map.class);

        assertEquals(401, resp.getStatusCodeValue());
    }
}
