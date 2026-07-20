package com.example.attempt.controller;

import com.example.attempt.domain.Attend;
import com.example.attempt.domain.AttendStatus;
import com.example.attempt.domain.Member;
import com.example.attempt.domain.Place;
import com.example.attempt.domain.Schedule;
import com.example.attempt.repository.AttendRepository;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.repository.PlaceRepository;
import com.example.attempt.repository.ScheduleRepository;
import com.example.attempt.service.SmsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminAttendControllerIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @MockBean
    SmsService smsService;

    @Autowired
    AttendRepository attendRepository;

    @Autowired
    ScheduleRepository scheduleRepository;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    MemberRepository memberRepository;

    @AfterEach
    void cleanUpFixtures() {
        attendRepository.deleteAll();
        scheduleRepository.deleteAll();
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

    private String obtainMemberAccessToken(String phoneNumber) {
        String memberAuthBase = "http://localhost:" + port + "/api/v1/member-auth";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForEntity(memberAuthBase + "/otp/request",
                new HttpEntity<>(Map.of("phoneNumber", phoneNumber), headers), Void.class);

        org.mockito.ArgumentCaptor<String> messageCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(smsService).sendCustomMessage(org.mockito.ArgumentMatchers.eq(phoneNumber), messageCaptor.capture());
        String code = messageCaptor.getValue().replaceAll(".*인증번호는 (\\d+) .*", "$1");

        HttpEntity<Map<String, String>> verifyReq = new HttpEntity<>(
                Map.of("phoneNumber", phoneNumber, "code", code), headers);
        ResponseEntity<Map> verifyResp = restTemplate.postForEntity(
                memberAuthBase + "/otp/verify", verifyReq, Map.class);
        return (String) verifyResp.getBody().get("accessToken");
    }

    private HttpHeaders authHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        return headers;
    }

    private Place saveTestPlace() {
        return placeRepository.save(new Place("중앙공원", "주소", 35.3, 129.0));
    }

    private Attend saveTestAttend() {
        Place place = saveTestPlace();
        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .title("오전 근무")
                .scheduleDate(LocalDate.of(2026, 7, 13))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(13, 0))
                .place(place)
                .build());
        Member member = memberRepository.save(new Member("김할매", "01070002222"));
        return attendRepository.save(Attend.builder()
                .member(member)
                .schedule(schedule)
                .status(AttendStatus.SCHEDULED)
                .build());
    }

    @Test
    void patch_withoutAuth_returns401() {
        Attend attend = saveTestAttend();
        Map<String, Object> body = Map.of("status", "ABSENT", "note", "병가");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Object> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/admin/attend/" + attend.getId(),
                HttpMethod.PATCH, new HttpEntity<>(body, headers), Object.class);

        assertEquals(401, resp.getStatusCodeValue());
    }

    @Test
    void patch_withMemberToken_returns403() {
        Attend attend = saveTestAttend();
        String accessToken = obtainMemberAccessToken("01099998888");
        Map<String, Object> body = Map.of("status", "ABSENT", "note", "병가");

        ResponseEntity<Object> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/admin/attend/" + attend.getId(),
                HttpMethod.PATCH, new HttpEntity<>(body, authHeaders(accessToken)), Object.class);

        assertEquals(403, resp.getStatusCodeValue());
    }

    @Test
    void patch_withAdminToken_nonExistentAttendId_returns404() {
        String accessToken = obtainAdminAccessToken();
        Map<String, Object> body = Map.of("status", "ABSENT", "note", "병가");

        ResponseEntity<Object> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/admin/attend/999999",
                HttpMethod.PATCH, new HttpEntity<>(body, authHeaders(accessToken)), Object.class);

        assertEquals(404, resp.getStatusCodeValue());
    }

    @Test
    void patch_withAdminToken_validAttendId_updatesStatusAndReturns200WithAttendeeItem() {
        Attend attend = saveTestAttend();
        String accessToken = obtainAdminAccessToken();
        Map<String, Object> body = Map.of("status", "EXCUSED", "note", "병원 진료");

        ResponseEntity<Map> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/admin/attend/" + attend.getId(),
                HttpMethod.PATCH, new HttpEntity<>(body, authHeaders(accessToken)), Map.class);

        assertEquals(200, resp.getStatusCodeValue());
        Map responseBody = resp.getBody();
        assertEquals(attend.getId().intValue(), ((Number) responseBody.get("attendId")).intValue());
        assertEquals("김할매", responseBody.get("memberName"));
        assertEquals("EXCUSED", responseBody.get("status"));
        assertEquals("병원 진료", responseBody.get("note"));

        Attend updated = attendRepository.findById(attend.getId()).orElseThrow();
        assertEquals(AttendStatus.EXCUSED, updated.getStatus());
        assertEquals("병원 진료", updated.getNote());
    }

    @Test
    void patch_withBlankNote_clearsStaleNoteInDb() {
        Attend attend = saveTestAttend();
        attend.setStatus(AttendStatus.ABSENT);
        attend.setNote("병가");
        attendRepository.save(attend);

        String accessToken = obtainAdminAccessToken();
        Map<String, Object> body = Map.of("status", "PRESENT", "note", "");

        ResponseEntity<Map> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/admin/attend/" + attend.getId(),
                HttpMethod.PATCH, new HttpEntity<>(body, authHeaders(accessToken)), Map.class);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals("", resp.getBody().get("note"));

        Attend updated = attendRepository.findById(attend.getId()).orElseThrow();
        assertEquals(AttendStatus.PRESENT, updated.getStatus());
        assertEquals("", updated.getNote());
    }

    @Test
    void patch_withMissingStatus_returns400() {
        Attend attend = saveTestAttend();
        String accessToken = obtainAdminAccessToken();
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("note", "병가");

        ResponseEntity<Object> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/admin/attend/" + attend.getId(),
                HttpMethod.PATCH, new HttpEntity<>(body, authHeaders(accessToken)), Object.class);

        assertEquals(400, resp.getStatusCodeValue());
    }
}
