# 실제 호출 경로 기준 테스트 커버리지 확대 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 실제로 호출되는 백엔드 경로(`AttendService.checkIn()`, `PlaceController` 인증 검색, `LlmJobSearchClient`)와 Flutter 화면 6개의 테스트 커버리지를 정답지(`docs/superpowers/specs/2026-07-13-backend-mobile-test-coverage-design.md`)에 정의된 시나리오대로 채운다.

**Architecture:** 백엔드는 기존 Mockito 단위 테스트 관례(`PlaceSearchServiceTest` 등)를 따르고, 지각 판정에 유예시간(`attendance.late.grace-minutes`, 기본 10분) 설정을 신규 추가한다. `LlmJobSearchClient`는 `PlaceSearchService`와 동일한 "2개 생성자(운영용/테스트용)" 패턴으로 `RestTemplate`을 테스트 가능하게 바꾼다. 모바일은 `job_repository_test.dart`의 가짜 `HttpClientAdapter` 관례를 화면(위젯) 테스트로 확장하고, `apiClientProvider`를 오버라이드하는 공용 테스트 유틸을 신규로 둔다.

**Tech Stack:** Spring Boot 3.5.6 / JUnit 5 / Mockito / Flutter / Riverpod / flutter_test / geolocator_platform_interface(테스트 전용 dev_dependency 추가)

## Global Constraints

- 지각 판정 유예시간 기본값은 10분(`attendance.late.grace-minutes`), `attendance.location.radius`와 동일하게 환경변수로 오버라이드 가능해야 한다
- `ScheduleService` 관련 코드는 이미 삭제 완료된 상태다(더 이상 존재하지 않음) — 이 플랜에서 다시 참조하지 않는다
- `AttendService.markAbsent/markExcused/updateAttendStatus`는 이번 플랜 범위 밖이다 — 수정하지 않는다
- 백엔드 신규/수정 테스트는 기존 관례를 따른다: 순수 단위 테스트는 Mockito로 `new`, 컨트롤러 테스트는 `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`
- 모바일 위젯 테스트는 실제 네트워크를 타지 않는다 — `apiClientProvider`를 오버라이드해 가짜 `HttpClientAdapter`로 응답을 준다
- 커밋은 태스크 단위로 한다 (기존 저장소 관례)

---

### Task 1: AttendService — 지각 유예시간 설정 + AttendServiceTest

**Files:**
- Modify: `backend/src/main/java/com/example/attempt/service/AttendService.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/example/attempt/service/AttendServiceTest.java` (신규)

**Interfaces:**
- Consumes: `AttendRepository.findByScheduleIdAndMemberId`, `SmsService.sendAttendanceNotification` (기존)
- Produces: `AttendService(AttendRepository, SmsService, int locationRadius, int lateGraceMinutes)` 생성자 (Spring이 `@Value`로 자동 주입, 다른 태스크에서 직접 호출하지 않음)

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/example/attempt/service/AttendServiceTest.java`:

```java
package com.example.attempt.service;

