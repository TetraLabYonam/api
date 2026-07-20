package com.example.attempt.controller;

import com.example.attempt.domain.Place;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.repository.PlaceRepository;
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
class MemberSelfControllerIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockBean
    SmsService smsService;

    @Test
    void assignPlace_withoutAuth_returns401() {
        String url = "http://localhost:" + port + "/api/v1/members/me/assign-place";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(Map.of("placeId", 1), headers);

        ResponseEntity<Object> resp = restTemplate.postForEntity(url, req, Object.class);

        assertEquals(401, resp.getStatusCodeValue());
    }

    private HttpHeaders authHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        return headers;
    }

    @Test
    void me_withAuth_returnsCurrentMemberInfo() {
        String accessToken = MemberAuthTestSupport.loginAsMember(
                restTemplate, port, memberRepository, passwordEncoder, "김할매", "01011119999");

        ResponseEntity<Map> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/members/me",
                HttpMethod.GET, new HttpEntity<>(authHeaders(accessToken)), Map.class);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals("김할매", resp.getBody().get("username"));
        assertEquals(false, resp.getBody().get("locationConsentAgreed"));
        assertEquals("", resp.getBody().get("assignedPlaceId"));
    }

    @Test
    void consent_withAuth_recordsConsentTimestamp() {
        String accessToken = MemberAuthTestSupport.loginAsMember(
                restTemplate, port, memberRepository, passwordEncoder, "김할매", "01022229999");

        ResponseEntity<Map> consentResp = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/members/me/consent",
                HttpMethod.POST, new HttpEntity<>(authHeaders(accessToken)), Map.class);
        assertEquals(200, consentResp.getStatusCodeValue());

        ResponseEntity<Map> meResp = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/members/me",
                HttpMethod.GET, new HttpEntity<>(authHeaders(accessToken)), Map.class);
        assertEquals(true, meResp.getBody().get("locationConsentAgreed"));
    }

    @Test
    void assignPlace_withAuthAndExistingPlace_assignsPlaceToMember() {
        Place place = new Place("공원안전지킴이", "주소1", 35.3, 129.0);
        place = placeRepository.save(place);
        String accessToken = MemberAuthTestSupport.loginAsMember(
                restTemplate, port, memberRepository, passwordEncoder, "김할매", "01033339999");

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(Map.of("placeId", place.getId()), authHeaders(accessToken));
        ResponseEntity<Map> assignResp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/members/me/assign-place", req, Map.class);
        assertEquals(200, assignResp.getStatusCodeValue());

        ResponseEntity<Map> meResp = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/members/me",
                HttpMethod.GET, new HttpEntity<>(authHeaders(accessToken)), Map.class);
        assertEquals(place.getId().intValue(), meResp.getBody().get("assignedPlaceId"));
    }

    @Test
    void assignPlace_withAuthAndNonExistentPlace_returnsNotFound() {
        String accessToken = MemberAuthTestSupport.loginAsMember(
                restTemplate, port, memberRepository, passwordEncoder, "김할매", "01044449999");

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(Map.of("placeId", 999999), authHeaders(accessToken));
        ResponseEntity<Object> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/members/me/assign-place", req, Object.class);

        assertEquals(404, resp.getStatusCodeValue());
    }
}
