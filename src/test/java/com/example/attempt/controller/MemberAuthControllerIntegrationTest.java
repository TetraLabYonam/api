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

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberAuthControllerIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @MockBean
    SmsService smsService;

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

    /**
     * OTP 인증을 완료하고, SMS 발송 텍스트에서 실제 인증번호를 추출해 검증에 사용한다
     * (MemberOtpServiceTest와 동일한 방식). 반환된 accessToken이 ROLE_MEMBER 클레임을
     * 갖는지, 그리고 Set-Cookie로 내려온 refreshToken 쿠키 값을 확인한다.
     */
    private ResponseEntity<Map> verifyOtpAndReturnResponse(String phoneNumber) {
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
        return restTemplate.postForEntity(memberAuthBase + "/otp/verify", verifyReq, Map.class);
    }

    private String decodeJwtPayload(String jwt) {
        String[] parts = jwt.split("\\.");
        return new String(Base64.getUrlDecoder().decode(parts[1]));
    }

    @Test
    void verifyOtp_withCorrectCode_issuesMemberTokenWithRoleMemberOnly() {
        ResponseEntity<Map> verifyResp = verifyOtpAndReturnResponse("01077778888");

        assertEquals(200, verifyResp.getStatusCodeValue());
        String accessToken = (String) verifyResp.getBody().get("accessToken");
        assertNotNull(accessToken);

        // decode the JWT payload (no signature verification needed here — just confirming
        // the claims this test controls, using the unsecured parser)
        String payloadJson = decodeJwtPayload(accessToken);
        assertTrue(payloadJson.contains("ROLE_MEMBER"));
        assertFalse(payloadJson.contains("ROLE_ADMIN"));

        String setCookie = verifyResp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("refreshToken="));
        assertTrue(setCookie.contains("/api/v1/member-auth/refresh"));
    }

    @Test
    void memberRefreshToken_worksAgainstMemberRefreshEndpoint_andReissuesRoleMemberToken() {
        // consumeRefreshToken revokes the token as soon as it is resolved (see
        // RefreshTokenServiceImpl.consumeRefreshToken), so this flow uses its own
        // freshly-issued cookie, independent from the escalation-rejection test below.
        ResponseEntity<Map> verifyResp = verifyOtpAndReturnResponse("01055559999");
        String setCookie = verifyResp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);

        HttpHeaders cookieHeaders = new HttpHeaders();
        cookieHeaders.add(HttpHeaders.COOKIE, setCookie);
        ResponseEntity<Map> memberRefreshResp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/member-auth/refresh",
                new HttpEntity<>(cookieHeaders), Map.class);

        assertEquals(200, memberRefreshResp.getStatusCodeValue());
        String refreshedToken = (String) memberRefreshResp.getBody().get("accessToken");
        assertNotNull(refreshedToken);
        String refreshedPayload = decodeJwtPayload(refreshedToken);
        assertTrue(refreshedPayload.contains("ROLE_MEMBER"));
        assertFalse(refreshedPayload.contains("ROLE_ADMIN"));
    }

    @Test
    void memberRefreshToken_presentedToAdminRefreshEndpoint_isRejected() {
        // Regression test for the Task 7 review finding (Critical): a member's refresh token,
        // manually presented to the ADMIN /api/auth/refresh endpoint (bypassing cookie path
        // scoping, as an attacker would by sending the Cookie header directly), must NOT
        // yield a ROLE_ADMIN token. AuthController.refresh() now verifies the resolved
        // username actually belongs to an Admin before minting a ROLE_ADMIN token.
        //
        // This uses its own freshly-issued cookie (separate from the member-refresh-success
        // test) because RefreshTokenServiceImpl.consumeRefreshToken revokes the token as its
        // very first action, before AuthController.refresh() ever gets to the new admin
        // check — so even this rejected attempt permanently burns the token.
        ResponseEntity<Map> verifyResp = verifyOtpAndReturnResponse("01066667777");
        String setCookie = verifyResp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);

        HttpHeaders cookieHeaders = new HttpHeaders();
        cookieHeaders.add(HttpHeaders.COOKIE, setCookie);
        ResponseEntity<Map> adminRefreshResp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/auth/refresh",
                new HttpEntity<>(cookieHeaders), Map.class);

        assertEquals(401, adminRefreshResp.getStatusCodeValue());
    }
}