import com.example.attempt.domain.Attend;
import com.example.attempt.domain.AttendStatus;
import com.example.attempt.domain.Place;
import com.example.attempt.domain.Schedule;
import com.example.attempt.dto.attend.AttendCheckInRequest;
import com.example.attempt.dto.attend.AttendCheckInResponse;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.AttendRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AttendServiceTest {

    private static final int RADIUS_METERS = 500;
    private static final int GRACE_MINUTES = 10;

    private AttendRepository attendRepository;
    private SmsService smsService;
    private AttendService service;

    @BeforeEach
    void setup() {
        attendRepository = mock(AttendRepository.class);
        smsService = mock(SmsService.class);
        service = new AttendService(attendRepository, smsService, RADIUS_METERS, GRACE_MINUTES);
    }

    private Place placeAt(double lat, double lon) {
        return new Place("공원안전지킴이", "주소", lat, lon);
    }

    private Schedule scheduleStartingAt(LocalTime startTime, Place place) {
        return Schedule.builder()
                .id(1L)
                .title("오전 근무")
                .startTime(startTime)
                .place(place)
                .build();
    }

    private Attend scheduledAttend(Schedule schedule) {
        return Attend.builder()
                .id(10L)
                .schedule(schedule)
                .status(AttendStatus.SCHEDULED)
                .build();
    }

    private AttendCheckInRequest requestAt(double lat, double lon) {
        return AttendCheckInRequest.builder()
                .scheduleId(1L)
                .memberId(100L)
                .latitude(lat)
                .longitude(lon)
                .build();
    }

    @Test
    void checkIn_onTimeWithinRadius_marksPresent() {
        Schedule schedule = scheduleStartingAt(LocalTime.now().plusHours(1), placeAt(35.30, 129.00));
        Attend attend = scheduledAttend(schedule);
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.of(attend));

        AttendCheckInResponse response = service.checkIn(requestAt(35.3001, 129.0001));

        assertEquals(AttendStatus.PRESENT, response.getStatus());
        assertFalse(response.isLate());
        assertTrue(response.isSuccess());
        assertNotNull(response.getAttendedAt());
        verify(attendRepository).save(attend);
    }

    @Test
    void checkIn_withinGracePeriodAfterStart_stillMarksPresent() {
        Schedule schedule = scheduleStartingAt(LocalTime.now().minusMinutes(5), placeAt(35.30, 129.00));
        Attend attend = scheduledAttend(schedule);
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.of(attend));

        AttendCheckInResponse response = service.checkIn(requestAt(35.3001, 129.0001));

        assertEquals(AttendStatus.PRESENT, response.getStatus());
        assertFalse(response.isLate());
    }

    @Test
    void checkIn_pastGracePeriodAfterStart_marksLate() {
        Schedule schedule = scheduleStartingAt(LocalTime.now().minusMinutes(15), placeAt(35.30, 129.00));
        Attend attend = scheduledAttend(schedule);
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.of(attend));

        AttendCheckInResponse response = service.checkIn(requestAt(35.3001, 129.0001));

        assertEquals(AttendStatus.LATE, response.getStatus());
        assertTrue(response.isLate());
    }

    @Test
    void checkIn_outsideRadius_throwsIllegalStateAndDoesNotSave() {
        Schedule schedule = scheduleStartingAt(LocalTime.now().plusHours(1), placeAt(35.30, 129.00));
        Attend attend = scheduledAttend(schedule);
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.of(attend));

        assertThrows(IllegalStateException.class, () -> service.checkIn(requestAt(35.31, 129.00)));
        verify(attendRepository, never()).save(any());
    }

    @Test
    void checkIn_alreadyAttended_returnsUnsuccessfulWithoutChangingState() {
        Attend attend = Attend.builder()
                .id(10L)
                .status(AttendStatus.PRESENT)
                .attendedAt(LocalDateTime.now().minusHours(1))
                .build();
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.of(attend));

        AttendCheckInResponse response = service.checkIn(requestAt(35.3001, 129.0001));

        assertFalse(response.isSuccess());
        assertEquals("이미 출석 처리되었습니다.", response.getMessage());
        assertEquals(AttendStatus.PRESENT, response.getStatus());
        verify(attendRepository, never()).save(any());
    }

    @Test
    void checkIn_noAttendRecord_throwsResourceNotFound() {
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.checkIn(requestAt(35.3, 129.0)));
    }

    @Test
    void checkIn_smsSendFails_stillReturnsSuccess() {
        Schedule schedule = scheduleStartingAt(LocalTime.now().plusHours(1), placeAt(35.30, 129.00));
        Attend attend = scheduledAttend(schedule);
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.of(attend));
        doThrow(new RuntimeException("SMS 전송 실패")).when(smsService).sendAttendanceNotification(any());

        AttendCheckInResponse response = service.checkIn(requestAt(35.3001, 129.0001));

        assertTrue(response.isSuccess());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.service.AttendServiceTest"`
Expected: FAIL — 컴파일 에러. `AttendService(AttendRepository, SmsService, int, int)` 4-arg 생성자가 아직 없음 (현재는 `@RequiredArgsConstructor`로 2-arg만 존재)

- [ ] **Step 3: Modify AttendService — 생성자와 지각 판정 로직 변경**

`backend/src/main/java/com/example/attempt/service/AttendService.java`의 import에서 `lombok.RequiredArgsConstructor` 제거:

```java
import com.example.attempt.domain.Attend;
import com.example.attempt.domain.AttendStatus;
import com.example.attempt.domain.Place;
import com.example.attempt.domain.Schedule;
import com.example.attempt.dto.attend.AttendCheckInRequest;
import com.example.attempt.dto.attend.AttendCheckInResponse;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.AttendRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
```

클래스 선언과 필드/생성자 부분(기존 `@RequiredArgsConstructor` 어노테이션과 필드 2개 + `@Value` 필드)을 아래로 교체:

```java
/**
 * 출석 관리 서비스
 */
@Service
@Transactional
@Slf4j
public class AttendService {

    private final AttendRepository attendRepository;
    private final SmsService smsService;
    private final int locationRadius;
    private final int lateGraceMinutes;

    public AttendService(AttendRepository attendRepository,
                          SmsService smsService,
                          @Value("${attendance.location.radius:100}") int locationRadius,
                          @Value("${attendance.late.grace-minutes:10}") int lateGraceMinutes) {
        this.attendRepository = attendRepository;
        this.smsService = smsService;
        this.locationRadius = locationRadius;
        this.lateGraceMinutes = lateGraceMinutes;
    }
```

`isLate(Schedule schedule)` 메서드를 아래로 교체:

```java
    /**
     * 지각 여부 판단 — 시작 시간 + 유예시간(lateGraceMinutes)까지는 정시로 인정한다
     */
    private boolean isLate(Schedule schedule) {
        if (schedule.getStartTime() == null) {
            return false; // 시작 시간이 없으면 지각 아님
        }

        LocalTime now = LocalTime.now();
        LocalTime lateThreshold = schedule.getStartTime().plusMinutes(lateGraceMinutes);

        return now.isAfter(lateThreshold);
    }
```

