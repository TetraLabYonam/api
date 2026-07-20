# 일정(Schedule) 생성 + 출석(Attend) 자동 생성 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a backend-only admin API that creates `Schedule` rows (single date or recurring by weekday pattern) for a `Place`, and auto-generates the initial `Attend(SCHEDULED)` row for every member assigned to that place.

**Architecture:** New `ScheduleController` (`POST /api/admin/schedules`, already covered by the existing `/api/admin/**` → `hasRole("ADMIN")` security matcher) delegates to a new `ScheduleService` that validates the request, expands it into a list of target dates, skips dates that already have a `Schedule` for that place, and for each remaining date creates one `Schedule` plus one `Attend(SCHEDULED)` per member whose `assignedPlaceId` matches. A new `CurrentAdminService` (mirroring the existing `CurrentMemberService`) resolves the authenticated `Admin` for `Schedule.createdBy`.

**Tech Stack:** Spring Boot 3.5 (Java 17), Spring Data JPA, Lombok, JUnit 5 + Mockito (unit), `@SpringBootTest` + `TestRestTemplate` on H2 in-memory (integration) — all matching the existing `backend` module's conventions.

## Global Constraints

- New endpoint: `POST /api/admin/schedules` — must return 401 unauthenticated, 403 for `ROLE_MEMBER` tokens, 200 for `ROLE_ADMIN` tokens. No `SecurityConfig` change needed — `/api/admin/**` already requires `ROLE_ADMIN`.
- Max allowed date range for one request: 180 days by default, configurable via `@Value("${schedule.max-range-days:180}")`. Exceeding it is a 400, not silently truncated.
- `daysOfWeek` is required and non-empty whenever `endDate != startDate`; ignored when `endDate == startDate` (single-day request).
- Duplicate detection is (place, date) only — one schedule per place per day. A duplicate date is skipped, not an error for the whole request.
- All existing tests must keep passing (`./gradlew test` run at the end).
- Follow the existing package/DTO/exception conventions exactly: DTOs use `@Data @Builder @NoArgsConstructor @AllArgsConstructor`, validation failures throw `IllegalArgumentException` (→ 400 via existing `GlobalExceptionHandler`), missing entities throw `ResourceNotFoundException` (→ 404).

---

### Task 1: CurrentAdminService

**Files:**
- Create: `backend/src/main/java/com/example/attempt/service/CurrentAdminService.java`
- Test: `backend/src/test/java/com/example/attempt/service/CurrentAdminServiceTest.java`

**Interfaces:**
- Consumes: `AdminRepository.findByUsername(String): Optional<Admin>` (already exists), `SecurityContextHolder.getContext().getAuthentication().getName()` (JWT subject == `Admin.username`, confirmed via `AuthController.login()`/`JwtAuthenticationFilter`).
- Produces: `CurrentAdminService.getCurrentAdmin(): Admin` — used by Task 3's `ScheduleController`.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/example/attempt/service/CurrentAdminServiceTest.java`:

```java
package com.example.attempt.service;

import com.example.attempt.domain.Admin;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.AdminRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentAdminServiceTest {

    private AdminRepository adminRepository;
    private CurrentAdminService service;

    @BeforeEach
    void setup() {
        adminRepository = mock(AdminRepository.class);
        service = new CurrentAdminService(adminRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentAdmin_looksUpByAuthenticatedUsername() {
        Admin admin = new Admin("admin@example.com", "hashed");
        when(adminRepository.findByUsername("admin@example.com")).thenReturn(Optional.of(admin));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@example.com", null, List.of()));

        Admin result = service.getCurrentAdmin();

        assertEquals("admin@example.com", result.getUsername());
    }

    @Test
    void getCurrentAdmin_throws_whenNoAdminForUsername() {
        when(adminRepository.findByUsername("ghost@example.com")).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ghost@example.com", null, List.of()));

        assertThrows(ResourceNotFoundException.class, () -> service.getCurrentAdmin());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.service.CurrentAdminServiceTest"`
Expected: FAIL — compilation error, `CurrentAdminService` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `backend/src/main/java/com/example/attempt/service/CurrentAdminService.java`:

```java
package com.example.attempt.service;

import com.example.attempt.domain.Admin;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * JWT 인증 주체(관리자 username)로부터 현재 로그인한 Admin을 조회한다.
 * CurrentMemberService와 동일한 패턴 — 컨트롤러가 요청 바디의 adminId를 신뢰하지 않도록 한다.
 */
@Service
@RequiredArgsConstructor
public class CurrentAdminService {

    private final AdminRepository adminRepository;

    public Admin getCurrentAdmin() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return adminRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("인증된 관리자를 찾을 수 없습니다. username=" + username));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.service.CurrentAdminServiceTest"`
Expected: PASS — 2 tests completed, 0 failed.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/attempt/service/CurrentAdminService.java \
        backend/src/test/java/com/example/attempt/service/CurrentAdminServiceTest.java
git commit -m "feat(admin): add CurrentAdminService to resolve authenticated Admin from JWT"
```

---

### Task 2: ScheduleService (core logic — DTOs, repository query, date expansion, dedup, fan-out)

**Files:**
- Create: `backend/src/main/java/com/example/attempt/dto/schedule/CreateScheduleRequest.java`
- Create: `backend/src/main/java/com/example/attempt/dto/schedule/CreateScheduleResponse.java`
- Modify: `backend/src/main/java/com/example/attempt/repository/ScheduleRepository.java`
- Create: `backend/src/main/java/com/example/attempt/service/ScheduleService.java`
- Test: `backend/src/test/java/com/example/attempt/service/ScheduleServiceTest.java`

**Interfaces:**
- Consumes: `MemberRepository.findByAssignedPlaceId(Long): List<Member>` (already exists), `PlaceRepository.findById(Long): Optional<Place>` (already exists, `JpaRepository`), `AttendRepository.save(Attend)` (already exists, `JpaRepository`), `Schedule.builder()...build()` (existing domain builder, fields: `title, description, scheduleDate, startTime, endTime, place, createdBy`).
- Produces: `ScheduleService.createSchedules(CreateScheduleRequest, Admin): CreateScheduleResponse` — used by Task 3's `ScheduleController`. `CreateScheduleRequest`/`CreateScheduleResponse` field names/types are used verbatim by Task 3.

- [ ] **Step 1: Write the failing tests**

Create `backend/src/test/java/com/example/attempt/service/ScheduleServiceTest.java`:

```java
package com.example.attempt.service;

