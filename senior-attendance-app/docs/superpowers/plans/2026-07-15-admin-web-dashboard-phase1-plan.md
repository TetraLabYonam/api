# 관리자 웹 대시보드 Phase 1 (로그인 + 사업단별 출석률 로비) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자가 `admin@example.com`/`1234`로 로그인해서 사업단 유형(공익형/시장형/사회서비스형)별 출석률을 오늘/이번주/이번달 기준으로 확인할 수 있는 웹 대시보드를 만든다.

**Architecture:** 백엔드(Spring Boot)에 관리자 전용 집계 API 1개(`GET /api/admin/attendance/summary`)를 추가하고, 새 React SPA(`admin-web`)가 기존 `/api/auth/login` JWT 인증 체계를 그대로 사용해 그 API를 호출한다. accessToken은 브라우저 메모리에만 두고, 새로고침 시 HttpOnly 리프레시 쿠키로 세션을 복원한다.

**Tech Stack:** Spring Boot(기존) / React + Vite + TypeScript + react-router-dom + Vitest + React Testing Library(신규)

## Global Constraints

- 로그인은 실제 백엔드(`/api/auth/login`)와 연동한다 — 프론트엔드 하드코딩/목업 금지.
- 기본 관리자 계정은 `admin@example.com` / `1234`로 변경한다 (기존 `admin`/`admin` 기본값 대체).
- accessToken은 브라우저 `localStorage`에 저장하지 않는다 — React 메모리(모듈 변수/Context)에만 보관.
- 신규 관리자 전용 백엔드 API는 `/api/admin/**` 경로 하위에 두고 `hasRole("ADMIN")`으로 잠근다.
- 출석률 계산은 `Schedule.getAttendanceRate()`와 동일한 정의(`(PRESENT + LATE) / 전체 * 100`)를 따른다.
- 사업단 유형 한글 라벨은 `UnitType.getDescription()`을 그대로 사용한다 — 프론트/백엔드 어디에도 별도로 하드코딩하지 않는다.
- `period` 파라미터는 `today` / `week` / `month` 세 가지만 허용하고, `week`는 ISO 월요일 기준이다.
- 응답에는 항상 `UnitType` 3종이 모두 포함되어야 한다 (데이터 없는 유형은 0%).
- 백엔드 테스트는 기존 패턴(`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`, 실제 로그인 플로우로 토큰 발급)을 따른다.
- 프론트엔드 테스트는 Vitest + React Testing Library를 사용한다.

---

## 파일 구조 개요

```
senior-attendance-app/
├── backend/
│   ├── src/main/java/com/example/attempt/
│   │   ├── config/AdminDataInitializer.java        (수정)
│   │   ├── config/SecurityConfig.java              (수정)
│   │   ├── controller/AdminAttendanceController.java (신규)
│   │   ├── service/AdminAttendanceService.java       (신규)
│   │   ├── repository/AttendRepository.java          (수정)
│   │   └── dto/admin/AttendanceSummaryResponse.java  (신규)
│   └── src/test/java/com/example/attempt/
│       ├── controller/AuthControllerIntegrationTest.java (수정)
│       ├── controller/AdminAttendanceControllerIntegrationTest.java (신규)
│       ├── service/AdminAttendanceServiceTest.java   (신규)
│       └── repository/AttendRepositoryUnitTypeSummaryTest.java (신규)
└── admin-web/                                        (신규 프로젝트 전체)
    └── src/
        ├── main.tsx, App.tsx
        ├── api/client.ts
        ├── test/setup.ts, test/support/fakeFetch.ts
        ├── types/attendance.ts
        └── features/
            ├── auth/AuthContext.tsx, LoginPage.tsx
            └── lobby/LobbyPage.tsx, UnitTypeCard.tsx, PeriodSelector.tsx
```

---

### Task 1: 관리자 시드 계정 변경 (admin@example.com/1234)

**Files:**
- Modify: `backend/src/main/java/com/example/attempt/config/AdminDataInitializer.java`
- Modify: `backend/src/test/java/com/example/attempt/controller/AuthControllerIntegrationTest.java`

**Interfaces:**
- Produces: 기본 관리자 계정 `admin@example.com` / `1234` (이후 모든 태스크의 ADMIN 토큰 발급 전제)

- [ ] **Step 1: `AdminDataInitializer`의 `@Value` 기본값 변경**

`backend/src/main/java/com/example/attempt/config/AdminDataInitializer.java`의 아래 두 줄을:

```java
                                           @Value("${app.default-admin.username:admin}") String defaultAdmin,
                                           @Value("${app.default-admin.password:admin}") String defaultPassword) {
```

다음으로 교체한다:

```java
                                           @Value("${app.default-admin.username:admin@example.com}") String defaultAdmin,
                                           @Value("${app.default-admin.password:1234}") String defaultPassword) {
```

- [ ] **Step 2: 기존 `AuthControllerIntegrationTest`가 새 기본값을 쓰도록 갱신**

`backend/src/test/java/com/example/attempt/controller/AuthControllerIntegrationTest.java`에서:

```java
        Map<String, String> body = Map.of("username", "admin", "password", "admin");
```

를 다음으로 교체한다:

```java
        Map<String, String> body = Map.of("username", "admin@example.com", "password", "1234");
```

- [ ] **Step 3: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.controller.AuthControllerIntegrationTest"`
Expected: `BUILD SUCCESSFUL`, `loginAndRefresh_flow` 테스트 PASS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/example/attempt/config/AdminDataInitializer.java \
        backend/src/test/java/com/example/attempt/controller/AuthControllerIntegrationTest.java