- [ ] **Step 4: Add attendance.late.grace-minutes to application.yml**

`backend/src/main/resources/application.yml`의 기존 `attendance:` 블록을 아래로 교체:

```yaml
# 출석 관련 설정
attendance:
  location:
    radius: 500  # 출석 가능 반경 (미터)
  late:
    grace-minutes: 10  # 지각 판정 유예 시간 (분) — 시작시간 + 이 값까지는 정시로 인정
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.service.AttendServiceTest"`
Expected: PASS (7개 테스트 모두)

- [ ] **Step 6: Run full backend suite to confirm no regression**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL (기존 `AttendControllerIntegrationTest`의 409 테스트도 여전히 통과해야 함 — 그 테스트는 동의 미완료 409이지 위치 409가 아니므로 영향 없음)

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/example/attempt/service/AttendService.java \
        backend/src/main/resources/application.yml \
        backend/src/test/java/com/example/attempt/service/AttendServiceTest.java
git commit -m "feat(attend): add late grace-period config, cover checkIn with unit tests"
```

---

### Task 2: PlaceController — 인증된 성공 경로 테스트 추가

**Files:**
- Modify: `backend/src/test/java/com/example/attempt/controller/PlaceControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `PlaceController` (기존, 수정 없음 — 이미 올바르게 동작함)

이 태스크는 이미 동작하는 엔드포인트에 대한 회귀 방지 테스트를 추가하는 것이라, "실패 확인" 단계가 없다. 테스트를 작성하면 바로 통과해야 한다.

- [ ] **Step 1: Write the test**

`backend/src/test/java/com/example/attempt/controller/PlaceControllerIntegrationTest.java` 전체를 아래로 교체:

```java
package com.example.attempt.controller;

import com.example.attempt.domain.Place;
import com.example.attempt.domain.UnitType;
import com.example.attempt.repository.PlaceRepository;
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
class PlaceControllerIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    PlaceRepository placeRepository;

    @MockBean
    SmsService smsService;

    // 이 테스트는 인증 없이 401이 반환되는 것만 검증한다. OTP 코드는 SMS로만 발송되고
    // 해시로 저장되므로 통합 테스트에서 원본 코드를 알아낼 방법이 없다 — 로그인 성공
    // 이후의 인가 동작(200 응답)은 아래 인증된 테스트들이 커버한다.
    @Test
    void listByUnitType_withoutAuth_returns401() {
        Place park = new Place("공원안전지킴이", "주소1", 35.3, 129.0);
        park.setUnitType(UnitType.PUBLIC_INTEREST);
        placeRepository.save(park);

        String url = "http://localhost:" + port + "/api/v1/places?unitType=PUBLIC_INTEREST";
        ResponseEntity<Object> resp = restTemplate.getForEntity(url, Object.class);

        assertEquals(401, resp.getStatusCodeValue());
    }

    /**
     * MemberAuthControllerIntegrationTest와 동일한 방식: SMS 발송 텍스트를 캡처해
     * 실제 OTP 코드를 추출하고, 검증까지 완료해 진짜 ROLE_MEMBER accessToken을 얻는다.
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
    void listByUnitType_withAuth_returnsOnlyMatchingUnitType() {
        Place park = new Place("공원안전지킴이", "주소1", 35.3, 129.0);
        park.setUnitType(UnitType.PUBLIC_INTEREST);
        placeRepository.save(park);

        Place market = new Place("동네마당재활용", "주소2", 35.4, 129.1);
        market.setUnitType(UnitType.MARKET);
        placeRepository.save(market);

        String accessToken = obtainMemberAccessToken("01012340001");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        String url = "http://localhost:" + port + "/api/v1/places?unitType=PUBLIC_INTEREST";
        ResponseEntity<Object[]> resp = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Object[].class);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(1, resp.getBody().length);
    }

    @Test
    void searchByKeyword_withAuth_returnsOnlyMatchingResults() {
        Place park = new Place("공원안전지킴이", "주소1", 35.3, 129.0);
        park.setUnitType(UnitType.PUBLIC_INTEREST);
        park.setDescription("청소 및 화단 관리");
        placeRepository.save(park);

        Place other = new Place("스쿨존실버봉사단", "주소2", 35.5, 129.2);
        other.setUnitType(UnitType.PUBLIC_INTEREST);
        other.setDescription("등하교 안전 지도");
        placeRepository.save(other);

        String accessToken = obtainMemberAccessToken("01012340002");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        String url = "http://localhost:" + port + "/api/v1/places?unitType=PUBLIC_INTEREST&q=청소";
        ResponseEntity<Object[]> resp = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Object[].class);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(1, resp.getBody().length);
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.controller.PlaceControllerIntegrationTest"`
Expected: PASS (3개 테스트 모두 — 신규 구현 없이 기존 동작을 회귀 테스트로 고정)

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/example/attempt/controller/PlaceControllerIntegrationTest.java
git commit -m "test(place): cover authenticated list/search success paths"
```

---

### Task 3: LlmJobSearchClient — RestTemplate 생성자 주입 + LlmJobSearchClientTest

**Files:**
- Modify: `backend/src/main/java/com/example/attempt/service/LlmJobSearchClient.java`
- Test: `backend/src/test/java/com/example/attempt/service/LlmJobSearchClientTest.java` (신규)

**Interfaces:**
- Consumes: 없음
- Produces: `LlmJobSearchClient(String apiKey, String model, RestTemplate restTemplate)` package-private 생성자 (테스트 전용, `PlaceSearchService`가 사용하는 `pickBestMatch(String, List<PlaceSummaryDto>): Optional<Long>`는 변경 없음)

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/example/attempt/service/LlmJobSearchClientTest.java`:

```java
package com.example.attempt.service;

import com.example.attempt.domain.UnitType;
import com.example.attempt.dto.place.PlaceSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LlmJobSearchClientTest {

    private RestTemplate restTemplate;
    private LlmJobSearchClient client;

    @BeforeEach
    void setup() {
        restTemplate = mock(RestTemplate.class);
        client = new LlmJobSearchClient("test-api-key", "claude-haiku-4-5-20251001", restTemplate);
    }

    private PlaceSummaryDto candidate(long id, String name) {
        return PlaceSummaryDto.builder().id(id).name(name).unitType(UnitType.PUBLIC_INTEREST).build();
    }

    private void mockRestTemplateResponse(String responseBody) {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
    }

    @Test
    void pickBestMatch_withEmptyCandidates_returnsEmptyWithoutCallingRestTemplate() {
        Optional<Long> result = client.pickBestMatch("아무 말이나", List.of());

        assertTrue(result.isEmpty());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void pickBestMatch_parsesPlaceIdFromValidResponse() {
        mockRestTemplateResponse("{\"content\":[{\"text\":\"7\"}]}");

        Optional<Long> result = client.pickBestMatch("학교 앞에서 깃발", List.of(candidate(7L, "스쿨존실버봉사단1")));

        assertEquals(Optional.of(7L), result);
    }

    @Test
    void pickBestMatch_whenLlmRespondsZero_returnsEmpty() {
        mockRestTemplateResponse("{\"content\":[{\"text\":\"0\"}]}");

        Optional<Long> result = client.pickBestMatch("알 수 없는 일", List.of(candidate(7L, "스쿨존실버봉사단1")));

        assertTrue(result.isEmpty());
    }

    @Test
    void pickBestMatch_whenHttpCallThrows_returnsEmpty() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("연결 실패"));

        Optional<Long> result = client.pickBestMatch("학교 앞에서 깃발", List.of(candidate(7L, "스쿨존실버봉사단1")));

        assertTrue(result.isEmpty());
    }

    @Test
    void pickBestMatch_whenResponseIsMalformed_returnsEmpty() {
        mockRestTemplateResponse("이건 JSON이 아닙니다");

        Optional<Long> result = client.pickBestMatch("학교 앞에서 깃발", List.of(candidate(7L, "스쿨존실버봉사단1")));

        assertTrue(result.isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.service.LlmJobSearchClientTest"`
Expected: FAIL — 컴파일 에러. `LlmJobSearchClient(String, String, RestTemplate)` 생성자가 아직 없음

- [ ] **Step 3: Refactor LlmJobSearchClient to inject RestTemplate**

`backend/src/main/java/com/example/attempt/service/LlmJobSearchClient.java`에서 필드/생성자 부분을 아래로 교체 (import에 `org.springframework.beans.factory.annotation.Autowired` 추가 필요):

```java
import com.example.attempt.dto.place.PlaceSummaryDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
```

```java
@Service
@ConditionalOnExpression("'${llm.provider.api-key:}' != ''")
@Slf4j
public class LlmJobSearchClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String model;

    @Autowired
    public LlmJobSearchClient(@Value("${llm.provider.api-key}") String apiKey,
                               @Value("${llm.provider.model:claude-haiku-4-5-20251001}") String model) {
        this(apiKey, model, new RestTemplate());
    }

    // 테스트에서 RestTemplate을 mock으로 교체하기 위한 생성자.
    // PlaceSearchService와 동일한 이유로 생성자가 2개라, Spring이 실제로 쓸 생성자를
    // @Autowired로 명시한다.
    LlmJobSearchClient(String apiKey, String model, RestTemplate restTemplate) {
        this.apiKey = apiKey;
        this.model = model;
        this.restTemplate = restTemplate;
    }
```

나머지 `pickBestMatch(...)` 메서드는 수정하지 않는다 (이미 `this.restTemplate`을 사용 중).

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.service.LlmJobSearchClientTest"`
Expected: PASS (5개 테스트 모두)

- [ ] **Step 5: Run full backend suite to confirm no regression**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/attempt/service/LlmJobSearchClient.java \
        backend/src/test/java/com/example/attempt/service/LlmJobSearchClientTest.java
