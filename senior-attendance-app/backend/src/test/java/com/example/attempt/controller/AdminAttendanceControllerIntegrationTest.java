package com.example.attempt.controller;

import com.example.attempt.service.SmsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminAttendanceControllerIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

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

    /**
     * MemberAuthControllerIntegrationTest와 동일한 방식으로 실제 ROLE_MEMBER accessToken을 얻는다.
     */
    private String obtainMemberAccessToken(String phoneNumber) {
        String memberAuthBase = "http://localhost:" + port + "/api/v1/member-auth";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.postForEntity(memberAuthBase + "/otp/request",
                new HttpEntity<>(Map.of("phoneNumber", phoneNumber), headers), Void.class);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(smsService).sendCustomMessage(eq(phoneNumber), messageCaptor.capture());
        String capturedMessage = messageCaptor.getValue();
        String code = capturedMessage.replaceAll(".*인증번호는 (\\d+) .*", "$1");

        HttpEntity<Map<String, String>> verifyReq = new HttpEntity<>(
                Map.of("phoneNumber", phoneNumber, "code", code), headers);
        ResponseEntity<Map> verifyResp = restTemplate.postForEntity(
                memberAuthBase + "/otp/verify", verifyReq, Map.class);
        return (String) verifyResp.getBody().get("accessToken");
    }

    @Test
    void getSummary_withoutAuth_returns401() {
        String url = "http://localhost:" + port + "/api/admin/attendance/summary?period=today";
        ResponseEntity<Object> resp = restTemplate.getForEntity(url, Object.class);
        assertEquals(401, resp.getStatusCodeValue());
    }

    @Test
    void getSummary_withMemberToken_returns403() {
        String accessToken = obtainMemberAccessToken("01099998888");
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
