# 회원 자기-서비스 결석 처리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a member decline check-in from the mobile app's checkin screen ("아니오" button) and have that actually record an `ABSENT` status server-side — closing attend-feature gap #2 (the button currently just closes the screen with no API call).

**Architecture:** Add `AttendService.decline(scheduleId, memberId)` alongside the existing `checkIn()` method, reusing the existing `Attend.markAbsent()` domain method and `SmsService.sendAbsenceNotification()`. Expose it via a new `POST /api/v1/attend/decline` mapping on the existing `AttendController` (no location-consent gate, unlike check-in). Wire the mobile `checkin_screen.dart`'s `_declineCheckIn()` to actually call this endpoint through a new `CheckinRepository.decline()` method, showing the result on the same success/result screen the check-in flow already uses.

**Tech Stack:** Spring Boot 3.5 (Java 17) backend — same conventions as the existing `AttendService`/`AttendController`. Flutter mobile — Riverpod, Dio, existing `fake_api_client.dart` test helpers.

## Global Constraints

- New endpoint: `POST /api/v1/attend/decline` — 401 unauthenticated (existing `hasRole("MEMBER")` matcher on `/api/v1/attend/**` already covers it, no `SecurityConfig` change needed).
- No location-consent check on this endpoint (unlike `check-in`) — decline doesn't need location.
- No free-text reason from the client — always uses the fixed string `"회원 자가 결석"`.
- Status transition rules (all as 200 responses, never errors, except the not-found case):
  - `SCHEDULED` → `ABSENT`, SMS sent, `success=true`, message `"결석 처리되었습니다."`
  - `PRESENT`/`LATE` (already attended) → no state change, `success=false`, message `"이미 출석 처리되었습니다."`
  - `ABSENT`/`EXCUSED` (already processed) → no state change, no SMS resent, `success=true`, message `"이미 결석 처리되었습니다."`
  - No matching Attend row → 404 `ResourceNotFoundException`
- SMS failure must never fail the decline itself (same try/catch pattern as existing `checkIn()`/`markAbsent()`).
- Scope is member self-service only — no admin-facing changes in this plan.
- All existing backend and mobile tests must keep passing at the end.

---

### Task 1: Backend — AttendService.decline() + DTOs

**Files:**
- Create: `backend/src/main/java/com/example/attempt/dto/attend/AttendDeclineApiRequest.java`
- Create: `backend/src/main/java/com/example/attempt/dto/attend/AttendDeclineResponse.java`
- Modify: `backend/src/main/java/com/example/attempt/service/AttendService.java`
- Modify: `backend/src/test/java/com/example/attempt/service/AttendServiceTest.java`

**Interfaces:**
- Consumes: `AttendRepository.findByScheduleIdAndMemberId(Long, Long): Optional<Attend>` (existing), `Attend.isAttended()`/`isAbsent()`/`markAbsent(String)` (existing domain methods), `SmsService.sendAbsenceNotification(Attend, String)` (existing).
- Produces: `AttendService.decline(Long scheduleId, Long memberId): AttendDeclineResponse` — used by Task 2's `AttendController`. `AttendDeclineApiRequest`/`AttendDeclineResponse` field names/types are used verbatim by Task 2.

- [ ] **Step 1: Write the failing tests**

Add these test methods to the existing `backend/src/test/java/com/example/attempt/service/AttendServiceTest.java` (insert them after the existing `checkIn_smsSendFails_stillReturnsSuccess` test, before `findTodayAttend_withScheduleToday_returnsIt` — do not reorganize the rest of the file):