git commit -m "refactor(search): inject RestTemplate into LlmJobSearchClient for testability, add unit tests"
```

---

### Task 4: 모바일 위젯 테스트 공용 유틸 + PhoneLoginScreen/OtpVerifyScreen 테스트

**Files:**
- Create: `mobile/test/support/fake_api_client.dart`
- Create: `mobile/test/features/auth/phone_login_screen_test.dart`
- Create: `mobile/test/features/auth/otp_verify_screen_test.dart`

**Interfaces:**
- Produces: `FakeSecureStore` (SecureStore 구현), `fakeApiClient(Future<ResponseBody> Function(RequestOptions) onFetch): ApiClient`, `jsonResponse(String body, {int statusCode}): ResponseBody` — 이후 모든 화면 위젯 테스트 태스크(5~8)가 이 유틸을 그대로 가져다 쓴다

- [ ] **Step 1: Create the shared fake API client helper**

`mobile/test/support/fake_api_client.dart`:

```dart
import 'package:dio/dio.dart';
import 'package:senior_job_attendance/core/api_client.dart';
import 'package:senior_job_attendance/core/token_storage.dart';

/// 위젯 테스트에서 flutter_secure_storage 플랫폼 채널을 타지 않도록 하는 가짜 저장소.
class FakeSecureStore implements SecureStore {
  @override
  Future<void> write({required String key, required String? value}) async {}

  @override
  Future<String?> read({required String key}) async => 'test-access-token';
}

/// 요청 경로/쿼리를 보고 원하는 응답(또는 예외)을 콜백으로 정의하는 가짜 어댑터.
class CallbackAdapter implements HttpClientAdapter {
  final Future<ResponseBody> Function(RequestOptions options) onFetch;
  CallbackAdapter(this.onFetch);

  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
      RequestOptions options, Stream<List<int>>? requestStream, Future<void>? cancelFuture) {
    return onFetch(options);
  }
}

ResponseBody jsonResponse(String body, {int statusCode = 200}) {
  return ResponseBody.fromString(body, statusCode, headers: {
    Headers.contentTypeHeader: [Headers.jsonContentType],
  });
}

/// apiClientProvider를 오버라이드할 때 쓰는 가짜 ApiClient.
/// [onFetch]에서 options.path/queryParameters를 보고 원하는 응답을 반환하거나 예외를 던지면 된다.
ApiClient fakeApiClient(Future<ResponseBody> Function(RequestOptions options) onFetch) {
  final client = ApiClient(baseUrl: 'http://test', tokenStorage: TokenStorage(FakeSecureStore()));
  client.dio.httpClientAdapter = CallbackAdapter(onFetch);
  return client;
}
```

- [ ] **Step 2: Write the PhoneLoginScreen tests**

`mobile/test/features/auth/phone_login_screen_test.dart`:

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/features/auth/phone_login_screen.dart';

import '../../support/fake_api_client.dart';

void main() {
  testWidgets('전화번호 입력 후 인증번호 받기를 누르면 OTP 요청 후 인증번호 입력 화면으로 이동한다', (tester) async {
    bool otpRequested = false;

    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          if (options.path == '/api/v1/member-auth/otp/request') {
            otpRequested = true;
            return jsonResponse('{}');
          }
          throw StateError('예상치 못한 요청: ${options.path}');
        })),
      ],
      child: const MaterialApp(home: PhoneLoginScreen()),
    ));

    await tester.enterText(find.byType(TextField), '01012345678');
    await tester.tap(find.text('인증번호 받기'));
    await tester.pumpAndSettle();

    expect(otpRequested, isTrue);
    expect(find.text('인증번호 6자리'), findsOneWidget);
  });

  testWidgets('요청 처리 중에는 버튼이 비활성화되고 전송 중으로 표시된다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          await Future<void>.delayed(const Duration(milliseconds: 100));
          return jsonResponse('{}');
        })),
      ],
      child: const MaterialApp(home: PhoneLoginScreen()),
    ));

    await tester.enterText(find.byType(TextField), '01012345678');
    await tester.tap(find.text('인증번호 받기'));
    await tester.pump();

    expect(find.text('전송 중...'), findsOneWidget);
    final button = tester.widget<ElevatedButton>(find.byType(ElevatedButton));
    expect(button.onPressed, isNull);

    await tester.pumpAndSettle();
  });
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `cd mobile && flutter test test/features/auth/phone_login_screen_test.dart`
Expected: PASS (2개 테스트 모두 — 화면과 헬퍼 모두 이 태스크에서 함께 새로 작성했으므로, 통과 여부로 헬퍼(`fakeApiClient`)와 테스트 자체가 올바른지 확인한다)

- [ ] **Step 4: Write and run OtpVerifyScreen tests**

`mobile/test/features/auth/otp_verify_screen_test.dart`:

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/features/auth/otp_verify_screen.dart';
import 'package:senior_job_attendance/features/unit_selection/unit_selection_screen.dart';

import '../../support/fake_api_client.dart';

void main() {
  Widget appWithRoutes(Widget home) {
    return MaterialApp(
      home: home,
      routes: {
        '/unit-selection': (context) => const UnitSelectionScreen(),
      },
    );
  }

  testWidgets('정확한 인증번호를 입력하면 사업단 유형 선택 화면으로 이동한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          if (options.path == '/api/v1/member-auth/otp/verify') {
            return jsonResponse('{"accessToken":"fake-token","memberId":1}');
          }
          throw StateError('예상치 못한 요청: ${options.path}');
        })),
      ],
      child: appWithRoutes(const OtpVerifyScreen(phoneNumber: '01012345678')),
    ));

    await tester.enterText(find.byType(TextField), '123456');
    await tester.tap(find.text('확인'));
    await tester.pumpAndSettle();

    expect(find.text('사업단 유형 선택'), findsOneWidget);
  });

  testWidgets('틀린 인증번호를 입력하면 에러 메시지를 보여주고 화면을 유지한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          return jsonResponse('{"error":"인증번호가 올바르지 않습니다."}', statusCode: 401);
        })),
      ],
      child: appWithRoutes(const OtpVerifyScreen(phoneNumber: '01012345678')),
    ));

    await tester.enterText(find.byType(TextField), '000000');
    await tester.tap(find.text('확인'));
    await tester.pumpAndSettle();

    expect(find.text('인증번호가 올바르지 않습니다.'), findsOneWidget);
    expect(find.text('인증번호 6자리'), findsOneWidget);
  });
}
```

