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
import com.example.attempt.security.JwtTokenProvider;
import com.example.attempt.service.SmsService;
import com.example.attempt.support.MemberAuthTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

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
     * loginAsMember가 발급한 accessToken의 JWT subject(employeeId)를 복호화해 방금 로그인한
     * Member 엔티티를 되찾는다. phoneNumber 필드/findByPhoneNumber가 사라진 뒤에도 테스트가
     * 로그인 흐름으로 만든 회원에 직접 Attend를 붙일 수 있게 하기 위함이다.
     */
    private Member currentMemberFromToken(String accessToken) {
        Long employeeId = Long.parseLong(jwtTokenProvider.getClaims(accessToken).getSubject());
        return memberRepository.findByEmployeeId(employeeId).orElseThrow();
    }

    @Test
    void checkIn_withAuthButWithoutConsent_returns409() {
        String accessToken = MemberAuthTestSupport.loginAsMember(
                restTemplate, port, memberRepository, passwordEncoder, "김할매", "01099998888");

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
        String accessToken = MemberAuthTestSupport.loginAsMember(
                restTemplate, port, memberRepository, passwordEncoder, "김할매", "01066667777");

        ResponseEntity<Map> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/attend/today",
                HttpMethod.GET, new HttpEntity<>(authHeaders(accessToken)), Map.class);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(false, resp.getBody().get("hasSchedule"));
    }

    @Test
    void today_withScheduledAttendToday_returnsScheduleInfo() {
        String phoneNumber = "01055556666";
        String accessToken = MemberAuthTestSupport.loginAsMember(
                restTemplate, port, memberRepository, passwordEncoder, "김할매", phoneNumber);
        Member member = currentMemberFromToken(accessToken);

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
        String accessToken = MemberAuthTestSupport.loginAsMember(
                restTemplate, port, memberRepository, passwordEncoder, "김할매", "01033332222");

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(
                Map.of("scheduleId", 999999), authHeaders(accessToken));

        ResponseEntity<Object> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/attend/decline", req, Object.class);

        assertEquals(404, resp.getStatusCodeValue());
    }

    @Test
    void decline_scheduled_returns200AndMarksAbsentInDb() {
        String phoneNumber = "01044445555";
        String accessToken = MemberAuthTestSupport.loginAsMember(
                restTemplate, port, memberRepository, passwordEncoder, "김할매", phoneNumber);
        Member member = currentMemberFromToken(accessToken);

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
        String accessToken = MemberAuthTestSupport.loginAsMember(
                restTemplate, port, memberRepository, passwordEncoder, "김할매", phoneNumber);
        Member member = currentMemberFromToken(accessToken);

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

    @Test
    void history_withoutAuth_returns401() {
        ResponseEntity<Object> resp = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/attend/history", Object.class);

        assertEquals(401, resp.getStatusCodeValue());
    }

    @Test
    void history_returnsOwnRatesAndRecordsOnly_otherMemberRecordDoesNotLeak() {
        String phoneNumber = "01022223333";
        String accessToken = MemberAuthTestSupport.loginAsMember(
                restTemplate, port, memberRepository, passwordEncoder, "김할매", phoneNumber);
        Member member = currentMemberFromToken(accessToken);

        String otherPhoneNumber = "01077778888";
        String otherAccessToken = MemberAuthTestSupport.loginAsMember(
                restTemplate, port, memberRepository, passwordEncoder, "김할매", otherPhoneNumber);
        Member otherMember = currentMemberFromToken(otherAccessToken);

        Place place = placeRepository.save(new Place("중앙공원", "주소", 35.3, 129.0));
        LocalDate today = LocalDate.now();

        Schedule mySchedule = scheduleRepository.save(Schedule.builder()
                .title("오전 근무")
                .scheduleDate(today)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(13, 0))
                .place(place)
                .build());
        attendRepository.save(Attend.builder()
                .member(member)
                .schedule(mySchedule)
                .status(AttendStatus.PRESENT)
                .build());

        Schedule otherSchedule = scheduleRepository.save(Schedule.builder()
                .title("오후 근무")
                .scheduleDate(today)
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(18, 0))
                .place(place)
                .build());
        attendRepository.save(Attend.builder()
                .member(otherMember)
                .schedule(otherSchedule)
                .status(AttendStatus.ABSENT)
                .build());

        ResponseEntity<Map> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/attend/history",
                HttpMethod.GET, new HttpEntity<>(authHeaders(accessToken)), Map.class);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(100.0, resp.getBody().get("attendanceRate"));

        List<Map<String, Object>> records = (List<Map<String, Object>>) resp.getBody().get("records");
        assertEquals(1, records.size());
        assertEquals("중앙공원", records.get(0).get("placeName"));
        assertEquals("PRESENT", records.get(0).get("status"));
    }
}