git commit -m "feat(admin): change default admin seed credentials to admin@example.com/1234"
```

---

### Task 2: AttendRepository 사업단 유형별 출석 집계 쿼리

**Files:**
- Modify: `backend/src/main/java/com/example/attempt/repository/AttendRepository.java`
- Test: `backend/src/test/java/com/example/attempt/repository/AttendRepositoryUnitTypeSummaryTest.java`

**Interfaces:**
- Produces: `AttendRepository.getAttendanceStatsByUnitTypeAndDateRange(LocalDate start, LocalDate end): List<Object[]>` — 각 `Object[]`는 `[UnitType unitType, AttendStatus status, Long count]`

- [ ] **Step 1: 실패하는 리포지토리 테스트 작성**

Create `backend/src/test/java/com/example/attempt/repository/AttendRepositoryUnitTypeSummaryTest.java`:

```java
package com.example.attempt.repository;

import com.example.attempt.domain.Attend;
import com.example.attempt.domain.AttendStatus;
import com.example.attempt.domain.Member;
import com.example.attempt.domain.Place;
import com.example.attempt.domain.Schedule;
import com.example.attempt.domain.UnitType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AttendRepositoryUnitTypeSummaryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AttendRepository attendRepository;

    @Test
    void getAttendanceStatsByUnitTypeAndDateRange_groupsByUnitTypeAndStatus_withinDateRange() {
        Place publicPlace = new Place("공원안전지킴이", "주소1", 35.3, 129.0);
        publicPlace.setUnitType(UnitType.PUBLIC_INTEREST);
        entityManager.persist(publicPlace);

        Place marketPlace = new Place("동네마당재활용", "주소2", 35.4, 129.1);
        marketPlace.setUnitType(UnitType.MARKET);
        entityManager.persist(marketPlace);

        Member member = new Member("홍길동", "01011112222");
        entityManager.persist(member);

        Schedule inRangeSchedule = Schedule.builder()
                .title("오전 근무")
                .scheduleDate(LocalDate.of(2026, 7, 10))
                .place(publicPlace)
                .build();
        entityManager.persist(inRangeSchedule);

        Schedule outOfRangeSchedule = Schedule.builder()
                .title("과거 근무")
                .scheduleDate(LocalDate.of(2026, 1, 1))
                .place(publicPlace)
                .build();
        entityManager.persist(outOfRangeSchedule);

        Schedule marketSchedule = Schedule.builder()
                .title("시장 근무")
                .scheduleDate(LocalDate.of(2026, 7, 11))
                .place(marketPlace)
                .build();
        entityManager.persist(marketSchedule);

        entityManager.persist(Attend.builder().member(member).schedule(inRangeSchedule).status(AttendStatus.PRESENT).build());
        entityManager.persist(Attend.builder().member(member).schedule(outOfRangeSchedule).status(AttendStatus.PRESENT).build());
        entityManager.persist(Attend.builder().member(member).schedule(marketSchedule).status(AttendStatus.LATE).build());
        entityManager.flush();

        List<Object[]> result = attendRepository.getAttendanceStatsByUnitTypeAndDateRange(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertEquals(2, result.size());

        boolean hasPublicPresent = result.stream().anyMatch(row ->
                row[0] == UnitType.PUBLIC_INTEREST && row[1] == AttendStatus.PRESENT && ((Long) row[2]) == 1L);
        boolean hasMarketLate = result.stream().anyMatch(row ->
                row[0] == UnitType.MARKET && row[1] == AttendStatus.LATE && ((Long) row[2]) == 1L);

        assertTrue(hasPublicPresent);
        assertTrue(hasMarketLate);
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인 (메서드 없음)**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.repository.AttendRepositoryUnitTypeSummaryTest"`
Expected: FAIL — `cannot find symbol: method getAttendanceStatsByUnitTypeAndDateRange`

- [ ] **Step 3: `AttendRepository`에 집계 쿼리 메서드 추가**

`backend/src/main/java/com/example/attempt/repository/AttendRepository.java`의 클래스 마지막 (`deleteByMemberId` 메서드 선언 다음)에 추가:

```java

    /**
     * 사업단 유형별·출석 상태별 집계 조회 (관리자 대시보드용)
     */
    @Query("SELECT p.unitType, a.status, COUNT(a) FROM Attend a " +
           "JOIN a.schedule s JOIN s.place p " +
           "WHERE s.scheduleDate BETWEEN :start AND :end " +
           "GROUP BY p.unitType, a.status")
    List<Object[]> getAttendanceStatsByUnitTypeAndDateRange(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.repository.AttendRepositoryUnitTypeSummaryTest"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/attempt/repository/AttendRepository.java \
        backend/src/test/java/com/example/attempt/repository/AttendRepositoryUnitTypeSummaryTest.java
git commit -m "feat(admin): add unit-type attendance aggregation query to AttendRepository"
```

---

### Task 3: AdminAttendanceService (기간→날짜 변환 + 출석률 계산)

**Files:**
- Create: `backend/src/main/java/com/example/attempt/dto/admin/AttendanceSummaryResponse.java`
- Create: `backend/src/main/java/com/example/attempt/service/AdminAttendanceService.java`
- Test: `backend/src/test/java/com/example/attempt/service/AdminAttendanceServiceTest.java`

**Interfaces:**
- Consumes: `AttendRepository.getAttendanceStatsByUnitTypeAndDateRange(LocalDate, LocalDate): List<Object[]>` (Task 2)
- Produces: `AdminAttendanceService.getSummary(String period): List<AttendanceSummaryResponse>`, `AttendanceSummaryResponse { String unitType; String label; double attendanceRate; }` (Task 4의 컨트롤러가 사용)

- [ ] **Step 1: DTO 작성**

Create `backend/src/main/java/com/example/attempt/dto/admin/AttendanceSummaryResponse.java`:

```java
package com.example.attempt.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 사업단 유형별 출석률 응답 DTO (관리자 대시보드)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSummaryResponse {
    private String unitType;
    private String label;
    private double attendanceRate;
}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

Create `backend/src/test/java/com/example/attempt/service/AdminAttendanceServiceTest.java`:

```java
package com.example.attempt.service;

import com.example.attempt.domain.AttendStatus;
import com.example.attempt.domain.UnitType;
import com.example.attempt.dto.admin.AttendanceSummaryResponse;
import com.example.attempt.repository.AttendRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminAttendanceServiceTest {

    private AttendRepository attendRepository;
    private AdminAttendanceService service;

    @BeforeEach
    void setup() {
        attendRepository = mock(AttendRepository.class);
        service = new AdminAttendanceService(attendRepository);
    }

    @Test
    void resolveStartDate_today_returnsSameDate() {
        LocalDate today = LocalDate.of(2026, 7, 15); // 수요일
        assertEquals(today, AdminAttendanceService.resolveStartDate("today", today));
    }

    @Test
    void resolveStartDate_week_returnsMondayOfThatWeek() {
        LocalDate wednesday = LocalDate.of(2026, 7, 15);
        LocalDate expectedMonday = LocalDate.of(2026, 7, 13);
        assertEquals(expectedMonday, AdminAttendanceService.resolveStartDate("week", wednesday));
    }

    @Test
    void resolveStartDate_week_whenTodayIsMonday_returnsSameDate() {
        LocalDate monday = LocalDate.of(2026, 7, 13);
        assertEquals(monday, AdminAttendanceService.resolveStartDate("week", monday));
    }

    @Test
    void resolveStartDate_month_returnsFirstDayOfMonth() {
        LocalDate today = LocalDate.of(2026, 7, 15);
        LocalDate expectedFirst = LocalDate.of(2026, 7, 1);
        assertEquals(expectedFirst, AdminAttendanceService.resolveStartDate("month", today));
    }

    @Test
    void resolveStartDate_unknownPeriod_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> AdminAttendanceService.resolveStartDate("year", LocalDate.now()));
    }

    @Test
    void getSummary_calculatesRatePerUnitType_andIncludesAllThreeTypesEvenWithNoData() {
        when(attendRepository.getAttendanceStatsByUnitTypeAndDateRange(any(), any())).thenReturn(List.of(
                new Object[]{UnitType.PUBLIC_INTEREST, AttendStatus.PRESENT, 3L},
                new Object[]{UnitType.PUBLIC_INTEREST, AttendStatus.ABSENT, 1L},
                new Object[]{UnitType.MARKET, AttendStatus.LATE, 2L},
                new Object[]{UnitType.MARKET, AttendStatus.PRESENT, 8L}
        ));

        List<AttendanceSummaryResponse> result = service.getSummary("today");

        assertEquals(3, result.size());

        AttendanceSummaryResponse publicInterest = result.stream()
                .filter(r -> r.getUnitType().equals("PUBLIC_INTEREST")).findFirst().orElseThrow();
        assertEquals("공익형", publicInterest.getLabel());
        assertEquals(75.0, publicInterest.getAttendanceRate(), 0.001); // 3 / (3+1) * 100

        AttendanceSummaryResponse market = result.stream()
                .filter(r -> r.getUnitType().equals("MARKET")).findFirst().orElseThrow();
        assertEquals(100.0, market.getAttendanceRate(), 0.001); // (2+8) / 10 * 100

        AttendanceSummaryResponse socialService = result.stream()
                .filter(r -> r.getUnitType().equals("SOCIAL_SERVICE")).findFirst().orElseThrow();
        assertEquals(0.0, socialService.getAttendanceRate(), 0.001); // 데이터 없음
    }
}
```

- [ ] **Step 3: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.service.AdminAttendanceServiceTest"`
Expected: FAIL — `cannot find symbol: class AdminAttendanceService`