```java
    @Test
    void decline_scheduled_marksAbsentAndReturnsSuccess() {
        Attend attend = Attend.builder().id(10L).status(AttendStatus.SCHEDULED).build();
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.of(attend));

        AttendDeclineResponse response = service.decline(1L, 100L);

        assertTrue(response.isSuccess());
        assertEquals("결석 처리되었습니다.", response.getMessage());
        assertEquals(AttendStatus.ABSENT, response.getStatus());
        assertEquals(AttendStatus.ABSENT, attend.getStatus());
        verify(attendRepository).save(attend);
        verify(smsService).sendAbsenceNotification(eq(attend), any());
    }

    @Test
    void decline_alreadyAttended_returnsUnsuccessfulWithoutChangingState() {
        Attend attend = Attend.builder().id(10L).status(AttendStatus.PRESENT).build();
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.of(attend));

        AttendDeclineResponse response = service.decline(1L, 100L);

        assertFalse(response.isSuccess());
        assertEquals("이미 출석 처리되었습니다.", response.getMessage());
        assertEquals(AttendStatus.PRESENT, response.getStatus());
        verify(attendRepository, never()).save(any());
        verify(smsService, never()).sendAbsenceNotification(any(), any());
    }

    @Test
    void decline_alreadyAbsent_idempotentNoDuplicateSms() {
        Attend attend = Attend.builder().id(10L).status(AttendStatus.ABSENT).build();
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.of(attend));

        AttendDeclineResponse response = service.decline(1L, 100L);

        assertTrue(response.isSuccess());
        assertEquals("이미 결석 처리되었습니다.", response.getMessage());
        assertEquals(AttendStatus.ABSENT, response.getStatus());
        verify(attendRepository, never()).save(any());
        verify(smsService, never()).sendAbsenceNotification(any(), any());
    }

    @Test
    void decline_alreadyExcused_idempotentNoStateChange() {
        Attend attend = Attend.builder().id(10L).status(AttendStatus.EXCUSED).build();
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.of(attend));

        AttendDeclineResponse response = service.decline(1L, 100L);

        assertTrue(response.isSuccess());
        assertEquals(AttendStatus.EXCUSED, response.getStatus());
        verify(attendRepository, never()).save(any());
    }

    @Test
    void decline_noAttendRecord_throwsResourceNotFound() {
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.decline(1L, 100L));
    }

    @Test
    void decline_smsSendFails_stillReturnsSuccess() {
        Attend attend = Attend.builder().id(10L).status(AttendStatus.SCHEDULED).build();
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.of(attend));
        doThrow(new RuntimeException("SMS 전송 실패")).when(smsService).sendAbsenceNotification(any(), any());

        AttendDeclineResponse response = service.decline(1L, 100L);

        assertTrue(response.isSuccess());
    }
```

Also add this import alongside the existing `com.example.attempt.dto.attend.*` imports at the top of the file:

```java
import com.example.attempt.dto.attend.AttendDeclineResponse;
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.service.AttendServiceTest"`
Expected: FAIL — compilation error, `AttendDeclineResponse` and `AttendService.decline(...)` do not exist yet.

- [ ] **Step 3: Write minimal implementation**

Create `backend/src/main/java/com/example/attempt/dto/attend/AttendDeclineApiRequest.java`:

```java
package com.example.attempt.dto.attend;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 클라이언트가 보내는 결석 처리 요청 — memberId는 포함하지 않는다 (JWT에서 서버가 조회)
 */
@Data
public class AttendDeclineApiRequest {
    @NotNull(message = "일정 ID는 필수입니다.")
    private Long scheduleId;
}
```

Create `backend/src/main/java/com/example/attempt/dto/attend/AttendDeclineResponse.java`:

```java
package com.example.attempt.dto.attend;

import com.example.attempt.domain.AttendStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 결석 처리 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendDeclineResponse {

    /**
     * 출석 ID
     */
    private Long attendId;

    /**
     * 출석 상태
     */
    private AttendStatus status;

    /**
     * 응답 메시지
     */
    private String message;

    /**
     * 성공 여부
     */
    private boolean success;
}
```

Modify `backend/src/main/java/com/example/attempt/service/AttendService.java` — add this import near the existing `com.example.attempt.dto.attend.*` imports:

```java
import com.example.attempt.dto.attend.AttendDeclineResponse;
```

