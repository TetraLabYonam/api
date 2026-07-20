package com.example.attempt.controller;

import com.example.attempt.repository.MemberRepository;
import com.example.attempt.service.SmsService;
import com.example.attempt.support.MemberAuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminAttendanceControllerIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockBean
    SmsService smsService;

    private String obtainAdminAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> req = new HttpEntity<>(
                Map.of("username", "admin@example.com", "password", "1234"), headers);
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/auth/login", req, Map.class);
        return (String) resp.getBody().get("accessToken");
    }

    @Test
    void getSummary_withoutAuth_returns401() {
        String url = "http://localhost:" + port + "/api/admin/attendance/summary?period=today";
        ResponseEntity<Object> resp = restTemplate.getForEntity(url, Object.class);
        assertEquals(401, resp.getStatusCodeValue());
    }

    @Test
    void getSummary_withMemberToken_returns403() {
        String accessToken = MemberAuthTestSupport.loginAsMember(
                restTemplate, port, memberRepository, passwordEncoder, "김할매", "01099998888");
        assertNotNull(accessToken, "Member accessToken should not be null");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        String url = "http://localhost:" + port + "/api/admin/attendance/summary?period=today";
        ResponseEntity<Object> resp = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Object.class);

        assertEquals(403, resp.getStatusCodeValue());
    }

    @Test
    void getSummary_withAdminToken_returnsAllThreeUnitTypes() {
        String accessToken = obtainAdminAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        String url = "http://localhost:" + port + "/api/admin/attendance/summary?period=today";
        ResponseEntity<Object[]> resp = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Object[].class);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(3, resp.getBody().length);
    }

    @Test
    void getSummary_withInvalidPeriod_returns400() {
        String accessToken = obtainAdminAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        String url = "http://localhost:" + port + "/api/admin/attendance/summary?period=year";
        ResponseEntity<Object> resp = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Object.class);

        assertEquals(400, resp.getStatusCodeValue());
    }
}