- [ ] **Step 4: `AdminAttendanceService` 구현**

Create `backend/src/main/java/com/example/attempt/service/AdminAttendanceService.java`:

```java
package com.example.attempt.service;

import com.example.attempt.domain.AttendStatus;
import com.example.attempt.domain.UnitType;
import com.example.attempt.dto.admin.AttendanceSummaryResponse;
import com.example.attempt.repository.AttendRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자 대시보드용 사업단 유형별 출석률 집계 서비스
 */
@Service
public class AdminAttendanceService {

    private final AttendRepository attendRepository;

    public AdminAttendanceService(AttendRepository attendRepository) {
        this.attendRepository = attendRepository;
    }

    public List<AttendanceSummaryResponse> getSummary(String period) {
        LocalDate today = LocalDate.now();
        LocalDate start = resolveStartDate(period, today);

        List<Object[]> rows = attendRepository.getAttendanceStatsByUnitTypeAndDateRange(start, today);

        // [0] = 출석(PRESENT+LATE) 건수, [1] = 전체 건수
        Map<UnitType, long[]> counts = new EnumMap<>(UnitType.class);
        for (UnitType type : UnitType.values()) {
            counts.put(type, new long[2]);
        }

        for (Object[] row : rows) {
            UnitType unitType = (UnitType) row[0];
            AttendStatus status = (AttendStatus) row[1];
            long count = (Long) row[2];

            long[] bucket = counts.get(unitType);
            bucket[1] += count;
            if (status == AttendStatus.PRESENT || status == AttendStatus.LATE) {
                bucket[0] += count;
            }
        }

        List<AttendanceSummaryResponse> result = new ArrayList<>();
        for (UnitType type : UnitType.values()) {
            long[] bucket = counts.get(type);
            double rate = bucket[1] == 0 ? 0.0 : (bucket[0] * 100.0) / bucket[1];
            result.add(new AttendanceSummaryResponse(type.name(), type.getDescription(), rate));
        }
        return result;
    }

    static LocalDate resolveStartDate(String period, LocalDate today) {
        return switch (period) {
            case "today" -> today;
            case "week" -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "month" -> today.withDayOfMonth(1);
            default -> throw new IllegalArgumentException("알 수 없는 period 값입니다: " + period);
        };
    }
}
```

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.service.AdminAttendanceServiceTest"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/attempt/dto/admin/AttendanceSummaryResponse.java \
        backend/src/main/java/com/example/attempt/service/AdminAttendanceService.java \
        backend/src/test/java/com/example/attempt/service/AdminAttendanceServiceTest.java