Run: `cd mobile && flutter test test/features/auth/otp_verify_screen_test.dart`
Expected: PASS (2개 테스트 모두)

- [ ] **Step 5: Run full mobile suite to confirm no regression**

Run: `cd mobile && flutter test`
Expected: 전체 통과

- [ ] **Step 6: Commit**

```bash
git add mobile/test/support/fake_api_client.dart \
        mobile/test/features/auth/phone_login_screen_test.dart \
        mobile/test/features/auth/otp_verify_screen_test.dart
git commit -m "test(auth): add widget tests for phone login and OTP verify screens"
```

---

### Task 5: UnitSelectionScreen 위젯 테스트

**Files:**
- Test: `mobile/test/features/unit_selection/unit_selection_screen_test.dart` (신규)

**Interfaces:**
- Consumes: `fakeApiClient`, `jsonResponse` (Task 4)

- [ ] **Step 1: Write the test**

`mobile/test/features/unit_selection/unit_selection_screen_test.dart`:

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/core/unit_type.dart';
import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/features/unit_selection/unit_selection_screen.dart';

import '../../support/fake_api_client.dart';

void main() {
  testWidgets('사업단 유형 개수만큼 선택 버튼이 뜬다', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: UnitSelectionScreen()));

    for (final type in UnitType.values) {
      expect(find.text(type.label), findsOneWidget);
    }
  });

  testWidgets('유형 하나를 탭하면 해당 유형의 일자리 검색 화면으로 이동한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async => jsonResponse('[]'))),
      ],
      child: const MaterialApp(home: UnitSelectionScreen()),
    ));

    await tester.tap(find.text(UnitType.market.label));
    await tester.pumpAndSettle();

    expect(find.text('${UnitType.market.label} 일자리 찾기'), findsOneWidget);
  });
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `cd mobile && flutter test test/features/unit_selection/unit_selection_screen_test.dart`
Expected: PASS (2개 테스트 모두 — 화면이 이미 구현되어 있으므로 신규 구현 없이 통과)

- [ ] **Step 3: Commit**

```bash
git add mobile/test/features/unit_selection/unit_selection_screen_test.dart
git commit -m "test(unit-selection): add widget tests for unit type selection screen"
```

---

### Task 6: JobSearchScreen 위젯 테스트

**Files:**
- Test: `mobile/test/features/job_search/job_search_screen_test.dart` (신규)

**Interfaces:**
- Consumes: `fakeApiClient`, `jsonResponse` (Task 4)

- [ ] **Step 1: Write the test**

`mobile/test/features/job_search/job_search_screen_test.dart`:

```dart
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/core/unit_type.dart';
import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/features/job_search/job_search_screen.dart';

import '../../support/fake_api_client.dart';

const _place1Json = '{"id":1,"name":"공원안전지킴이","address":"주소1",'
    '"unitType":"PUBLIC_INTEREST","description":null,"latitude":35.3,"longitude":129.0}';

Widget _wrap(Widget child, Future<ResponseBody> Function(RequestOptions) onFetch) {
  return ProviderScope(
    overrides: [apiClientProvider.overrideWithValue(fakeApiClient(onFetch))],
    child: MaterialApp(home: child),
  );
}

void main() {
  testWidgets('화면 진입 시 해당 유형의 일자리 목록이 자동으로 뜬다', (tester) async {
    await tester.pumpWidget(_wrap(
      const JobSearchScreen(unitType: UnitType.publicInterest),
      (options) async => jsonResponse('[$_place1Json]'),
    ));
    await tester.pumpAndSettle();

    expect(find.text('공원안전지킴이'), findsOneWidget);
  });

  testWidgets('목록 로드에 실패하면 에러 안내를 보여준다', (tester) async {
    await tester.pumpWidget(_wrap(
      const JobSearchScreen(unitType: UnitType.publicInterest),
      (options) async => jsonResponse('{"message":"실패"}', statusCode: 500),
    ));
    await tester.pumpAndSettle();

    expect(find.text('일자리 목록을 불러오지 못했습니다. 다시 시도해주세요.'), findsOneWidget);
  });

  testWidgets('검색 결과가 0건이면 AI로 더 찾아보기 버튼이 뜬다', (tester) async {
    await tester.pumpWidget(_wrap(
      const JobSearchScreen(unitType: UnitType.publicInterest),
      (options) async {
        if (options.queryParameters['q'] == '존재안함') {
          return jsonResponse('[]');
        }
        return jsonResponse('[$_place1Json]');
      },
    ));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), '존재안함');
    await tester.tap(find.byIcon(Icons.search));
    await tester.pumpAndSettle();

    expect(find.text('AI로 더 찾아보기'), findsOneWidget);
  });

  testWidgets('AI로 더 찾아보기를 탭하면 AI 폴백 검색 결과로 목록이 갱신된다', (tester) async {
    await tester.pumpWidget(_wrap(
      const JobSearchScreen(unitType: UnitType.publicInterest),
      (options) async {
        if (options.path == '/api/v1/places/search/fallback') {
          return jsonResponse('[$_place1Json]');
        }
        return jsonResponse('[]');
      },
    ));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), '학교 앞에서 깃발');
    await tester.tap(find.byIcon(Icons.search));
    await tester.pumpAndSettle();

    await tester.tap(find.text('AI로 더 찾아보기'));
    await tester.pumpAndSettle();

    expect(find.text('공원안전지킴이'), findsOneWidget);
  });

  testWidgets('목록에서 항목을 탭하면 본인 일자리로 등록하고 동의 화면으로 이동한다', (tester) async {
    await tester.pumpWidget(_wrap(
      const JobSearchScreen(unitType: UnitType.publicInterest),
      (options) async {
        if (options.path == '/api/v1/members/me/assign-place') {
          return jsonResponse('{}');
        }
        return jsonResponse('[$_place1Json]');
      },
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.text('공원안전지킴이'));
    await tester.pumpAndSettle();

    expect(find.text('위치정보 수집 동의'), findsOneWidget);
  });
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `cd mobile && flutter test test/features/job_search/job_search_screen_test.dart`
Expected: PASS (5개 테스트 모두 — 화면이 이미 구현되어 있으므로 신규 구현 없이 통과)

- [ ] **Step 3: Commit**

```bash
git add mobile/test/features/job_search/job_search_screen_test.dart
git commit -m "test(job-search): add widget tests covering load, search, AI fallback, and selection"
```

---

### Task 7: ConsentScreen 위젯 테스트

**Files:**
- Test: `mobile/test/features/consent/consent_screen_test.dart` (신규)

**Interfaces:**
- Consumes: `fakeApiClient`, `jsonResponse` (Task 4)

- [ ] **Step 1: Write the test**

`mobile/test/features/consent/consent_screen_test.dart`:

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/features/consent/consent_screen.dart';

import '../../support/fake_api_client.dart';

void main() {
  testWidgets('동의 체크 전에는 계속하기 버튼이 비활성화된다', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: MaterialApp(home: ConsentScreen())));

    final button = tester.widget<ElevatedButton>(find.byType(ElevatedButton));
    expect(button.onPressed, isNull);
  });

  testWidgets('동의 체크 후 제출에 성공하면 출석 체크 화면으로 이동한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async => jsonResponse('{}'))),
      ],
      child: const MaterialApp(home: ConsentScreen()),
    ));

    await tester.tap(find.byType(CheckboxListTile));
    await tester.pump();
    await tester.tap(find.text('동의하고 계속하기'));
    await tester.pumpAndSettle();

    expect(find.text('출석 체크'), findsOneWidget);
  });

  testWidgets('제출 중 서버 오류가 나면 에러 안내를 보여주고 화면을 유지한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async => jsonResponse('{}', statusCode: 500))),
      ],
      child: const MaterialApp(home: ConsentScreen()),
    ));

    await tester.tap(find.byType(CheckboxListTile));
    await tester.pump();
    await tester.tap(find.text('동의하고 계속하기'));
    await tester.pumpAndSettle();

    expect(find.text('동의 처리에 실패했습니다. 다시 시도해주세요.'), findsOneWidget);
    expect(find.text('위치정보 수집 동의'), findsOneWidget);
  });
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `cd mobile && flutter test test/features/consent/consent_screen_test.dart`
Expected: PASS (3개 테스트 모두 — 화면이 이미 구현되어 있으므로 신규 구현 없이 통과)

- [ ] **Step 3: Commit**

```bash
git add mobile/test/features/consent/consent_screen_test.dart
git commit -m "test(consent): add widget tests for consent screen"
```

---

### Task 8: CheckinScreen 위젯 테스트 (Geolocator mock)

**Files:**
- Modify: `mobile/pubspec.yaml`
- Test: `mobile/test/features/checkin/checkin_screen_test.dart` (신규)

**Interfaces:**
- Consumes: `fakeApiClient`, `jsonResponse` (Task 4), `geolocator_platform_interface`의 `GeolocatorPlatform`/`Position`/`LocationPermission`/`LocationServiceDisabledException`

- [ ] **Step 1: Add geolocator_platform_interface as a direct dev dependency**

`mobile/pubspec.yaml`의 `dev_dependencies:` 블록에 추가 (버전은 현재 `pubspec.lock`에 이미 해석되어 있는 버전과 맞춤):

```yaml
dev_dependencies:
  flutter_test:
    sdk: flutter
  geolocator_platform_interface: ^4.2.8
  flutter_lints: ^6.0.0
