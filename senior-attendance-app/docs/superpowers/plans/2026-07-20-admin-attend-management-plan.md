# 관리자용 개별 출석 관리 — 구현 계획 (짧은 형식, constitution.md 준수)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** 관리자가 장소+날짜로 특정 일정을 조회하고, 그 일정의 출석자 개별 상태(+사유)를 수정할 수 있게 한다.

**참고 스펙:** `docs/superpowers/specs/2026-07-20-admin-attend-management-design.md`

**이 계획의 스타일:** 이 프로젝트의 `constitution.md`를 따라 짧은 체크리스트 형식으로 작성한다. 전체 코드 대신 파일별 변경 목적, 정확한 계약(경로/필드명/상태코드), 실패/통과해야 할 테스트만 적는다. 구현자는 지정된 기존 패턴 파일을 그대로 참고해서 작성한다.

## Global Constraints

- 신규 엔드포인트는 전부 `/api/admin/**` 아래 — 기존 `hasRole("ADMIN")` 매처가 자동 적용, `SecurityConfig` 변경 불필요.
- 관리자는 회원 자기-서비스와 달리 `AttendStatus` 어떤 값으로든 자유롭게 변경 가능.
- 기존 서비스 메서드(`AttendService.updateAttendStatus`) 재사용, 새 서비스 클래스 만들지 않는다 (로직이 단순 위임뿐이면 컨트롤러가 리포지토리를 직접 호출해도 됨 — `MemberSelfController`가 이미 이 패턴).
- 기존 DTO(`PlaceSummaryDto`) 재사용, 새 DTO는 응답 모양이 실제로 다를 때만 만든다.
- 모든 백엔드/프론트 테스트가 끝에 통과해야 한다.

---

### Task 1: 백엔드 — `GET /api/admin/places`

**목적:** admin-web의 장소 선택 드롭다운용 전체 장소 목록. 기존 `GET /api/v1/places`는 `hasRole("MEMBER")`라 admin 토큰으로 호출 불가 — 그래서 admin 전용 엔드포인트가 필요.

**파일:**
- Create: `backend/src/main/java/com/example/attempt/controller/AdminPlaceController.java`
  - `@RestController @RequestMapping("/api/admin/places")`, `PlaceRepository.findAll()` 직접 호출 후 기존 `PlaceSummaryDto`(패턴: `dto/place/PlaceSummaryDto.java`)로 매핑. 별도 서비스 클래스 없음 — `MemberSelfController.java`의 리포지토리 직접 호출 패턴 참고.
- Test: `backend/src/test/java/com/example/attempt/controller/AdminPlaceControllerIntegrationTest.java` (신규, 패턴: `AdminAttendanceControllerIntegrationTest.java`의 `obtainAdminAccessToken`/`obtainMemberAccessToken` 헬퍼 재사용)

**계약:** `GET /api/admin/places` → 200, `List<PlaceSummaryDto>` (unitType 필터 없음 — 관리자는 전체를 봐야 함).

**실패해야 할 테스트 → 통과해야 할 테스트:**
- 인증 없음 → 401
- MEMBER 토큰 → 403
- ADMIN 토큰, Place 2개 이상 저장된 상태 → 200, 배열 길이/필드 확인

**커밋:** `feat(admin): add GET /api/admin/places for admin place picker`

---

### Task 2: 백엔드 — `GET /api/admin/schedules?placeId&date`

**목적:** 장소+날짜의 일정 1건과 그 출석자 목록 조회.

**파일:**
- Modify: `backend/src/main/java/com/example/attempt/repository/ScheduleRepository.java` — `Optional<Schedule> findByPlaceIdAndScheduleDate(Long placeId, LocalDate scheduleDate)` 추가 (1번 작업의 `existsByPlaceIdAndScheduleDate`와 짝).
- Create: `backend/src/main/java/com/example/attempt/dto/admin/AdminScheduleAttendanceResponse.java` — `{ scheduleId, title, scheduleDate, startTime, endTime, placeName, attendees: List<AttendeeItem> }`
- Create: `backend/src/main/java/com/example/attempt/dto/admin/AttendeeItem.java` — `{ attendId, memberId, memberName, status: AttendStatus, note, attendedAt }` (패턴: `dto/admin/AttendanceSummaryResponse.java`와 동일한 `@Data @NoArgsConstructor @AllArgsConstructor` 스타일)
- Modify: `backend/src/main/java/com/example/attempt/controller/ScheduleController.java` — `@GetMapping` 추가. `Schedule.attends`(기존 `@OneToMany` 필드)를 순회해 `AttendeeItem` 리스트로 매핑 — 별도 서비스 로직 없이 컨트롤러에서 직접 매핑 가능한 수준.
- Modify: `backend/src/test/java/com/example/attempt/controller/ScheduleControllerIntegrationTest.java` (테스트 추가)