git commit -m "feat(admin): add AdminAttendanceService for unit-type attendance rate summary"
```

---

### Task 4: AdminAttendanceController + SecurityConfig ADMIN 권한

**Files:**
- Create: `backend/src/main/java/com/example/attempt/controller/AdminAttendanceController.java`
- Modify: `backend/src/main/java/com/example/attempt/config/SecurityConfig.java`
- Test: `backend/src/test/java/com/example/attempt/controller/AdminAttendanceControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `AdminAttendanceService.getSummary(String period): List<AttendanceSummaryResponse>` (Task 3)
- Produces: `GET /api/admin/attendance/summary?period=today|week|month` → `200 OK` with `AttendanceSummaryResponse[]` JSON (admin-web 프론트엔드가 사용)

- [ ] **Step 1: 실패하는 통합 테스트 작성**

Create `backend/src/test/java/com/example/attempt/controller/AdminAttendanceControllerIntegrationTest.java`:

```java
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
class AdminAttendanceControllerIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @MockBean
    SmsService smsService;

    private String obtainAdminAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> req = new HttpEntity<>(
                Map.of("username", "admin@example.com", "password", "1234"), headers);
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/auth/login", req, Map.class);
        return (String) resp.getBody().get("accessToken");
    }

    /**
     * MemberAuthControllerIntegrationTest와 동일한 방식으로 실제 ROLE_MEMBER accessToken을 얻는다.
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
    void getSummary_withoutAuth_returns401() {
        String url = "http://localhost:" + port + "/api/admin/attendance/summary?period=today";
        ResponseEntity<Object> resp = restTemplate.getForEntity(url, Object.class);
        assertEquals(401, resp.getStatusCodeValue());
    }

    @Test
    void getSummary_withMemberToken_returns403() {
        String accessToken = obtainMemberAccessToken("01099998888");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        String url = "http://localhost:" + port + "/api/admin/attendance/summary?period=today";
        ResponseEntity<Object> resp = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Object.class);

        assertEquals(403, resp.getStatusCodeValue());
    }

    @Test
    void getSummary_withAdminToken_returnsAllThreeUnitTypes() {
        String accessToken = obtainAdminAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        String url = "http://localhost:" + port + "/api/admin/attendance/summary?period=today";
        ResponseEntity<Object[]> resp = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Object[].class);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(3, resp.getBody().length);
    }

    @Test
    void getSummary_withInvalidPeriod_returns400() {
        String accessToken = obtainAdminAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        String url = "http://localhost:" + port + "/api/admin/attendance/summary?period=year";
        ResponseEntity<Object> resp = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Object.class);

        assertEquals(400, resp.getStatusCodeValue());
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.controller.AdminAttendanceControllerIntegrationTest"`
Expected: FAIL — 404 (컨트롤러 없음)

- [ ] **Step 3: `SecurityConfig`에 `/api/admin/**` ADMIN 권한 추가**

`backend/src/main/java/com/example/attempt/config/SecurityConfig.java`의 `authorizeHttpRequests` 블록에서:

```java
                    // 회원 본인 서비스 및 장소 검색 API는 MEMBER 권한 필요
                    .requestMatchers("/api/v1/members/me/**", "/api/v1/places/**", "/api/v1/attend/**").hasRole("MEMBER")
                    // 그 외 모든 요청은 인증 필요
                    .anyRequest().authenticated()
```

를 다음으로 교체한다 (관리자 전용 경로를 명시적으로 잠그는 줄 추가):

```java
                    // 회원 본인 서비스 및 장소 검색 API는 MEMBER 권한 필요
                    .requestMatchers("/api/v1/members/me/**", "/api/v1/places/**", "/api/v1/attend/**").hasRole("MEMBER")
                    // 관리자 전용 API는 ADMIN 권한 필요
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    // 그 외 모든 요청은 인증 필요
                    .anyRequest().authenticated()
```

- [ ] **Step 4: `AdminAttendanceController` 구현**

Create `backend/src/main/java/com/example/attempt/controller/AdminAttendanceController.java`:

```java
package com.example.attempt.controller;

import com.example.attempt.dto.admin.AttendanceSummaryResponse;
import com.example.attempt.service.AdminAttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAttendanceController {

    private final AdminAttendanceService adminAttendanceService;

    @GetMapping("/attendance/summary")
    public List<AttendanceSummaryResponse> getSummary(@RequestParam String period) {
        return adminAttendanceService.getSummary(period);
    }
}
```

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.controller.AdminAttendanceControllerIntegrationTest"`
Expected: `BUILD SUCCESSFUL`, 4개 테스트 모두 PASS

- [ ] **Step 6: 백엔드 전체 회귀 확인**

Run: `cd backend && ./gradlew test`
Expected: `BUILD SUCCESSFUL`, 실패 0건 (Task 1~4에서 바뀐 시드 계정/보안설정이 기존 테스트에 영향 없는지 확인)

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/example/attempt/controller/AdminAttendanceController.java \
        backend/src/main/java/com/example/attempt/config/SecurityConfig.java \
        backend/src/test/java/com/example/attempt/controller/AdminAttendanceControllerIntegrationTest.java
