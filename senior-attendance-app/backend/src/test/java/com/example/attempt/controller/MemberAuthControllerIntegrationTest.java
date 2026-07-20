package com.example.attempt.controller;

import com.example.attempt.domain.Member;
import com.example.attempt.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberAuthControllerIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Member createMember(String username, Long employeeId, String phoneNumber, boolean active) {
        Member member = Member.withPhoneNumberHash(username, passwordEncoder.encode(phoneNumber));
        member.setEmployeeId(employeeId);
        member.setActive(active);
        return memberRepository.save(member);
    }

    private ResponseEntity<Map> login(Long employeeId, String phoneNumber) {
        String base = "http://localhost:" + port + "/api/v1/member-auth";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(
                Map.of("employeeId", employeeId, "phoneNumber", phoneNumber), headers);
        return restTemplate.postForEntity(base + "/login", req, Map.class);
    }

    @Test
    void login_withMatchingEmployeeIdAndPhoneNumber_returns200AndAccessToken() {
        createMember("김할매", 2001L, "01011112222", true);

        ResponseEntity<Map> resp = login(2001L, "01011112222");

        assertEquals(200, resp.getStatusCodeValue());
        assertNotNull(resp.getBody().get("accessToken"));
    }

    @Test
    void login_withWrongPhoneNumber_returns401() {
        createMember("김할매", 2002L, "01022223333", true);

        ResponseEntity<Map> resp = login(2002L, "01099998888");

        assertEquals(401, resp.getStatusCodeValue());
    }

    @Test
    void login_withUnknownEmployeeId_returns401() {
        ResponseEntity<Map> resp = login(9999L, "01033334444");

        assertEquals(401, resp.getStatusCodeValue());
    }

    @Test
    void login_withInactiveMember_returns401() {
        createMember("김할매", 2003L, "01044445555", false);

        ResponseEntity<Map> resp = login(2003L, "01044445555");

        assertEquals(401, resp.getStatusCodeValue());
    }

    private String decodeJwtPayload(String jwt) {
        String[] parts = jwt.split("\\.");
        return new String(Base64.getUrlDecoder().decode(parts[1]));
    }

    @Test
    void memberRefreshToken_worksAgainstMemberRefreshEndpoint_andReissuesRoleMemberToken() {
        // consumeRefreshToken revokes the token as soon as it is resolved (see
        // RefreshTokenServiceImpl.consumeRefreshToken), so this flow uses its own
        // freshly-issued cookie, independent from the escalation-rejection test below.
        createMember("김할매", 2004L, "01055559999", true);
        ResponseEntity<Map> loginResp = login(2004L, "01055559999");
        String setCookie = loginResp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
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
        // Regression test for a prior Critical review finding: a member's refresh token,
        // manually presented to the ADMIN /api/auth/refresh endpoint (bypassing cookie path
        // scoping, as an attacker would by sending the Cookie header directly), must NOT
        // yield a ROLE_ADMIN token. AuthController.refresh() verifies the resolved username
        // actually belongs to an Admin before minting a ROLE_ADMIN token.
        //
        // This uses its own freshly-issued cookie (separate from the member-refresh-success
        // test) because RefreshTokenServiceImpl.consumeRefreshToken revokes the token as its
        // very first action, before AuthController.refresh() ever gets to the admin check --
        // so even this rejected attempt permanently burns the token.
        createMember("김할매", 2005L, "01066667777", true);
        ResponseEntity<Map> loginResp = login(2005L, "01066667777");
        String setCookie = loginResp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);

        HttpHeaders cookieHeaders = new HttpHeaders();
        cookieHeaders.add(HttpHeaders.COOKIE, setCookie);
        ResponseEntity<Map> adminRefreshResp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/auth/refresh",
                new HttpEntity<>(cookieHeaders), Map.class);

        assertEquals(401, adminRefreshResp.getStatusCodeValue());
    }
}
