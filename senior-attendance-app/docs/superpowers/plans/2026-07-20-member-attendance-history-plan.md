# 회원 본인 출석 이력 조회 — 구현 계획 (짧은 형식)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** 회원이 체크인 화면에서 이번 달 출석률+날짜별 이력을 볼 수 있게 한다.

**참고 스펙:** `docs/superpowers/specs/2026-07-20-member-attendance-history-design.md`

## Global Constraints

- 신규 엔드포인트는 기존 `/api/v1/attend/**` → `hasRole("MEMBER")` 매처가 자동 적용, `SecurityConfig` 변경 불필요.
- 새 리포지토리 쿼리 없음 — 기존 `getAttendanceStatsByMemberId`/`findByMemberIdAndDateRange` 재사용.
- 출석률 계산은 `AdminAttendanceService.getSummary()`와 동일 정의 ((PRESENT+LATE)/전체*100, 0건이면 0%).

---

### Task 1: 백엔드 — `GET /api/v1/attend/history`

**파일:**
- Create: `backend/src/main/java/com/example/attempt/dto/attend/AttendHistoryResponse.java` — `{ attendanceRate: double, records: List<AttendHistoryItem> }`
- Create: `backend/src/main/java/com/example/attempt/dto/attend/AttendHistoryItem.java` — `{ scheduleDate: LocalDate, placeName: String, status: AttendStatus }` (패턴: `dto/admin/AttendeeItem.java`와 동일한 `@Data @NoArgsConstructor @AllArgsConstructor` 스타일)
- Modify: `backend/src/main/java/com/example/attempt/service/AttendService.java` — `getHistory(Long memberId): AttendHistoryResponse` 추가. 이번 달 범위(`LocalDate.now().withDayOfMonth(1)` ~ `LocalDate.now()`)로 `getAttendanceStatsByMemberId`(비율 계산, 패턴: `AdminAttendanceService.getSummary()`의 집계 로직) + `findByMemberIdAndDateRange`(목록, `schedule.getPlace().getName()`/`schedule.getScheduleDate()`/`status`로 매핑) 호출.
- Modify: `backend/src/main/java/com/example/attempt/controller/AttendController.java` — `@GetMapping("/history")` 추가, `currentMemberService.getCurrentMember()`로 memberId 해석 후 위임.
- Test: `backend/src/test/java/com/example/attempt/service/AttendServiceTest.java` (기존 파일에 추가) + `backend/src/test/java/com/example/attempt/controller/AttendControllerIntegrationTest.java` (기존 파일에 추가)

**테스트:**
- 서비스: 출석률 계산 정확성(PRESENT+LATE 포함, ABSENT 제외), 0건일 때 0%+빈 목록, 날짜별 목록 매핑 정확성
- 컨트롤러: 인증 없음→401, 정상 조회→200(본인 기록만, 다른 회원 기록 안 섞임 확인)

**커밋:** `feat(attend): add GET /api/v1/attend/history for member self-service history`

---

### Task 2: 모바일 — 출석 이력 화면

**파일:**
- Create: `mobile/lib/features/attendance_history/attendance_history_repository.dart` — `getHistory(): Future<AttendHistory>` (패턴: `checkin_repository.dart`의 `getTodayAttend()`)
- Create: `mobile/lib/features/attendance_history/attendance_history_screen.dart` — 출석률 + 날짜별 목록 렌더링. 0건이면 "이번 달 출석 기록이 없습니다" 표시. (패턴: `checkin_screen.dart`의 로딩/에러 상태 관리 구조)
- Modify: `mobile/lib/features/checkin/checkin_screen.dart` — AppBar에 진입 버튼 추가, `Navigator.of(context).push(MaterialPageRoute(builder: (_) => const AttendanceHistoryScreen()))` (패턴: `consent_screen.dart`의 `Navigator.push` 사용법)
- Test: `mobile/test/features/attendance_history/attendance_history_repository_test.dart`, `mobile/test/features/attendance_history/attendance_history_screen_test.dart` (패턴: `checkin_repository_test.dart`/`checkin_screen_test.dart`)

**테스트:**
- 리포지토리: 정상 응답 파싱, 빈 목록 파싱
- 화면: 출석률+목록 렌더링, 빈 목록 안내 문구, 진입 버튼 탭 시 화면 전환

**커밋:** `feat(mobile): add attendance history screen`

---

### Task 3: 전체 회귀 테스트

```
cd backend && ./gradlew test
cd mobile && flutter test
```
기준: 둘 다 실패 0건.