git commit -m "feat(admin): add AdminAttendanceController and lock /api/admin/** to ROLE_ADMIN"
```

---

### Task 5: admin-web 프로젝트 스캐폴딩

**Files:**
- Create: `admin-web/` 전체 (Vite 스캐폴드)
- Modify: `admin-web/vite.config.ts`
- Create: `admin-web/src/test/setup.ts`

**Interfaces:**
- Produces: `npm run build`, `npm test`(=`vitest run`) 커맨드가 동작하는 빈 React+TS+Vitest 프로젝트 (이후 모든 프론트엔드 태스크의 기반)

- [ ] **Step 1: Vite 스캐폴드 생성**

Run (반드시 `senior-attendance-app/` 디렉터리에서 실행):
```bash
cd senior-attendance-app
npm create vite@latest admin-web -- --template react-ts
```
Expected: `admin-web/` 디렉터리가 생성되고 `package.json`, `src/`, `.gitignore` 등이 포함됨

- [ ] **Step 2: 의존성 설치**

```bash
cd senior-attendance-app/admin-web
npm install
npm install react-router-dom
npm install -D vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom
```

- [ ] **Step 3: `vite.config.ts`에 vitest 설정 추가**

`admin-web/vite.config.ts` 전체를 다음으로 교체:

```typescript
/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
```

- [ ] **Step 4: 테스트 셋업 파일 작성**

Create `admin-web/src/test/setup.ts`:

```typescript
import '@testing-library/jest-dom/vitest';
```

- [ ] **Step 5: `package.json`에 test 스크립트 추가**

`admin-web/package.json`의 `"scripts"` 블록에 아래 줄을 추가한다 (기존 `dev`/`build`/`lint`/`preview` 줄은 그대로 둔다):

```json
    "test": "vitest run",
```

- [ ] **Step 6: 빌드/테스트 러너 동작 확인**

Run: `cd senior-attendance-app/admin-web && npm run build`
Expected: 성공, `dist/` 생성

Run: `cd senior-attendance-app/admin-web && npx vitest run --passWithNoTests`
Expected: 성공 종료 (설정 오류 없이 "no test files found" 취지의 메시지와 함께 exit code 0)

- [ ] **Step 7: Commit**

```bash
cd senior-attendance-app
git add admin-web
git commit -m "chore(admin-web): scaffold Vite React TypeScript project with Vitest"
```

(참고: Vite 스캐폴드가 `admin-web/.gitignore`를 자동 생성하며 `node_modules`/`dist`를 이미 무시하므로, 저장소 루트 `.gitignore`는 수정할 필요 없다. `git add admin-web` 실행 후 `git status`로 `node_modules`가 스테이징되지 않았는지 확인한다.)

---

### Task 6: API 클라이언트 + fake fetch 테스트 헬퍼

**Files:**
- Create: `admin-web/src/api/client.ts`
- Create: `admin-web/src/test/support/fakeFetch.ts`
- Test: `admin-web/src/api/client.test.ts`

**Interfaces:**
- Produces:
  - `setAccessToken(token: string | null): void`
  - `login(username: string, password: string): Promise<boolean>`
  - `refreshAccessToken(): Promise<boolean>`
  - `apiFetch(path: string, init?: RequestInit): Promise<Response>`
  - `installFakeFetch(handler: (url: string, init?: RequestInit) => Response | Promise<Response>): ReturnType<typeof vi.fn>`
  - `jsonResponse(body: unknown, status?: number): Response`
- (이후 모든 화면 컴포넌트 태스크가 `client.ts`를 사용/모킹함)

- [ ] **Step 1: fake fetch 테스트 헬퍼 작성**

Create `admin-web/src/test/support/fakeFetch.ts`:

```typescript
import { vi } from 'vitest';

export type FetchHandler = (url: string, init?: RequestInit) => Response | Promise<Response>;

export function installFakeFetch(handler: FetchHandler) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) =>
    Promise.resolve(handler(url, init)));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
```

- [ ] **Step 2: 실패하는 client 테스트 작성**

Create `admin-web/src/api/client.test.ts`:

```typescript
import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiFetch, login, setAccessToken } from './client';
import { installFakeFetch, jsonResponse } from '../test/support/fakeFetch';

describe('login', () => {
  afterEach(() => {
    setAccessToken(null);
    vi.unstubAllGlobals();
  });

  it('성공하면 true를 반환하고 accessToken을 저장한다', async () => {
    installFakeFetch(() => jsonResponse({ accessToken: 'abc123' }));

    const ok = await login('admin@example.com', '1234');

    expect(ok).toBe(true);
  });

  it('401이면 false를 반환한다', async () => {
    installFakeFetch(() => jsonResponse({ error: 'Invalid credentials' }, 401));

    const ok = await login('wrong@example.com', 'wrong');

    expect(ok).toBe(false);
  });
});

describe('apiFetch', () => {
  afterEach(() => {
    setAccessToken(null);
    vi.unstubAllGlobals();
  });

  it('accessToken이 있으면 Authorization 헤더를 붙인다', async () => {
    setAccessToken('token-1');
    const fetchMock = installFakeFetch(() => jsonResponse({ ok: true }));

    await apiFetch('/api/admin/attendance/summary?period=today');

    const [, init] = fetchMock.mock.calls[0];
    expect((init?.headers as Record<string, string>).Authorization).toBe('Bearer token-1');
  });

  it('401을 받으면 refresh 후 한 번 재시도한다', async () => {
    setAccessToken('expired-token');
    let attendanceCallCount = 0;
    installFakeFetch((url) => {
      if (url.includes('/api/auth/refresh')) {
        return jsonResponse({ accessToken: 'new-token' });
      }
      attendanceCallCount += 1;
      return attendanceCallCount === 1 ? jsonResponse({}, 401) : jsonResponse({ data: 'ok' });
    });

    const res = await apiFetch('/api/admin/attendance/summary?period=today');

    expect(res.status).toBe(200);
    expect(attendanceCallCount).toBe(2);
  });
});
```

- [ ] **Step 3: 테스트 실행해서 실패 확인**

Run: `cd senior-attendance-app/admin-web && npm test -- src/api/client.test.ts`
Expected: FAIL — `Cannot find module './client'`

- [ ] **Step 4: `client.ts` 구현**

Create `admin-web/src/api/client.ts`:

```typescript
const BASE_URL = 'http://localhost:8080';