Add this method to the class (place it right after `checkIn(...)`, before `findTodayAttend(...)`):

```java
    /**
     * 결석 처리 (회원 자기-서비스) — 위치 검증 없이 즉시 결석 처리한다.
     */
    public AttendDeclineResponse decline(Long scheduleId, Long memberId) {
        log.info("결석 처리 시도: scheduleId={}, memberId={}", scheduleId, memberId);

        Attend attend = attendRepository.findByScheduleIdAndMemberId(scheduleId, memberId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "해당 일정의 출석 정보를 찾을 수 없습니다. scheduleId=" + scheduleId +
                        ", memberId=" + memberId));

        if (attend.isAttended()) {
            log.warn("이미 출석 처리된 기록에 결석 시도: attendId={}, status={}", attend.getId(), attend.getStatus());
            return AttendDeclineResponse.builder()
                    .attendId(attend.getId())
                    .status(attend.getStatus())
                    .message("이미 출석 처리되었습니다.")
                    .success(false)
                    .build();
        }

        if (attend.isAbsent()) {
            log.info("이미 결석 처리된 기록: attendId={}, status={}", attend.getId(), attend.getStatus());
            return AttendDeclineResponse.builder()
                    .attendId(attend.getId())
                    .status(attend.getStatus())
                    .message("이미 결석 처리되었습니다.")
                    .success(true)
                    .build();
        }

        attend.markAbsent("회원 자가 결석");
        attendRepository.save(attend);

        try {
            smsService.sendAbsenceNotification(attend, "회원 자가 결석");
        } catch (Exception e) {
            log.error("결석 SMS 전송 실패: attendId={}", attend.getId(), e);
        }

        log.info("결석 처리 완료: attendId={}", attend.getId());

        return AttendDeclineResponse.builder()
                .attendId(attend.getId())
                .status(attend.getStatus())
                .message("결석 처리되었습니다.")
                .success(true)
                .build();
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.service.AttendServiceTest"`
Expected: PASS — 15 tests completed (9 existing + 6 new), 0 failed.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/attempt/dto/attend/AttendDeclineApiRequest.java \
        backend/src/main/java/com/example/attempt/dto/attend/AttendDeclineResponse.java \
        backend/src/main/java/com/example/attempt/service/AttendService.java \
        backend/src/test/java/com/example/attempt/service/AttendServiceTest.java
git commit -m "feat(attend): add AttendService.decline() for member self-service absence"
```

---

### Task 2: Backend — POST /api/v1/attend/decline endpoint

**Files:**
- Modify: `backend/src/main/java/com/example/attempt/controller/AttendController.java`
- Modify: `backend/src/test/java/com/example/attempt/controller/AttendControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `AttendService.decline(Long, Long): AttendDeclineResponse` and `AttendDeclineApiRequest` (both from Task 1, exact signatures as defined there), `CurrentMemberService.getCurrentMember(): Member` (existing).
- Produces: `POST /api/v1/attend/decline` HTTP endpoint — no further backend tasks depend on this.

- [ ] **Step 1: Write the failing tests**

Add these test methods to the existing `backend/src/test/java/com/example/attempt/controller/AttendControllerIntegrationTest.java` (insert after the existing `today_withScheduledAttendToday_returnsScheduleInfo` test, at the end of the class before the closing brace):

```java
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
```

Note: `Attend`, `AttendStatus`, `Member`, `Place`, `Schedule`, `LocalDate`, `LocalTime` are already imported in this file (used by the existing `today_withScheduledAttendToday_returnsScheduleInfo` test) — no new imports needed beyond what's already there.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.controller.AttendControllerIntegrationTest"`
Expected: FAIL — 404 on all new requests (no `/api/v1/attend/decline` mapping exists yet), confirming the endpoint doesn't exist.

- [ ] **Step 3: Write minimal implementation**

Modify `backend/src/main/java/com/example/attempt/controller/AttendController.java` — add these imports alongside the existing `com.example.attempt.dto.attend.*` imports:

```java
import com.example.attempt.dto.attend.AttendDeclineApiRequest;
import com.example.attempt.dto.attend.AttendDeclineResponse;
```

Add this method to the class (place it after `checkIn(...)`, before `today()`):

```java
    @PostMapping("/decline")
    public AttendDeclineResponse decline(@Valid @RequestBody AttendDeclineApiRequest request) {
        Member member = currentMemberService.getCurrentMember();
        return attendService.decline(request.getScheduleId(), member.getId());
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.controller.AttendControllerIntegrationTest"`
Expected: PASS — 9 tests completed (5 existing + 4 new), 0 failed.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/attempt/controller/AttendController.java \
        backend/src/test/java/com/example/attempt/controller/AttendControllerIntegrationTest.java
git commit -m "feat(attend): add POST /api/v1/attend/decline endpoint"
```

---

### Task 3: Mobile — CheckinRepository.decline()

**Files:**
- Modify: `mobile/lib/features/checkin/checkin_repository.dart`
- Modify: `mobile/test/features/checkin/checkin_repository_test.dart`

**Interfaces:**
- Consumes: existing `Dio` instance held by `CheckinRepository` (constructor already takes `dio`), existing `CheckinResult` class (`success: bool`, `message: String`).
- Produces: `CheckinRepository.decline({required int scheduleId}): Future<CheckinResult>` — used by Task 4's `checkin_screen.dart`.

- [ ] **Step 1: Write the failing tests**

Add these test cases to the existing `mobile/test/features/checkin/checkin_repository_test.dart`, inside the `main()` function, after the existing `checkIn surfaces the server error message on 409` test:

```dart
  test('decline parses success result from response body', () async {
    final dio = Dio();
    dio.httpClientAdapter = _FakeAdapter(
      '{"success":true,"message":"결석 처리되었습니다."}',
    );
    final repository = CheckinRepository(dio: dio);

    final result = await repository.decline(scheduleId: 1);

    expect(result.success, isTrue);
    expect(result.message, '결석 처리되었습니다.');
  });

  test('decline surfaces already-attended message with success false', () async {
    final dio = Dio();
    dio.httpClientAdapter = _FakeAdapter(
      '{"success":false,"message":"이미 출석 처리되었습니다."}',
    );
    final repository = CheckinRepository(dio: dio);

    final result = await repository.decline(scheduleId: 1);

    expect(result.success, isFalse);
    expect(result.message, '이미 출석 처리되었습니다.');
  });

  test('decline surfaces the server error message on failure status', () async {
    final dio = Dio();
    dio.httpClientAdapter = _FakeAdapter(
      '{"message":"해당 일정의 출석 정보를 찾을 수 없습니다."}',
      statusCode: 404,
    );
    final repository = CheckinRepository(dio: dio);

    final result = await repository.decline(scheduleId: 1);

    expect(result.success, isFalse);
    expect(result.message, contains('찾을 수 없습니다'));
  });
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd mobile && flutter test test/features/checkin/checkin_repository_test.dart`
Expected: FAIL — compilation error, `CheckinRepository.decline` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

Modify `mobile/lib/features/checkin/checkin_repository.dart` — add this method to the `CheckinRepository` class, after the existing `checkIn(...)` method:

```dart
  Future<CheckinResult> decline({required int scheduleId}) async {
    try {
      final response = await dio.post('/api/v1/attend/decline', data: {
        'scheduleId': scheduleId,
      });
      return CheckinResult(
        success: response.data['success'] as bool? ?? true,
        message: response.data['message'] as String? ?? '결석 처리되었습니다.',
      );
    } on DioException catch (e) {
      final message = e.response?.data is Map
          ? (e.response?.data['message'] as String? ?? '결석 처리에 실패했습니다.')
          : '결석 처리에 실패했습니다.';
      return CheckinResult(success: false, message: message);
    }
  }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd mobile && flutter test test/features/checkin/checkin_repository_test.dart`
Expected: PASS — 7 tests completed (4 existing + 3 new), 0 failed.

- [ ] **Step 5: Commit**

```bash
git add mobile/lib/features/checkin/checkin_repository.dart \
        mobile/test/features/checkin/checkin_repository_test.dart