```

Run: `cd mobile && flutter pub get`
Expected: 정상 완료 (이미 transitive dependency로 해석되어 있던 버전이라 lock 파일 버전 변경 없음)

- [ ] **Step 2: Write the test**

`mobile/test/features/checkin/checkin_screen_test.dart`:

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geolocator_platform_interface/geolocator_platform_interface.dart';
import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/features/checkin/checkin_screen.dart';

import '../../support/fake_api_client.dart';

class _FakeGeolocatorPlatform extends GeolocatorPlatform {
  final LocationPermission permission;
  final Position? position;
  final bool throwOnPosition;

  _FakeGeolocatorPlatform({required this.permission, this.position, this.throwOnPosition = false});

  @override
  Future<LocationPermission> requestPermission() async => permission;

  @override
  Future<Position> getCurrentPosition({LocationSettings? locationSettings}) async {
    if (throwOnPosition) {
      throw const LocationServiceDisabledException();
    }
    return position!;
  }
}

Position _fakePosition() {
  return Position(
    latitude: 35.3,
    longitude: 129.0,
    timestamp: DateTime.now(),
    accuracy: 1.0,
    altitude: 0.0,
    altitudeAccuracy: 1.0,
    heading: 0.0,
    headingAccuracy: 1.0,
    speed: 0.0,
    speedAccuracy: 1.0,
  );
}

void main() {
  testWidgets('위치 권한을 거부하면 안내 메시지를 보여주고 서버를 호출하지 않는다', (tester) async {
    GeolocatorPlatform.instance = _FakeGeolocatorPlatform(permission: LocationPermission.denied);
    bool checkInCalled = false;

    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          checkInCalled = true;
          return jsonResponse('{}');
        })),
      ],
      child: const MaterialApp(home: CheckinScreen()),
    ));

    await tester.tap(find.text('출석 체크'));
    await tester.pumpAndSettle();

    expect(find.text('위치 권한이 필요합니다. 설정에서 위치 권한을 허용해주세요.'), findsOneWidget);
    expect(checkInCalled, isFalse);
  });

  testWidgets('위치 확인과 체크인이 성공하면 서버 응답 메시지를 그대로 보여준다', (tester) async {
    GeolocatorPlatform.instance = _FakeGeolocatorPlatform(
      permission: LocationPermission.whileInUse,
      position: _fakePosition(),
    );

    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          return jsonResponse('{"success":true,"message":"출석 처리되었습니다."}');
        })),
      ],
      child: const MaterialApp(home: CheckinScreen()),
    ));

    await tester.tap(find.text('출석 체크'));
    await tester.pumpAndSettle();

    expect(find.text('출석 처리되었습니다.'), findsOneWidget);
  });

  testWidgets('위치 서비스가 꺼져 있으면 위치 확인 실패 안내를 보여준다', (tester) async {
    GeolocatorPlatform.instance = _FakeGeolocatorPlatform(
      permission: LocationPermission.whileInUse,
      throwOnPosition: true,
    );

    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async => jsonResponse('{}'))),
      ],
      child: const MaterialApp(home: CheckinScreen()),
    ));

    await tester.tap(find.text('출석 체크'));
    await tester.pumpAndSettle();

    expect(find.text('위치 확인에 실패했습니다. 위치 서비스가 켜져 있는지 확인해주세요.'), findsOneWidget);
  });
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `cd mobile && flutter test test/features/checkin/checkin_screen_test.dart`
Expected: PASS (3개 테스트 모두 — 화면이 이미 구현되어 있으므로 신규 구현 없이 통과)

- [ ] **Step 4: Commit**

```bash
git add mobile/pubspec.yaml mobile/pubspec.lock mobile/test/features/checkin/checkin_screen_test.dart
git commit -m "test(checkin): add widget tests covering permission, success, and location-service-off paths"
```

---

### Task 9: 전체 회귀 확인

**Files:** 없음 (검증 전용 태스크)

- [ ] **Step 1: Run full backend suite**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL, 실패 0건

- [ ] **Step 2: Run full mobile suite**

Run: `cd mobile && flutter test`
Expected: 전체 통과, 실패 0건

- [ ] **Step 3: Report**

두 스위트 모두 통과하면 정답지(`docs/superpowers/specs/2026-07-13-backend-mobile-test-coverage-design.md`)의 T1~T13(백엔드) 및 화면별 시나리오(모바일)가 전부 구현·검증된 것으로 간주하고 사용자에게 완료를 보고한다.