let accessToken: string | null = null;

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export async function login(username: string, password: string): Promise<boolean> {
  const res = await fetch(`${BASE_URL}/api/auth/login`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) {
    return false;
  }
  const body = await res.json();
  accessToken = body.accessToken;
  return true;
}

export async function refreshAccessToken(): Promise<boolean> {
  const res = await fetch(`${BASE_URL}/api/auth/refresh`, {
    method: 'POST',
    credentials: 'include',
  });
  if (!res.ok) {
    accessToken = null;
    return false;
  }
  const body = await res.json();
  accessToken = body.accessToken;
  return true;
}

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const doFetch = () =>
    fetch(`${BASE_URL}${path}`, {
      ...init,
      credentials: 'include',
      headers: {
        ...init.headers,
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
    });

  let res = await doFetch();
  if (res.status === 401) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      res = await doFetch();
    }
  }
  return res;
}
```

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `cd senior-attendance-app/admin-web && npm test -- src/api/client.test.ts`
Expected: 모든 테스트 PASS

- [ ] **Step 6: Commit**

```bash
cd senior-attendance-app
git add admin-web/src/api/client.ts admin-web/src/api/client.test.ts admin-web/src/test/support/fakeFetch.ts
git commit -m "feat(admin-web): add API client with 401-retry and fake fetch test helper"
```

---

### Task 7: AuthContext + LoginPage

**Files:**
- Create: `admin-web/src/features/auth/AuthContext.tsx`
- Create: `admin-web/src/features/auth/LoginPage.tsx`
- Test: `admin-web/src/features/auth/LoginPage.test.tsx`

**Interfaces:**
- Consumes: `login`, `refreshAccessToken` from `../../api/client` (Task 6)
- Produces: `AuthProvider`, `useAuth(): { isLoggedIn: boolean; isLoading: boolean; login: (username: string, password: string) => Promise<boolean> }`, `LoginPage` 컴포넌트 (Task 8의 `App.tsx`가 사용)

- [ ] **Step 1: `AuthContext` 작성**

Create `admin-web/src/features/auth/AuthContext.tsx`:

```tsx
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { login as loginRequest, refreshAccessToken } from '../../api/client';

interface AuthContextValue {
  isLoggedIn: boolean;
  isLoading: boolean;
  login: (username: string, password: string) => Promise<boolean>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    refreshAccessToken().then((ok) => {
      setIsLoggedIn(ok);
      setIsLoading(false);
    });
  }, []);

  async function login(username: string, password: string): Promise<boolean> {
    const ok = await loginRequest(username, password);
    setIsLoggedIn(ok);
    return ok;
  }

  return (
    <AuthContext.Provider value={{ isLoggedIn, isLoading, login }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
```

- [ ] **Step 2: 실패하는 `LoginPage` 테스트 작성**

Create `admin-web/src/features/auth/LoginPage.test.tsx`:

```tsx
import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { LoginPage } from './LoginPage';
import { AuthProvider } from './AuthContext';
import * as client from '../../api/client';

vi.mock('../../api/client', async () => {
  const actual = await vi.importActual<typeof client>('../../api/client');
  return {
    ...actual,
    refreshAccessToken: vi.fn().mockResolvedValue(false),
    login: vi.fn(),
  };
});

function renderLoginPage() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    </MemoryRouter>
  );
}

describe('LoginPage', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('로그인 성공 시 에러 메시지가 없다', async () => {
    vi.mocked(client.login).mockResolvedValue(true);
    renderLoginPage();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('아이디'), 'admin@example.com');
    await user.type(screen.getByLabelText('비밀번호'), '1234');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    expect(client.login).toHaveBeenCalledWith('admin@example.com', '1234');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('로그인 실패 시 에러 메시지를 보여준다', async () => {
    vi.mocked(client.login).mockResolvedValue(false);
    renderLoginPage();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('아이디'), 'wrong@example.com');
    await user.type(screen.getByLabelText('비밀번호'), 'wrong');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '아이디 또는 비밀번호가 올바르지 않습니다'
    );
  });
});
```

- [ ] **Step 3: 테스트 실행해서 실패 확인**

Run: `cd senior-attendance-app/admin-web && npm test -- src/features/auth/LoginPage.test.tsx`
Expected: FAIL — `Cannot find module './LoginPage'`

- [ ] **Step 4: `LoginPage` 구현**

Create `admin-web/src/features/auth/LoginPage.tsx`:

```tsx
import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from './AuthContext';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    const ok = await login(username, password);
    if (ok) {
      navigate('/');
    } else {
      setError('아이디 또는 비밀번호가 올바르지 않습니다');
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <h1>관리자 로그인</h1>
      <label htmlFor="username">아이디</label>
      <input id="username" value={username} onChange={(e) => setUsername(e.target.value)} />
      <label htmlFor="password">비밀번호</label>
      <input
        id="password"
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
      />
      {error && <p role="alert">{error}</p>}
      <button type="submit">로그인</button>
    </form>
  );
}
```

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `cd senior-attendance-app/admin-web && npm test -- src/features/auth/LoginPage.test.tsx`
Expected: 모든 테스트 PASS

- [ ] **Step 6: Commit**

```bash
cd senior-attendance-app
git add admin-web/src/features/auth
git commit -m "feat(admin-web): add AuthContext and LoginPage"
```

---

### Task 8: LobbyPage + UnitTypeCard + PeriodSelector

**Files:**
- Create: `admin-web/src/types/attendance.ts`
- Create: `admin-web/src/features/lobby/PeriodSelector.tsx`
- Create: `admin-web/src/features/lobby/UnitTypeCard.tsx`
- Create: `admin-web/src/features/lobby/LobbyPage.tsx`
- Test: `admin-web/src/features/lobby/LobbyPage.test.tsx`

**Interfaces:**
- Consumes: `apiFetch` from `../../api/client` (Task 6)
- Produces: `LobbyPage` 컴포넌트, `Period` / `UnitTypeAttendanceSummary` 타입 (Task 9의 `App.tsx`가 사용)

- [ ] **Step 1: 타입 정의**

Create `admin-web/src/types/attendance.ts`:

```typescript
export type Period = 'today' | 'week' | 'month';