git commit -m "feat(mobile): add CheckinRepository.decline()"
```

---

### Task 4: Mobile — wire up the "아니오" button

**Files:**
- Modify: `mobile/lib/features/checkin/checkin_screen.dart`
- Modify: `mobile/test/features/checkin/checkin_screen_test.dart`

**Interfaces:**
- Consumes: `CheckinRepository.decline({required int scheduleId}): Future<CheckinResult>` (from Task 3, exact signature as defined there).
- Produces: user-visible behavior only — no further tasks depend on this (terminal task before final regression).

- [ ] **Step 1: Write the failing tests**

Add these test cases to the existing `mobile/test/features/checkin/checkin_screen_test.dart`, inside `main()`, after the existing `'위치 확인과 체크인이 성공하면 결과 화면에 서버 응답 메시지를 보여준다'` test:

```dart
  testWidgets('아니오를 누르면 결석 처리 API를 호출하고 결과 화면에 메시지를 보여준다', (tester) async {
    bool declineCalled = false;

    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          if (options.path == '/api/v1/attend/today') {
            return jsonResponse('{"hasSchedule":true,"scheduleId":1,"placeName":"중앙공원"}');
          }
          if (options.path == '/api/v1/attend/decline') {
            declineCalled = true;
            return jsonResponse('{"success":true,"message":"결석 처리되었습니다."}');
          }
          return jsonResponse('{}');
        })),
      ],
      child: const MaterialApp(home: CheckinScreen()),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(ElevatedButton, '아니오'));
    await tester.pumpAndSettle();

    expect(declineCalled, isTrue);
    expect(find.text('결석 처리되었습니다.'), findsOneWidget);
    expect(find.widgetWithText(ElevatedButton, '확인'), findsOneWidget);
  });

  testWidgets('결석 처리 API가 실패 응답을 반환하면 결과 화면에 서버 메시지를 보여준다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          if (options.path == '/api/v1/attend/today') {
            return jsonResponse('{"hasSchedule":true,"scheduleId":1,"placeName":"중앙공원"}');
          }
          if (options.path == '/api/v1/attend/decline') {
            return jsonResponse('{"message":"서버 오류"}', statusCode: 500);
          }
          return jsonResponse('{}');
        })),
      ],
      child: const MaterialApp(home: CheckinScreen()),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(ElevatedButton, '아니오'));
    await tester.pumpAndSettle();

    // CheckinRepository.decline() already catches DioException internally and
    // returns a CheckinResult(success: false, message: <server message>) — it
    // never rethrows. So a 500 here surfaces through the same `_result` success
    // screen as a normal decline, showing the server's message, not through
    // `_errorMessage`. Only a non-Dio exception (not exercised by this fake
    // adapter setup) would reach `_declineCheckIn()`'s own catch block.
    expect(find.text('서버 오류'), findsOneWidget);
    expect(find.widgetWithText(ElevatedButton, '확인'), findsOneWidget);
  });
```

No import changes are needed in this test file for either new test.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd mobile && flutter test test/features/checkin/checkin_screen_test.dart`
Expected: FAIL — both new tests fail because `declineCalled` never becomes `true` and no result screen ever appears; the button currently just calls `Navigator.of(context).maybePop()` with no API call at all.

- [ ] **Step 3: Write minimal implementation**

Modify `mobile/lib/features/checkin/checkin_screen.dart`. Replace the existing state fields and `_declineCheckIn` method:

Replace:
```dart
class _CheckinScreenState extends ConsumerState<CheckinScreen> {
  bool _loadingToday = true;
  TodayAttend? _today;
  bool _checkingIn = false;
  String? _errorMessage;
  CheckinResult? _result;
```

