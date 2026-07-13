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
class AttendControllerIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @MockBean
    SmsService smsService;

    @Test
    void checkIn_withoutAuth_returns401() {
        String url = "http://localhost:" + port + "/api/v1/attend/check-in";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(
                Map.of("scheduleId", 1, "latitude", 35.3, "longitude", 129.0), headers);

        ResponseEntity<Object> resp = restTemplate.postForEntity(url, req, Object.class);

        assertEquals(401, resp.getStatusCodeValue());
    }

    /**
     * MemberSelfControllerIntegrationTest와 동일한 방식: 실제 OTP 요청/검증을 태워
     * ROLE_MEMBER accessToken을 얻는다. 새로 가입한 회원은 동의를 한 적이 없으므로
     * locationConsentAgreedAt이 null인 상태를 이 흐름만으로 재현할 수 있다.
     */
    private String obtainMemberAccessToken(String phoneNumber) {
        String memberAuthBase = "http://localhost:" + port + "/api/v1/member-auth";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.postForEntity(memberAuthBase + "/otp/request",
                new HttpEntity<>(Map.of("phoneNumber", phoneNumber), headers), Void.class);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(smsService).sendCustomMessage(eq(phoneNumber), messageCaptor.capture());
        String code = messageCaptor.getValue().replaceAll(".*인증번호는 (\\d+) .*", "$1");

        HttpEntity<Map<String, String>> verifyReq = new HttpEntity<>(
                Map.of("phoneNumber", phoneNumber, "code", code), headers);
        ResponseEntity<Map> verifyResp = restTemplate.postForEntity(
                memberAuthBase + "/otp/verify", verifyReq, Map.class);
        return (String) verifyResp.getBody().get("accessToken");
    }

    @Test
    void checkIn_withAuthButWithoutConsent_returns409() {
        String accessToken = obtainMemberAccessToken("01099998888");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(
                Map.of("scheduleId", 1, "latitude", 35.3, "longitude", 129.0), headers);

        ResponseEntity<Object> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/attend/check-in", req, Object.class);

        assertEquals(409, resp.getStatusCodeValue());
    }
}