**계약:** 일정 없으면 404 (`ResourceNotFoundException`). 있으면 200 + 위 응답 모양.

**실패해야 할 테스트 → 통과해야 할 테스트:**
- 인증 없음 → 401
- MEMBER 토큰 → 403
- 장소+날짜에 일정 없음 → 404
- 일정 있고 출석자 2명 → 200, `attendees` 길이/각 필드 확인 (다른 장소·날짜의 일정이 섞여 나오지 않는지도 확인)

**커밋:** `feat(admin): add GET /api/admin/schedules for place+date attendance lookup`

---

### Task 3: 백엔드 — `PATCH /api/admin/attend/{attendId}`

**목적:** 관리자가 개별 출석자의 상태+사유를 수정.

**파일:**
- Create: `backend/src/main/java/com/example/attempt/controller/AdminAttendController.java` — `@RequestMapping("/api/admin/attend")`, `PATCH /{attendId}` → `AttendService.updateAttendStatus(attendId, request.getStatus(), request.getNote())` 호출 후 갱신된 `Attend`를 `AttendeeItem`(Task 2에서 만든 DTO 재사용)으로 응답.
- Create: `backend/src/main/java/com/example/attempt/dto/admin/UpdateAttendStatusRequest.java` — `{ status: AttendStatus, note: String }`
- Test: `backend/src/test/java/com/example/attempt/controller/AdminAttendControllerIntegrationTest.java` (신규)

**계약:** attendId 없으면 404. 있으면 200 + 갱신된 `AttendeeItem`.

**실패해야 할 테스트 → 통과해야 할 테스트:**
- 인증 없음 → 401
- MEMBER 토큰 → 403
- 존재하지 않는 attendId → 404
- 정상 변경 → 200, DB에서 실제로 status/note가 바뀌었는지 재조회로 확인

**커밋:** `feat(admin): add PATCH /api/admin/attend/{id} for manual status override`

---

### Task 4: admin-web — 일정별 출석 관리 화면

**목적:** Task 1~3 API를 사용하는 화면 하나 + 로비에서 진입 링크.

**파일:**
- Create: `admin-web/src/features/attend-management/AttendManagementPage.tsx` — 장소 드롭다운(`GET /api/admin/places`) + 날짜 입력 → 조회 버튼 → `GET /api/admin/schedules` 호출 → 출석자 테이블(이름/상태/사유) 렌더링. 일정 없으면 "해당 날짜에 일정이 없습니다" 표시. 행마다 상태/사유 수정 → `PATCH /api/admin/attend/{id}` → 성공 시 목록 재조회. 구조는 `LobbyPage.tsx`(현재 파일)의 `apiFetch`/`useState`/에러 처리 패턴을 그대로 따른다.
- Create: `admin-web/src/features/attend-management/types.ts` — 위 API 응답에 대응하는 타입 (패턴: `types/attendance.ts`)
- Modify: `admin-web/src/App.tsx` — `/attend-management` 라우트 추가, `RequireAuth`로 감싸기 (기존 `/` 라우트와 동일 패턴)
- Modify: `admin-web/src/features/lobby/LobbyPage.tsx` — 상단에 "일정별 출석 관리" 진입 링크(`react-router-dom`의 `Link`) 한 줄 추가
- Test: `admin-web/src/features/attend-management/AttendManagementPage.test.tsx` (신규, 패턴: `LobbyPage.test.tsx`의 `apiFetch` mock 방식)

**실패해야 할 테스트 → 통과해야 할 테스트:**
- 장소+날짜 조회 성공 → 출석자 테이블 렌더링
- 일정 없음(404) → 안내 문구 렌더링
- 행 수정 → PATCH 호출 확인 + 목록 갱신 렌더링 확인
- 로비에서 링크 클릭 → 화면 전환 확인 (또는 라우트 존재만 확인)

**커밋:** `feat(admin-web): add schedule attendance management screen`

---

### Task 5: 전체 회귀 테스트

**실행:**
```
cd backend && ./gradlew test
cd admin-web && npx vitest run
```
**기준:** 둘 다 실패 0건. 실패 시 원인 수정 후 재실행, 수정 있었으면 별도 커밋.

---

## 자체 점검

- 스펙의 3개 계약(장소 목록, 일정+출석자 조회, 상태 변경)이 Task 1~3에 각각 대응.
- admin-web 화면(Task 4)이 세 API를 모두 사용.
- 새 추상화 없음 — 기존 DTO/컨트롤러 패턴 재사용, 서비스 레이어는 로직이 있는 곳(`ScheduleService`, `AttendService`)에만 유지.