With:
```dart
class _CheckinScreenState extends ConsumerState<CheckinScreen> {
  bool _loadingToday = true;
  TodayAttend? _today;
  bool _checkingIn = false;
  bool _declining = false;
  String? _errorMessage;
  CheckinResult? _result;
```

Replace:
```dart
  void _declineCheckIn() {
    Navigator.of(context).maybePop();
  }
```

With:
```dart
  Future<void> _declineCheckIn() async {
    final scheduleId = _today?.scheduleId;
    if (scheduleId == null || _declining) return;

    setState(() {
      _declining = true;
      _errorMessage = null;
    });
    try {
      final repo = CheckinRepository(dio: ref.read(apiClientProvider).dio);
      final result = await repo.decline(scheduleId: scheduleId);
      if (!mounted) return;
      setState(() => _result = result);
    } catch (e) {
      if (mounted) {
        setState(() => _errorMessage = '결석 처리에 실패했습니다. 다시 시도해주세요.');
      }
    } finally {
      if (mounted) setState(() => _declining = false);
    }
  }
```

Update the bottom action bar so each button disables while the other's request is in flight. Replace:
```dart
      bottomNavigationBar: AtmBottomActionBar.confirm(
        onYes: (canAct && !_checkingIn) ? _confirmCheckIn : null,
        onNo: canAct ? _declineCheckIn : null,
      ),
```

With:
```dart
      bottomNavigationBar: AtmBottomActionBar.confirm(
        onYes: (canAct && !_checkingIn && !_declining) ? _confirmCheckIn : null,
        onNo: (canAct && !_checkingIn && !_declining) ? _declineCheckIn : null,
      ),
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd mobile && flutter test test/features/checkin/checkin_screen_test.dart`
Expected: PASS — 6 tests completed (4 existing + 2 new), 0 failed.

- [ ] **Step 5: Commit**

```bash
git add mobile/lib/features/checkin/checkin_screen.dart \
        mobile/test/features/checkin/checkin_screen_test.dart
git commit -m "feat(mobile): wire up decline button to call the decline API"
```

---

### Task 5: Full regression run

**Files:** None (verification-only task).

**Interfaces:** N/A — this task only runs the full existing backend and mobile test suites to confirm no regressions from Tasks 1–4.

- [ ] **Step 1: Run the full backend test suite**

Run: `cd backend && ./gradlew test`
Expected: `BUILD SUCCESSFUL`, 0 failures.

- [ ] **Step 2: Run the full mobile test suite**

Run: `cd mobile && flutter test`
Expected: all tests pass, 0 failures.

- [ ] **Step 3: If anything fails, fix and re-run**

Do not proceed to sign-off until both suites pass with 0 failures.

- [ ] **Step 4: Commit (only if Step 3 required a fix)**

```bash
git add -A
git commit -m "fix(attend): resolve regression found in full test suite run"
```

If Steps 1–2 passed with no changes needed, skip this commit — there is nothing to commit.

---

## Plan Self-Review Notes

- **Spec coverage:** Architecture (Tasks 1–2 backend, Tasks 3–4 mobile), API contract (Task 1 DTOs), data flow / status-transition table (Task 1's `decline()` method body), error-handling table (Task 1's three branches + Task 2's 401/404 tests), mobile wiring (Task 4), testing strategy (all four tasks' test lists map directly to the spec's "테스트 전략" bullets). All five "결정 사항 요약" bullets are reflected in the Global Constraints and Task 1/4 implementations.
- **Type consistency checked:** `AttendDeclineApiRequest`/`AttendDeclineResponse` field names and types are identical across Task 1 (definition) and Task 2 (consumption). `AttendService.decline(Long, Long)` signature matches between Task 1's production code, its own test's direct calls, and Task 2's controller call (`request.getScheduleId()`, `member.getId()` — both `Long`). `CheckinRepository.decline({required int scheduleId})` signature matches between Task 3 (definition) and Task 4 (consumption).
- **No placeholders:** every step has complete, runnable code — no TBD/TODO markers.
