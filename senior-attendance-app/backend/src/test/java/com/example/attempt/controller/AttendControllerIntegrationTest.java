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
import org.mockito.ArgumentCaptor;
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

    @Autowired
    AttendRepository attendRepository;

    @Autowired
    ScheduleRepository scheduleRepository;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    MemberRepository memberRepository;

    /**
     * today_withScheduledAttendToday_returnsScheduleInfo()가 만든 Attend/Schedule/Place fixture는
     * 다른 통합 테스트 클래스와 DB를 공유하므로, 남겨두면 PlaceControllerIntegrationTest의
     * placeRepository.deleteAll()이 FK 제약 위반으로 실패한다. 매 테스트 뒤 정리한다.
     */
    @AfterEach
    void cleanUpAttendFixtures() {
        attendRepository.deleteAll();
        scheduleRepository.deleteAll();
        placeRepository.deleteAll();
    }

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

    private HttpHeaders authHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        return headers;
    }

    @Test
    void today_withoutAuth_returns401() {
        ResponseEntity<Object> resp = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/attend/today", Object.class);

        assertEquals(401, resp.getStatusCodeValue());
    }

    @Test
    void today_withoutScheduleToday_returnsHasScheduleFalse() {
        String accessToken = obtainMemberAccessToken("01066667777");

        ResponseEntity<Map> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/attend/today",
                HttpMethod.GET, new HttpEntity<>(authHeaders(accessToken)), Map.class);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(false, resp.getBody().get("hasSchedule"));
    }

    @Test
    void today_withScheduledAttendToday_returnsScheduleInfo() {
        String phoneNumber = "01055556666";
        String accessToken = obtainMemberAccessToken(phoneNumber);
        Member member = memberRepository.findByPhoneNumber(phoneNumber).orElseThrow();

        Place place = placeRepository.save(new Place("중앙공원", "주소", 35.3, 129.0));
        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .title("오전 근무")
                .scheduleDate(LocalDate.now())
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(13, 0))
                .place(place)
                .build());
        attendRepository.save(Attend.builder()
                .member(member)
                .schedule(schedule)
                .status(AttendStatus.SCHEDULED)
                .build());

        ResponseEntity<Map> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/attend/today",
                HttpMethod.GET, new HttpEntity<>(authHeaders(accessToken)), Map.class);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(true, resp.getBody().get("hasSchedule"));
        assertEquals("중앙공원", resp.getBody().get("placeName"));
        assertEquals("09:00", resp.getBody().get("startTime"));
        assertEquals("13:00", resp.getBody().get("endTime"));
    }

    @Test
    void decline_withoutAuth_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(Map.of("scheduleId", 1), headers);

        ResponseEntity<Object> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/attend/decline", req, Object.class);

        assertEquals(401, resp.getStatusCodeValue());
    }

    @Test
    void decline_noAttendRecord_returns404() {
        String accessToken = obtainMemberAccessToken("01033332222");

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(
                Map.of("scheduleId", 999999), authHeaders(accessToken));

        ResponseEntity<Object> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/attend/decline", req, Object.class);

        assertEquals(404, resp.getStatusCodeValue());
    }

    @Test
    void decline_scheduled_returns200AndMarksAbsentInDb() {
        String phoneNumber = "01044445555";
        String accessToken = obtainMemberAccessToken(phoneNumber);
        Member member = memberRepository.findByPhoneNumber(phoneNumber).orElseThrow();

        Place place = placeRepository.save(new Place("중앙공원", "주소", 35.3, 129.0));
        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .title("오전 근무")
                .scheduleDate(LocalDate.now())
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(13, 0))
                .place(place)
                .build());
        Attend attend = attendRepository.save(Attend.builder()
                .member(member)
                .schedule(schedule)
                .status(AttendStatus.SCHEDULED)
                .build());

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(
                Map.of("scheduleId", schedule.getId()), authHeaders(accessToken));

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/attend/decline", req, Map.class);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(true, resp.getBody().get("success"));
        assertEquals("결석 처리되었습니다.", resp.getBody().get("message"));

        Attend updated = attendRepository.findById(attend.getId()).orElseThrow();
        assertEquals(AttendStatus.ABSENT, updated.getStatus());
    }

    @Test
    void decline_alreadyAttended_returns200WithSuccessFalse() {
        String phoneNumber = "01088889999";
        String accessToken = obtainMemberAccessToken(phoneNumber);
        Member member = memberRepository.findByPhoneNumber(phoneNumber).orElseThrow();

        Place place = placeRepository.save(new Place("중앙공원", "주소", 35.3, 129.0));
        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .title("오전 근무")
                .scheduleDate(LocalDate.now())
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(13, 0))
                .place(place)
                .build());
        attendRepository.save(Attend.builder()
                .member(member)
                .schedule(schedule)
                .status(AttendStatus.PRESENT)
                .build());

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(
                Map.of("scheduleId", schedule.getId()), authHeaders(accessToken));

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/attend/decline", req, Map.class);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(false, resp.getBody().get("success"));
    }
}