export interface UnitTypeAttendanceSummary {
  unitType: string;
  label: string;
  attendanceRate: number;
}
```

- [ ] **Step 2: `PeriodSelector` 작성**

Create `admin-web/src/features/lobby/PeriodSelector.tsx`:

```tsx
import type { Period } from '../../types/attendance';

const OPTIONS: { value: Period; label: string }[] = [
  { value: 'today', label: '오늘' },
  { value: 'week', label: '이번주' },
  { value: 'month', label: '이번달' },
];

interface PeriodSelectorProps {
  value: Period;
  onChange: (period: Period) => void;
}

export function PeriodSelector({ value, onChange }: PeriodSelectorProps) {
  return (
    <div role="tablist" aria-label="기간 선택">
      {OPTIONS.map((option) => (
        <button
          key={option.value}
          role="tab"
          aria-selected={value === option.value}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
```

- [ ] **Step 3: `UnitTypeCard` 작성**

Create `admin-web/src/features/lobby/UnitTypeCard.tsx`:

```tsx
import type { UnitTypeAttendanceSummary } from '../../types/attendance';

export function UnitTypeCard({ summary }: { summary: UnitTypeAttendanceSummary }) {
  return (
    <div>
      <h2>{summary.label}</h2>
      <p>{summary.attendanceRate.toFixed(1)}%</p>
    </div>
  );
}
```

- [ ] **Step 4: 실패하는 `LobbyPage` 테스트 작성**

Create `admin-web/src/features/lobby/LobbyPage.test.tsx`:

```tsx
import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LobbyPage } from './LobbyPage';
import * as client from '../../api/client';

vi.mock('../../api/client', async () => {
  const actual = await vi.importActual<typeof client>('../../api/client');
  return { ...actual, apiFetch: vi.fn() };
});

function summaryResponse(rates: { unitType: string; label: string; attendanceRate: number }[]) {
  return new Response(JSON.stringify(rates), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('LobbyPage', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('마운트 시 오늘 기준으로 조회해서 사업단 유형별 카드를 보여준다', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue(
      summaryResponse([
        { unitType: 'PUBLIC_INTEREST', label: '공익형', attendanceRate: 87.3 },
        { unitType: 'MARKET', label: '시장형', attendanceRate: 92.0 },
        { unitType: 'SOCIAL_SERVICE', label: '사회서비스형', attendanceRate: 78.5 },
      ])
    );

    render(<LobbyPage />);

    expect(await screen.findByText('87.3%')).toBeInTheDocument();
    expect(client.apiFetch).toHaveBeenCalledWith('/api/admin/attendance/summary?period=today');
  });

  it('기간 탭을 바꾸면 해당 period로 다시 조회한다', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue(
      summaryResponse([
        { unitType: 'PUBLIC_INTEREST', label: '공익형', attendanceRate: 50 },
        { unitType: 'MARKET', label: '시장형', attendanceRate: 50 },
        { unitType: 'SOCIAL_SERVICE', label: '사회서비스형', attendanceRate: 50 },
      ])
    );
    render(<LobbyPage />);
    await screen.findByText('50.0%');
    const user = userEvent.setup();

    await user.click(screen.getByRole('tab', { name: '이번주' }));

    expect(client.apiFetch).toHaveBeenLastCalledWith('/api/admin/attendance/summary?period=week');
  });

  it('API 실패 시 에러 메시지와 재시도 버튼을 보여준다', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue(new Response(null, { status: 500 }));

    render(<LobbyPage />);

    expect(await screen.findByText('출석률을 불러오지 못했습니다')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '재시도' })).toBeInTheDocument();
  });
});
```

- [ ] **Step 5: 테스트 실행해서 실패 확인**

Run: `cd senior-attendance-app/admin-web && npm test -- src/features/lobby/LobbyPage.test.tsx`
Expected: FAIL — `Cannot find module './LobbyPage'`

- [ ] **Step 6: `LobbyPage` 구현**

Create `admin-web/src/features/lobby/LobbyPage.tsx`:

```tsx
import { useCallback, useEffect, useState } from 'react';
import { apiFetch } from '../../api/client';
import type { Period, UnitTypeAttendanceSummary } from '../../types/attendance';
import { PeriodSelector } from './PeriodSelector';
import { UnitTypeCard } from './UnitTypeCard';

