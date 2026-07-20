package com.example.attempt.controller;

import com.example.attempt.domain.Admin;
import com.example.attempt.domain.Attend;
import com.example.attempt.domain.AttendStatus;
import com.example.attempt.domain.Member;
import com.example.attempt.domain.Place;
import com.example.attempt.domain.Schedule;
import com.example.attempt.repository.AdminRepository;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScheduleControllerIntegrationTest {

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
    AdminRepository adminRepository;

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

    @Test
    void create_withoutAuth_returns401() {
        Place place = saveTestPlace();
        Map<String, Object> body = Map.of(
                "placeId", place.getId(), "title", "오전 근무",
                "startDate", "2026-07-13", "startTime", "09:00", "endTime", "13:00");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Object> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/admin/schedules",
                new HttpEntity<>(body, headers), Object.class);

        assertEquals(401, resp.getStatusCodeValue());
    }

    @Test
    void create_withMemberToken_returns403() {
        Place place = saveTestPlace();
        String accessToken = obtainMemberAccessToken("01099998888");
        Map<String, Object> body = Map.of(
                "placeId", place.getId(), "title", "오전 근무",
                "startDate", "2026-07-13", "startTime", "09:00", "endTime", "13:00");

        ResponseEntity<Object> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/admin/schedules",
                new HttpEntity<>(body, authHeaders(accessToken)), Object.class);

        assertEquals(403, resp.getStatusCodeValue());
    }

    @Test
    void create_withAdminToken_singleDay_createsScheduleAndAttendsForAssignedMembers() {
        Place place = saveTestPlace();
        Member member = memberRepository.save(new Member("김할매", "01070002222"));
        member.setAssignedPlaceId(place.getId());
        memberRepository.save(member);

        String accessToken = obtainAdminAccessToken();
        Map<String, Object> body = Map.of(
                "placeId", place.getId(), "title", "오전 근무",
                "startDate", "2026-07-13", "startTime", "09:00", "endTime", "13:00");

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/admin/schedules",
                new HttpEntity<>(body, authHeaders(accessToken)), Map.class);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(List.of("2026-07-13"), resp.getBody().get("createdDates"));
        assertEquals(List.of(), resp.getBody().get("skippedDates"));
        assertEquals(1, resp.getBody().get("attendCreatedCount"));

        List<Schedule> schedules = scheduleRepository.findAll();
        assertEquals(1, schedules.size());
        assertEquals(LocalDate.of(2026, 7, 13), schedules.get(0).getScheduleDate());
        assertEquals(LocalTime.of(9, 0), schedules.get(0).getStartTime());
        assertNotNull(schedules.get(0).getCreatedBy());
        // Schedule.createdBy is @ManyToOne(fetch = LAZY) and open-in-view is disabled, so the
        // repository's read-only transaction has already closed by the time we get here — calling
        // getUsername() on the lazy proxy would throw LazyInitializationException ("no session").
        // The proxy's id is available without touching the DB, so compare by id instead.
        Admin admin = adminRepository.findByUsername("admin@example.com").orElseThrow();
        assertEquals(admin.getId(), schedules.get(0).getCreatedBy().getId());
        assertEquals(1, attendRepository.findAll().size());
    }

    @Test
    void create_withAdminToken_recurringWithExistingDate_separatesCreatedAndSkipped() {
        Place place = saveTestPlace();
        Member member = memberRepository.save(new Member("김할매", "01070003333"));
        member.setAssignedPlaceId(place.getId());
        memberRepository.save(member);

        // 7/13(월)은 이미 일정이 있음 -> 스킵돼야 함
        scheduleRepository.save(Schedule.builder()
                .title("기존 일정")
                .scheduleDate(LocalDate.of(2026, 7, 13))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(13, 0))
                .place(place)
                .build());

        String accessToken = obtainAdminAccessToken();
        Map<String, Object> body = Map.of(
                "placeId", place.getId(), "title", "오전 근무",
                "startDate", "2026-07-06", "endDate", "2026-07-19",
                "daysOfWeek", List.of("MONDAY", "WEDNESDAY"),
                "startTime", "09:00", "endTime", "13:00");

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/admin/schedules",
                new HttpEntity<>(body, authHeaders(accessToken)), Map.class);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(List.of("2026-07-06", "2026-07-08", "2026-07-15"), resp.getBody().get("createdDates"));
        assertEquals(List.of("2026-07-13"), resp.getBody().get("skippedDates"));
        assertEquals(3, resp.getBody().get("attendCreatedCount"));
    }

    @Test
    void create_withAdminToken_invalidPlaceId_returns404() {
        String accessToken = obtainAdminAccessToken();
        Map<String, Object> body = Map.of(
                "placeId", 999999, "title", "오전 근무",
                "startDate", "2026-07-13", "startTime", "09:00", "endTime", "13:00");

        ResponseEntity<Object> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/admin/schedules",
                new HttpEntity<>(body, authHeaders(accessToken)), Object.class);

        assertEquals(404, resp.getStatusCodeValue());
    }

    @Test
    void create_withAdminToken_startDateAfterEndDate_returns400() {
        Place place = saveTestPlace();
        String accessToken = obtainAdminAccessToken();
        Map<String, Object> body = Map.of(
                "placeId", place.getId(), "title", "오전 근무",
                "startDate", "2026-07-15", "endDate", "2026-07-13",
                "daysOfWeek", List.of("MONDAY"),
                "startTime", "09:00", "endTime", "13:00");

        ResponseEntity<Object> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/admin/schedules",
                new HttpEntity<>(body, authHeaders(accessToken)), Object.class);

        assertEquals(400, resp.getStatusCodeValue());
    }

    @Test
    void get_withoutAuth_returns401() {
        Place place = saveTestPlace();
        ResponseEntity<Object> resp = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/admin/schedules?placeId=" + place.getId() + "&date=2026-07-13",
                Object.class);

        assertEquals(401, resp.getStatusCodeValue());
    }

    @Test
    void get_withMemberToken_returns403() {
        Place place = saveTestPlace();
        String accessToken = obtainMemberAccessToken("01099997777");

        ResponseEntity<Object> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/admin/schedules?placeId=" + place.getId() + "&date=2026-07-13",
                HttpMethod.GET, new HttpEntity<>(authHeaders(accessToken)), Object.class);

        assertEquals(403, resp.getStatusCodeValue());
    }

    @Test
    void get_withAdminToken_noScheduleForPlaceAndDate_returns404() {
        Place place = saveTestPlace();
        String accessToken = obtainAdminAccessToken();

        ResponseEntity<Object> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/admin/schedules?placeId=" + place.getId() + "&date=2026-07-13",
                HttpMethod.GET, new HttpEntity<>(authHeaders(accessToken)), Object.class);

        assertEquals(404, resp.getStatusCodeValue());
    }

    @Test
    void get_withAdminToken_scheduleWithAttendees_returns200WithAttendeeDetails() {
        Place place = saveTestPlace();
        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .title("오전 근무")
                .scheduleDate(LocalDate.of(2026, 7, 13))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(13, 0))
                .place(place)
                .build());

        Member member1 = memberRepository.save(new Member("김할매", "01070004444"));
        Member member2 = memberRepository.save(new Member("이할배", "01070005555"));

        Attend attend1 = attendRepository.save(Attend.builder()
                .member(member1)
                .schedule(schedule)
                .status(AttendStatus.PRESENT)
                .attendedAt(LocalDateTime.of(2026, 7, 13, 9, 2))
                .build());
        Attend attend2 = attendRepository.save(Attend.builder()
                .member(member2)
                .schedule(schedule)
                .status(AttendStatus.ABSENT)
                .note("병가")
                .build());

        // 다른 장소·날짜의 일정: 응답에 섞여 나오면 안 됨
        Place otherPlace = placeRepository.save(new Place("다른 공원", "다른 주소", 36.0, 128.0));
        Schedule otherSchedule = scheduleRepository.save(Schedule.builder()
                .title("다른 일정")
                .scheduleDate(LocalDate.of(2026, 7, 14))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(13, 0))
                .place(otherPlace)
                .build());
        Member otherMember = memberRepository.save(new Member("박할매", "01070006666"));
        attendRepository.save(Attend.builder()
                .member(otherMember)
                .schedule(otherSchedule)
                .status(AttendStatus.SCHEDULED)
                .build());

        String accessToken = obtainAdminAccessToken();

        ResponseEntity<Map> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/admin/schedules?placeId=" + place.getId() + "&date=2026-07-13",
                HttpMethod.GET, new HttpEntity<>(authHeaders(accessToken)), Map.class);

        assertEquals(200, resp.getStatusCodeValue());
        Map body = resp.getBody();
        assertEquals(schedule.getId().intValue(), ((Number) body.get("scheduleId")).intValue());
        assertEquals("오전 근무", body.get("title"));
        assertEquals("2026-07-13", body.get("scheduleDate"));
        assertEquals("09:00:00", body.get("startTime"));
        assertEquals("13:00:00", body.get("endTime"));
        assertEquals("중앙공원", body.get("placeName"));

        List<Map> attendees = (List<Map>) body.get("attendees");
        assertEquals(2, attendees.size());

        Map attendeeForMember1 = attendees.stream()
                .filter(a -> ((Number) a.get("memberId")).longValue() == member1.getId())
                .findFirst().orElseThrow();
        assertEquals(attend1.getId().intValue(), ((Number) attendeeForMember1.get("attendId")).intValue());
        assertEquals("김할매", attendeeForMember1.get("memberName"));
        assertEquals("PRESENT", attendeeForMember1.get("status"));
        assertNotNull(attendeeForMember1.get("attendedAt"));

        Map attendeeForMember2 = attendees.stream()
                .filter(a -> ((Number) a.get("memberId")).longValue() == member2.getId())
                .findFirst().orElseThrow();
        assertEquals(attend2.getId().intValue(), ((Number) attendeeForMember2.get("attendId")).intValue());
        assertEquals("이할배", attendeeForMember2.get("memberName"));
        assertEquals("ABSENT", attendeeForMember2.get("status"));
        assertEquals("병가", attendeeForMember2.get("note"));

        assertTrue(attendees.stream().noneMatch(a ->
                ((Number) a.get("memberId")).longValue() == otherMember.getId()));
    }

    @Test
    void get_withAdminToken_scheduleWithZeroAttendees_returns200WithEmptyAttendeeList() {
        Place place = saveTestPlace();
        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .title("오전 근무")
                .scheduleDate(LocalDate.of(2026, 7, 13))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(13, 0))
                .place(place)
                .build());

        String accessToken = obtainAdminAccessToken();

        ResponseEntity<Map> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/admin/schedules?placeId=" + place.getId() + "&date=2026-07-13",
                HttpMethod.GET, new HttpEntity<>(authHeaders(accessToken)), Map.class);

        assertEquals(200, resp.getStatusCodeValue());
        Map body = resp.getBody();
        assertEquals(schedule.getId().intValue(), ((Number) body.get("scheduleId")).intValue());

        List<Map> attendees = (List<Map>) body.get("attendees");
        assertNotNull(attendees);
        assertEquals(0, attendees.size());
    }
}
