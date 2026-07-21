package com.example.attempt.controller;

import com.example.attempt.domain.Member;
import com.example.attempt.domain.Place;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.repository.PlaceRepository;
import com.example.attempt.service.SmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminMemberControllerIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockBean
    SmsService smsService;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        placeRepository.deleteAll();
    }

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
    void register_withValidRequest_createsMemberAndReturnsQrPayload() {
        Place place = new Place();
        place.setName("공원안전지킴이");
        place.setAddress("주소1");
        place = placeRepository.save(place);

        String accessToken = obtainAdminAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "name", "김할매",
                "phoneNumber", "01000000000",
                "placeId", place.getId());
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/admin/members", req, Map.class);

        assertEquals(200, resp.getStatusCodeValue());
        Map<String, Object> respBody = resp.getBody();
        Number employeeIdNum = (Number) respBody.get("employeeId");
        assertNotNull(employeeIdNum);
        long employeeId = employeeIdNum.longValue();
        assertTrue(employeeId >= 1001, "employeeId should be auto-assigned starting from 1001");

        assertEquals(employeeId + ":01000000000", respBody.get("qrPayload"));

        Member saved = memberRepository.findByEmployeeId(employeeId).orElse(null);
        assertNotNull(saved, "Member should be persisted with the assigned employeeId");
        assertNotEquals("01000000000", saved.getPhoneNumberHash(),
                "Raw phone number must never be stored as-is");
        assertTrue(passwordEncoder.matches("01000000000", saved.getPhoneNumberHash()),
                "Stored hash should still verify against the raw phone number");
    }

    @Test
    void register_withNonExistentPlace_returns400() {
        String accessToken = obtainAdminAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "name", "김할매",
                "phoneNumber", "01000000000",
                "placeId", 999999L);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/admin/members", req, Map.class);

        assertEquals(400, resp.getStatusCodeValue());
    }

    @Test
    void list_returnsAllMembersWithPlaceName() {
        Place place = new Place();
        place.setName("동네마당재활용");
        place.setAddress("주소2");
        place = placeRepository.save(place);

        Member member = Member.withPhoneNumberHash("박할배", passwordEncoder.encode("01011112222"));
        member.setEmployeeId(memberRepository.findMaxEmployeeIdOrDefault() + 1);
        member.setAssignedPlaceId(place.getId());
        memberRepository.save(member);

        String accessToken = obtainAdminAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<List> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/admin/members",
                HttpMethod.GET, new HttpEntity<>(headers), List.class);

        assertEquals(200, resp.getStatusCodeValue());
        List<Map<String, Object>> body = resp.getBody();
        assertEquals(1, body.size());
        Map<String, Object> item = body.get(0);
        assertEquals("박할배", item.get("name"));
        assertEquals("동네마당재활용", item.get("placeName"));
        assertEquals(true, item.get("active"));
    }

    @Test
    void updateActive_setsFalse_thenLoginFails() {
        Member member = Member.withPhoneNumberHash("최할매", passwordEncoder.encode("01033334444"));
        member.setEmployeeId(memberRepository.findMaxEmployeeIdOrDefault() + 1);
        member = memberRepository.save(member);
        Long employeeId = member.getEmployeeId();

        String accessToken = obtainAdminAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(Map.of("active", false), headers);
        ResponseEntity<Void> patchResp = restTemplate.exchange(
                "http://localhost:" + port + "/api/admin/members/" + employeeId,
                HttpMethod.PATCH, req, Void.class);
        assertEquals(200, patchResp.getStatusCodeValue());

        assertFalse(memberRepository.findByEmployeeId(employeeId).get().isActive());

        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> loginReq = new HttpEntity<>(
                Map.of("employeeId", employeeId, "phoneNumber", "01033334444"), loginHeaders);
        ResponseEntity<Map> loginResp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/member-auth/login", loginReq, Map.class);

        assertEquals(401, loginResp.getStatusCodeValue());
    }
}