export function LobbyPage() {
  const [period, setPeriod] = useState<Period>('today');
  const [data, setData] = useState<UnitTypeAttendanceSummary[] | null>(null);
  const [error, setError] = useState(false);

  const load = useCallback(async () => {
    setError(false);
    try {
      const res = await apiFetch(`/api/admin/attendance/summary?period=${period}`);
      if (!res.ok) {
        setError(true);
        return;
      }
      setData(await res.json());
    } catch {
      setError(true);
    }
  }, [period]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div>
      <h1>사업단별 출석 현황</h1>
      <PeriodSelector value={period} onChange={setPeriod} />
      <button onClick={load}>새로고침</button>
      {error && (
        <div>
          <p>출석률을 불러오지 못했습니다</p>
          <button onClick={load}>재시도</button>
        </div>
      )}
      {!error && data && (
        <div>
          {data.map((summary) => (
            <UnitTypeCard key={summary.unitType} summary={summary} />
          ))}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 7: 테스트 실행해서 통과 확인**

Run: `cd senior-attendance-app/admin-web && npm test -- src/features/lobby/LobbyPage.test.tsx`
Expected: 모든 테스트 PASS

- [ ] **Step 8: Commit**

```bash
cd senior-attendance-app
git add admin-web/src/types/attendance.ts admin-web/src/features/lobby
git commit -m "feat(admin-web): add LobbyPage with period selector and unit-type cards"
```

---

### Task 9: App 라우팅 + 라우트 가드

**Files:**
- Modify: `admin-web/src/App.tsx`
- Modify: `admin-web/src/main.tsx`
- Test: `admin-web/src/App.test.tsx`

**Interfaces:**
- Consumes: `AuthProvider`, `useAuth` (Task 7), `LoginPage` (Task 7), `LobbyPage` (Task 8)
- Produces: 완성된 로그인→로비 라우팅 (Phase 1의 최종 조립 지점)

- [ ] **Step 1: 실패하는 `App` 테스트 작성**

Create `admin-web/src/App.test.tsx`:

```tsx
import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { App } from './App';
import * as client from './api/client';

vi.mock('./api/client', async () => {
  const actual = await vi.importActual<typeof client>('./api/client');
  return {
    ...actual,
    refreshAccessToken: vi.fn(),
    apiFetch: vi.fn(),
  };
});

describe('App', () => {
  afterEach(() => {
    vi.clearAllMocks();
    window.history.pushState({}, '', '/');
  });

  it('로그인 안 된 상태로 / 접근 시 로그인 화면으로 리다이렉트된다', async () => {
    vi.mocked(client.refreshAccessToken).mockResolvedValue(false);
    window.history.pushState({}, '', '/');

    render(<App />);

    expect(await screen.findByRole('heading', { name: '관리자 로그인' })).toBeInTheDocument();
  });

  it('로그인 된 상태(refresh 성공)면 / 접근 시 로비 화면이 보인다', async () => {
    vi.mocked(client.refreshAccessToken).mockResolvedValue(true);
    vi.mocked(client.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify([
          { unitType: 'PUBLIC_INTEREST', label: '공익형', attendanceRate: 80 },
          { unitType: 'MARKET', label: '시장형', attendanceRate: 90 },
          { unitType: 'SOCIAL_SERVICE', label: '사회서비스형', attendanceRate: 70 },
        ]),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    );
    window.history.pushState({}, '', '/');

    render(<App />);

    expect(await screen.findByRole('heading', { name: '사업단별 출석 현황' })).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd senior-attendance-app/admin-web && npm test -- src/App.test.tsx`
Expected: FAIL (기본 Vite 스캐폴드의 카운터 화면이 렌더링되어 원하는 heading을 찾지 못함)

- [ ] **Step 3: `App.tsx` 구현**

Replace `admin-web/src/App.tsx` 전체:

```tsx
import { type ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './features/auth/AuthContext';
import { LoginPage } from './features/auth/LoginPage';
import { LobbyPage } from './features/lobby/LobbyPage';

function RequireAuth({ children }: { children: ReactNode }) {
  const { isLoggedIn, isLoading } = useAuth();
  if (isLoading) {
    return <p>로딩 중...</p>;
  }
  if (!isLoggedIn) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

export function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/"
            element={
              <RequireAuth>
                <LobbyPage />
              </RequireAuth>
            }
          />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
```

- [ ] **Step 4: `main.tsx` 갱신**

Replace `admin-web/src/main.tsx` 전체:

```tsx
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import './index.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
);
```

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `cd senior-attendance-app/admin-web && npm test -- src/App.test.tsx`
Expected: 모든 테스트 PASS

- [ ] **Step 6: 프론트엔드 전체 회귀 확인**

Run: `cd senior-attendance-app/admin-web && npm test`
Expected: 모든 테스트 PASS (Task 6~9에서 작성한 테스트 전부 포함)

- [ ] **Step 7: Commit**

```bash
cd senior-attendance-app
git add admin-web/src/App.tsx admin-web/src/main.tsx admin-web/src/App.test.tsx
git commit -m "feat(admin-web): wire up login/lobby routing with auth guard"
```

---

### Task 10: 전체 회귀 확인

**Files:** 없음 (검증 전용 태스크)

- [ ] **Step 1: 백엔드 전체 테스트**

Run: `cd backend && ./gradlew test`
Expected: `BUILD SUCCESSFUL`, 실패 0건

- [ ] **Step 2: 프론트엔드 전체 테스트**

Run: `cd admin-web && npm test`
Expected: 모든 테스트 PASS, 실패 0건

- [ ] **Step 3: 프론트엔드 빌드 확인**

Run: `cd admin-web && npm run build`
Expected: 성공, `dist/` 생성

- [ ] **Step 4: 수동 스모크 테스트 (선택, 로컬 개발 환경에서)**

```bash
# 터미널 1
cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'
# 터미널 2
cd admin-web && npm run dev
```
브라우저에서 `http://localhost:5173` 접속 → `admin@example.com`/`1234`로 로그인 → 사업단별 출석률 카드 3개(0%로 표시될 수 있음, 데이터 없으면 정상) 확인 → 오늘/이번주/이번달 탭 전환 확인.

- [ ] **Step 5: 완료 보고**

두 스위트 모두 통과하고 빌드가 성공하면, 이 설계 문서(`docs/superpowers/specs/2026-07-14-admin-web-dashboard-phase1-design.md`)의 범위(로그인 + 사업단별 출석률 로비)가 전부 구현·검증된 것으로 간주하고 사용자에게 완료를 보고한다. 이때 Phase 2(스케줄표 엑셀 작성/추출)를 시작할지 사용자에게 확인한다.