import com.example.attempt.domain.Admin;
import com.example.attempt.domain.Attend;
import com.example.attempt.domain.Member;
import com.example.attempt.domain.Place;
import com.example.attempt.domain.Schedule;
import com.example.attempt.dto.schedule.CreateScheduleRequest;
import com.example.attempt.dto.schedule.CreateScheduleResponse;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.AttendRepository;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.repository.PlaceRepository;
import com.example.attempt.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ScheduleServiceTest {

    private ScheduleRepository scheduleRepository;
    private PlaceRepository placeRepository;
    private MemberRepository memberRepository;
    private AttendRepository attendRepository;
    private ScheduleService service;

    private final Place place = new Place("중앙공원", "주소", 35.3, 129.0);
    private final Admin admin = new Admin("admin@example.com", "hashed");

    @BeforeEach
    void setup() {
        scheduleRepository = mock(ScheduleRepository.class);
        placeRepository = mock(PlaceRepository.class);
        memberRepository = mock(MemberRepository.class);
        attendRepository = mock(AttendRepository.class);
        service = new ScheduleService(scheduleRepository, placeRepository, memberRepository, attendRepository, 180);

        place.setId(1L);
        when(placeRepository.findById(1L)).thenReturn(Optional.of(place));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private CreateScheduleRequest.CreateScheduleRequestBuilder baseRequest() {
        return CreateScheduleRequest.builder()
                .placeId(1L)
                .title("오전 근무")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(13, 0));
    }

    @Test
    void singleDay_ignoresDaysOfWeek_createsOneScheduleAndAttendsForAssignedMembers() {
        Member m1 = new Member("김할매", "01070001111");
        Member m2 = new Member("이할배", "01070001112");
        when(memberRepository.findByAssignedPlaceId(1L)).thenReturn(List.of(m1, m2));
        when(scheduleRepository.existsByPlaceIdAndScheduleDate(eq(1L), any())).thenReturn(false);

        CreateScheduleRequest request = baseRequest()
                .startDate(LocalDate.of(2026, 7, 13))
                .daysOfWeek(Set.of(DayOfWeek.FRIDAY)) // 무시되어야 함 (단건)
                .build();

        CreateScheduleResponse response = service.createSchedules(request, admin);

        assertEquals(List.of(LocalDate.of(2026, 7, 13)), response.getCreatedDates());
        assertEquals(List.of(), response.getSkippedDates());
        assertEquals(2, response.getAttendCreatedCount());
        verify(attendRepository, times(2)).save(any(Attend.class));
    }

    @Test
    void recurringRange_filtersByDaysOfWeek() {
        when(memberRepository.findByAssignedPlaceId(1L)).thenReturn(List.of(new Member("김할매", "01070001111")));
        when(scheduleRepository.existsByPlaceIdAndScheduleDate(eq(1L), any())).thenReturn(false);

        // 2026-07-06(월) ~ 2026-07-19(일), [월,수] -> 07-06,07-08,07-13,07-15
        CreateScheduleRequest request = baseRequest()
                .startDate(LocalDate.of(2026, 7, 6))
                .endDate(LocalDate.of(2026, 7, 19))
                .daysOfWeek(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
                .build();

        CreateScheduleResponse response = service.createSchedules(request, admin);

        assertEquals(List.of(
                LocalDate.of(2026, 7, 6),
                LocalDate.of(2026, 7, 8),
                LocalDate.of(2026, 7, 13),
                LocalDate.of(2026, 7, 15)
        ), response.getCreatedDates());
        assertEquals(4, response.getAttendCreatedCount());
    }

    @Test
    void duplicateDate_isSkipped_noAttendCreated() {
        when(memberRepository.findByAssignedPlaceId(1L)).thenReturn(List.of(new Member("김할매", "01070001111")));
        when(scheduleRepository.existsByPlaceIdAndScheduleDate(1L, LocalDate.of(2026, 7, 13))).thenReturn(true);
        when(scheduleRepository.existsByPlaceIdAndScheduleDate(1L, LocalDate.of(2026, 7, 6))).thenReturn(false);
        when(scheduleRepository.existsByPlaceIdAndScheduleDate(1L, LocalDate.of(2026, 7, 8))).thenReturn(false);
        when(scheduleRepository.existsByPlaceIdAndScheduleDate(1L, LocalDate.of(2026, 7, 15))).thenReturn(false);

        CreateScheduleRequest request = baseRequest()
                .startDate(LocalDate.of(2026, 7, 6))
                .endDate(LocalDate.of(2026, 7, 19))
                .daysOfWeek(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
                .build();

        CreateScheduleResponse response = service.createSchedules(request, admin);

        assertEquals(List.of(
                LocalDate.of(2026, 7, 6),
                LocalDate.of(2026, 7, 8),
                LocalDate.of(2026, 7, 15)
        ), response.getCreatedDates());
        assertEquals(List.of(LocalDate.of(2026, 7, 13)), response.getSkippedDates());
        assertEquals(3, response.getAttendCreatedCount());
    }

    @Test
    void zeroAssignedMembers_scheduleCreatedButZeroAttend() {
        when(memberRepository.findByAssignedPlaceId(1L)).thenReturn(List.of());
        when(scheduleRepository.existsByPlaceIdAndScheduleDate(eq(1L), any())).thenReturn(false);

        CreateScheduleRequest request = baseRequest().startDate(LocalDate.of(2026, 7, 13)).build();

        CreateScheduleResponse response = service.createSchedules(request, admin);

        assertEquals(List.of(LocalDate.of(2026, 7, 13)), response.getCreatedDates());
        assertEquals(0, response.getAttendCreatedCount());
        verify(scheduleRepository, times(1)).save(any(Schedule.class));
        verify(attendRepository, never()).save(any(Attend.class));
    }

    @Test
    void startDateAfterEndDate_throwsIllegalArgumentException() {
        CreateScheduleRequest request = baseRequest()
                .startDate(LocalDate.of(2026, 7, 15))
                .endDate(LocalDate.of(2026, 7, 13))
                .build();

        assertThrows(IllegalArgumentException.class, () -> service.createSchedules(request, admin));
    }

    @Test
    void startTimeNotBeforeEndTime_throwsIllegalArgumentException() {
        CreateScheduleRequest request = CreateScheduleRequest.builder()
                .placeId(1L).title("오전 근무")
                .startDate(LocalDate.of(2026, 7, 13))
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(9, 0))
                .build();

        assertThrows(IllegalArgumentException.class, () -> service.createSchedules(request, admin));
    }

    @Test
    void multiDayWithoutDaysOfWeek_throwsIllegalArgumentException() {
        CreateScheduleRequest request = baseRequest()
                .startDate(LocalDate.of(2026, 7, 6))
                .endDate(LocalDate.of(2026, 7, 19))
                .build();

        assertThrows(IllegalArgumentException.class, () -> service.createSchedules(request, admin));
    }

    @Test
    void rangeExceedsMaxRangeDays_throwsIllegalArgumentException() {
        ScheduleService strictService = new ScheduleService(
                scheduleRepository, placeRepository, memberRepository, attendRepository, 10);

        CreateScheduleRequest request = baseRequest()
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 7, 31))
                .daysOfWeek(Set.of(DayOfWeek.MONDAY))
                .build();

        assertThrows(IllegalArgumentException.class, () -> strictService.createSchedules(request, admin));
    }

    @Test
    void placeNotFound_throwsResourceNotFoundException() {
        when(placeRepository.findById(999L)).thenReturn(Optional.empty());
        CreateScheduleRequest request = CreateScheduleRequest.builder()
                .placeId(999L).title("오전 근무")
                .startDate(LocalDate.of(2026, 7, 13))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(13, 0))
                .build();

        assertThrows(ResourceNotFoundException.class, () -> service.createSchedules(request, admin));
    }
}
```

Note: `eq(...)` requires a static import — add `import static org.mockito.ArgumentMatchers.eq;` alongside the existing `any` import in the file above (already included in the import block).

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.service.ScheduleServiceTest"`
Expected: FAIL — compilation error (`ScheduleService`, `CreateScheduleRequest`, `CreateScheduleResponse`, `existsByPlaceIdAndScheduleDate` don't exist yet).

- [ ] **Step 3: Write minimal implementation**

Create `backend/src/main/java/com/example/attempt/dto/schedule/CreateScheduleRequest.java`:

```java
package com.example.attempt.dto.schedule;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * 일정 생성 요청 DTO — 단건(startDate==endDate) 또는 반복(daysOfWeek 지정) 생성을 모두 표현한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateScheduleRequest {

    @NotNull(message = "장소 ID는 필수입니다.")
    private Long placeId;

    @NotNull(message = "제목은 필수입니다.")
    private String title;

    private String description;

    @NotNull(message = "시작 날짜는 필수입니다.")
    private LocalDate startDate;

    private LocalDate endDate;

    private Set<DayOfWeek> daysOfWeek;

    @NotNull(message = "시작 시간은 필수입니다.")
    private LocalTime startTime;

    @NotNull(message = "종료 시간은 필수입니다.")
    private LocalTime endTime;
}
```

Create `backend/src/main/java/com/example/attempt/dto/schedule/CreateScheduleResponse.java`:

```java
package com.example.attempt.dto.schedule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateScheduleResponse {
    private List<LocalDate> createdDates;
    private List<LocalDate> skippedDates;
    private int attendCreatedCount;
}
```

Modify `backend/src/main/java/com/example/attempt/repository/ScheduleRepository.java` — add one method to the existing interface:

```java
package com.example.attempt.repository;

import com.example.attempt.domain.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    /**
     * 같은 장소·같은 날짜에 이미 일정이 있는지 확인 (일정 생성 시 중복 판단용)
     */
    boolean existsByPlaceIdAndScheduleDate(Long placeId, LocalDate scheduleDate);
}
```

Create `backend/src/main/java/com/example/attempt/service/ScheduleService.java`:

```java
package com.example.attempt.service;

import com.example.attempt.domain.Admin;
import com.example.attempt.domain.Attend;
import com.example.attempt.domain.AttendStatus;
import com.example.attempt.domain.Member;
import com.example.attempt.domain.Place;
import com.example.attempt.domain.Schedule;
import com.example.attempt.dto.schedule.CreateScheduleRequest;
import com.example.attempt.dto.schedule.CreateScheduleResponse;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.AttendRepository;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.repository.PlaceRepository;
import com.example.attempt.repository.ScheduleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 일정(Schedule) 생성 + 배정 회원 전원에 대한 Attend(SCHEDULED) 자동 생성 서비스
 */
@Service
@Transactional
@Slf4j
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final PlaceRepository placeRepository;
    private final MemberRepository memberRepository;
    private final AttendRepository attendRepository;
    private final long maxRangeDays;

    public ScheduleService(ScheduleRepository scheduleRepository,
                            PlaceRepository placeRepository,
                            MemberRepository memberRepository,
                            AttendRepository attendRepository,
                            @Value("${schedule.max-range-days:180}") long maxRangeDays) {
        this.scheduleRepository = scheduleRepository;
        this.placeRepository = placeRepository;
        this.memberRepository = memberRepository;
        this.attendRepository = attendRepository;
        this.maxRangeDays = maxRangeDays;
    }

    public CreateScheduleResponse createSchedules(CreateScheduleRequest request, Admin createdBy) {
        Place place = placeRepository.findById(request.getPlaceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "장소를 찾을 수 없습니다. ID: " + request.getPlaceId()));

        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate() != null ? request.getEndDate() : startDate;

        validate(request, startDate, endDate);

        List<LocalDate> targetDates = resolveTargetDates(startDate, endDate, request.getDaysOfWeek());
        List<Member> members = memberRepository.findByAssignedPlaceId(place.getId());

        List<LocalDate> createdDates = new ArrayList<>();
        List<LocalDate> skippedDates = new ArrayList<>();
        int attendCreatedCount = 0;

        for (LocalDate date : targetDates) {
            if (scheduleRepository.existsByPlaceIdAndScheduleDate(place.getId(), date)) {
                skippedDates.add(date);
                continue;
            }

            Schedule schedule = scheduleRepository.save(Schedule.builder()
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .scheduleDate(date)
                    .startTime(request.getStartTime())
                    .endTime(request.getEndTime())
                    .place(place)
                    .createdBy(createdBy)
                    .build());

            for (Member member : members) {
                attendRepository.save(Attend.builder()
                        .member(member)
                        .schedule(schedule)
                        .status(AttendStatus.SCHEDULED)
                        .build());
            }

            createdDates.add(date);
            attendCreatedCount += members.size();
        }

        log.info("일정 생성 완료: placeId={}, 생성={}건, 스킵={}건, Attend={}건",
                place.getId(), createdDates.size(), skippedDates.size(), attendCreatedCount);

        return CreateScheduleResponse.builder()
                .createdDates(createdDates)
                .skippedDates(skippedDates)
                .attendCreatedCount(attendCreatedCount)
                .build();
    }

    private void validate(CreateScheduleRequest request, LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작 날짜는 종료 날짜보다 이후일 수 없습니다.");
        }
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new IllegalArgumentException("시작 시간은 종료 시간보다 이전이어야 합니다.");
        }
        boolean isMultiDay = !startDate.isEqual(endDate);
        if (isMultiDay && (request.getDaysOfWeek() == null || request.getDaysOfWeek().isEmpty())) {
            throw new IllegalArgumentException("시작일과 종료일이 다르면 daysOfWeek를 지정해야 합니다.");
        }
        long rangeDays = ChronoUnit.DAYS.between(startDate, endDate);
        if (rangeDays > maxRangeDays) {
            throw new IllegalArgumentException(
                    "생성 기간이 최대 허용치(" + maxRangeDays + "일)를 초과했습니다.");
        }
    }

    private List<LocalDate> resolveTargetDates(LocalDate startDate, LocalDate endDate, Set<DayOfWeek> daysOfWeek) {
        if (startDate.isEqual(endDate)) {
            return List.of(startDate);
        }

        List<LocalDate> dates = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            if (daysOfWeek.contains(current.getDayOfWeek())) {
                dates.add(current);
            }
            current = current.plusDays(1);
        }
        return dates;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.service.ScheduleServiceTest"`
Expected: PASS — 9 tests completed, 0 failed.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/attempt/dto/schedule/ \
        backend/src/main/java/com/example/attempt/repository/ScheduleRepository.java \
        backend/src/main/java/com/example/attempt/service/ScheduleService.java \
        backend/src/test/java/com/example/attempt/service/ScheduleServiceTest.java
git commit -m "feat(admin): add ScheduleService to create schedules and auto-generate Attend rows"
```

---

### Task 3: ScheduleController (HTTP layer + integration tests)

**Files:**
- Create: `backend/src/main/java/com/example/attempt/controller/ScheduleController.java`
- Test: `backend/src/test/java/com/example/attempt/controller/ScheduleControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `ScheduleService.createSchedules(CreateScheduleRequest, Admin): CreateScheduleResponse` and `CurrentAdminService.getCurrentAdmin(): Admin` (both from Tasks 1–2, exact signatures as defined there).
- Produces: `POST /api/admin/schedules` HTTP endpoint — no further tasks depend on this (terminal task before final regression).

- [ ] **Step 1: Write the failing tests**

Create `backend/src/test/java/com/example/attempt/controller/ScheduleControllerIntegrationTest.java`:

```java
package com.example.attempt.controller;

import com.example.attempt.domain.Admin;
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
        assertEquals("admin@example.com", schedules.get(0).getCreatedBy().getUsername());
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.controller.ScheduleControllerIntegrationTest"`
Expected: FAIL — 404 on all requests (no `/api/admin/schedules` mapping exists yet), or compilation error if `ScheduleController` is referenced elsewhere. Confirms the endpoint doesn't exist yet.

- [ ] **Step 3: Write minimal implementation**

Create `backend/src/main/java/com/example/attempt/controller/ScheduleController.java`:

```java
package com.example.attempt.controller;

import com.example.attempt.domain.Admin;
import com.example.attempt.dto.schedule.CreateScheduleRequest;
import com.example.attempt.dto.schedule.CreateScheduleResponse;
import com.example.attempt.service.CurrentAdminService;
import com.example.attempt.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final CurrentAdminService currentAdminService;

    @PostMapping
    public CreateScheduleResponse create(@Valid @RequestBody CreateScheduleRequest request) {
        Admin admin = currentAdminService.getCurrentAdmin();
        return scheduleService.createSchedules(request, admin);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.controller.ScheduleControllerIntegrationTest"`
Expected: PASS — 6 tests completed, 0 failed.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/attempt/controller/ScheduleController.java \
        backend/src/test/java/com/example/attempt/controller/ScheduleControllerIntegrationTest.java
git commit -m "feat(admin): add POST /api/admin/schedules endpoint"
```

---

### Task 4: Full regression run

**Files:** None (verification-only task).

**Interfaces:** N/A — this task only runs the full existing test suite to confirm no regressions from Tasks 1–3.

- [ ] **Step 1: Run the full backend test suite**

Run: `cd backend && ./gradlew test`
Expected: `BUILD SUCCESSFUL`, 0 failures (existing test count + 2 + 9 + 6 new tests from Tasks 1–3 = existing total + 17).

- [ ] **Step 2: If anything fails, fix and re-run**

Do not proceed to sign-off until `BUILD SUCCESSFUL` with 0 failures.

- [ ] **Step 3: Commit (only if Step 2 required a fix)**

```bash
git add -A
git commit -m "fix(admin): resolve regression found in full test suite run"
```

If Step 1 passed with no changes needed, skip this commit — there is nothing to commit.

---

## Plan Self-Review Notes

- **Spec coverage:** Architecture (Task 1 + 2 + 3), API contract fields (Task 2 DTOs), data flow steps 1–6 (Task 2 `ScheduleService.createSchedules`), error handling table (Task 2 `validate` + `ResourceNotFoundException`, Task 3 security matcher reuse), testing strategy (Tasks 2–3 test lists match the spec's bullet points almost 1:1). All five "결정 사항" bullets are reflected in the global constraints and Task 2 implementation.
- **Type consistency checked:** `CreateScheduleRequest`/`CreateScheduleResponse` field names and types are identical across Task 2 (definition) and Task 3 (consumption). `ScheduleService` constructor signature (`ScheduleRepository, PlaceRepository, MemberRepository, AttendRepository, long maxRangeDays`) matches between Task 2's production code and its own test's direct instantiation. `CurrentAdminService.getCurrentAdmin()` signature matches between Task 1 (definition) and Task 3 (consumption).
- **No placeholders:** every step has complete, runnable code — no TBD/TODO markers.
