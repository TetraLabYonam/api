# 회원 로그인 직번+QR 전환 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 회원 로그인을 전화번호+SMS-OTP에서 직번(employeeId)+전화번호(해시) 방식으로 바꾸고, 관리자가 회원 등록 시 QR을 발급하며, 모바일은 로그인 이후 사업단/일자리 선택 없이 바로 위치동의→체크인으로 진입하고 로그아웃 전까지 그 상태를 유지한다.

**Architecture:** 백엔드(Member 엔티티+로그인/관리자API) → admin-web(회원 관리 화면) → mobile(로그인 화면 통합+QR 스캔+자동 로그인) 순서로 경계마다 독립적으로 검증한다. 각 경계는 이전 경계의 산출물(계약)에만 의존한다.

**Tech Stack:** Spring Boot/JPA/H2·MariaDB(기존), React+Vite(admin-web, 기존) + `qrcode.react`(신규), Flutter(mobile, 기존) + `mobile_scanner`(신규)

**참고 문서:** `docs/superpowers/specs/2026-07-21-member-employee-id-qr-login-design.md`

## Global Constraints

- 전화번호 평문은 회원 등록 API 응답(QR 생성용) 외 서버 어디에도 저장하지 않는다.
- 로그인 실패 사유(직번 없음/불일치/비활성)는 항상 동일한 401 메시지로 응답한다: `"직번 또는 전화번호가 올바르지 않습니다"`.
- 기존 회원 데이터 마이그레이션은 하지 않는다 — 스키마 변경 후 기존 방식 데이터는 폐기.
- 각 태스크 종료 시 해당 플랫폼의 전체 테스트가 통과해야 다음 태스크로 넘어간다.

---

## Task 1: Member 엔티티 — employeeId/phoneNumberHash/active 도입

**Files:**
- Modify: `backend/src/main/java/com/example/attempt/domain/Member.java`
- Modify: `backend/src/main/java/com/example/attempt/repository/MemberRepository.java`
- Modify: `backend/src/main/java/com/example/attempt/AttemptApplication.java:34-38` (죽은 시드 코드가 옛 생성자를 쓰므로 컴파일 깨짐 — `run()` 본문의 `Member`/`Attend` 로컬 변수 3줄과 빈 줄 전부 삭제, `Attend`/`Member` import 제거)
- Modify (생성자 호출부 수정만, 로직 변경 없음): `backend/src/test/java/com/example/attempt/domain/PlaceMemberSchemaTest.java:36`, `backend/src/test/java/com/example/attempt/controller/AdminAttendControllerIntegrationTest.java:107`, `backend/src/test/java/com/example/attempt/controller/ScheduleControllerIntegrationTest.java:139,174,280,281,305`, `backend/src/test/java/com/example/attempt/repository/AttendRepositoryUnitTypeSummaryTest.java:41`, `backend/src/test/java/com/example/attempt/service/ScheduleServiceTest.java:63,64,83,106`

**Interfaces:**
- Produces: `Member(String username, String phoneNumberHash)` 생성자(기존과 동일한 2-String 시그니처, 의미만 "평문"→"해시"로 변경), `member.getEmployeeId(): Long`, `member.isActive(): boolean`, `MemberRepository.findByEmployeeId(Long): Optional<Member>`, `MemberRepository.findMaxEmployeeIdOrDefault(): Long`(JPQL, `COALESCE(MAX(employeeId), 1000)`)

- [ ] **Step 1:** `Member.java`에서 `phoneNumber` 필드와 관련 생성자를 제거하고 아래로 교체:
  ```java
  @Column(name = "employee_id", unique = true)
  private Long employeeId;

  @Column(name = "phone_number_hash")
  private String phoneNumberHash;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  public Member(String username, String phoneNumberHash) {
      this.username = username;
      this.phoneNumberHash = phoneNumberHash;
  }
  ```
  기존 `Member(String username, String phoneNumber, String guardianPhone)` 3-arg 생성자는 삭제한다 — 유일한 호출부가 `AttemptApplication.java`의 죽은 코드(Step 3에서 삭제)뿐이라 이후로는 쓰이지 않는다. `guardianPhone`이 필요하면 `setGuardianPhone()`으로 충분하다.

- [ ] **Step 2:** `MemberRepository.java`에서 `findByPhoneNumber` 삭제, 아래 추가:
  ```java
  Optional<Member> findByEmployeeId(Long employeeId);

  @Query("SELECT COALESCE(MAX(m.employeeId), 1000) FROM Member m")
  Long findMaxEmployeeIdOrDefault();
  ```

- [ ] **Step 3:** `AttemptApplication.java`의 `run()` 본문에서 죽은 `Member`/`Attend` 지역변수 3+1줄과 미사용 import 삭제 (컴파일 통과 목적, 원래도 `save()` 호출 없이 아무 효과 없던 코드).

- [ ] **Step 4:** 위에 나열한 테스트 파일들의 `new Member("이름", "010....")` 호출부는 이제 두 번째 인자가 "해시"라는 의미이지만, 이 테스트들은 실제 해시 매칭을 검증하지 않으므로 문자열 값 자체는 그대로 둬도 무방 — 컴파일만 확인.

- [ ] **Step 5:** 컴파일 확인
  ```bash
  cd backend && ./gradlew compileJava compileTestJava
  ```
  Expected: BUILD SUCCESSFUL

- [ ] **Step 6:** Commit
  ```bash
  git add backend/src/main/java/com/example/attempt/domain/Member.java \
          backend/src/main/java/com/example/attempt/repository/MemberRepository.java \
          backend/src/main/java/com/example/attempt/AttemptApplication.java \
          backend/src/test/java/com/example/attempt/domain/PlaceMemberSchemaTest.java \
          backend/src/test/java/com/example/attempt/controller/AdminAttendControllerIntegrationTest.java \
          backend/src/test/java/com/example/attempt/controller/ScheduleControllerIntegrationTest.java \
          backend/src/test/java/com/example/attempt/repository/AttendRepositoryUnitTypeSummaryTest.java \
          backend/src/test/java/com/example/attempt/service/ScheduleServiceTest.java
  git commit -m "refactor(member): replace phoneNumber with employeeId+phoneNumberHash"
  ```

---

## Task 2: 회원 로그인 API 교체 + OTP 삭제

**Files:**
- Delete: `backend/src/main/java/com/example/attempt/service/MemberOtpService.java`, `backend/src/main/java/com/example/attempt/domain/MemberOtpCode.java`, `backend/src/main/java/com/example/attempt/repository/MemberOtpCodeRepository.java`, `backend/src/main/java/com/example/attempt/dto/memberauth/OtpRequestRequest.java`, `backend/src/main/java/com/example/attempt/dto/memberauth/OtpVerifyRequest.java`
- Delete (테스트): `backend/src/test/java/com/example/attempt/service/MemberOtpServiceTest.java`, `backend/src/test/java/com/example/attempt/repository/MemberOtpCodeRepositoryTest.java`
- Create: `backend/src/main/java/com/example/attempt/dto/memberauth/MemberLoginRequest.java`
- Modify: `backend/src/main/java/com/example/attempt/controller/MemberAuthController.java` (otp/request·otp/verify 삭제, login 추가, refresh는 employeeId 기준으로 조정)
- Modify: `backend/src/main/java/com/example/attempt/service/CurrentMemberService.java`
- Modify: `backend/src/main/java/com/example/attempt/controller/MemberSelfController.java:22-29` (`me()`에서 `phoneNumber` 필드 제거)
- Modify (재작성): `backend/src/test/java/com/example/attempt/controller/MemberAuthControllerIntegrationTest.java`
- Modify (재작성): `backend/src/test/java/com/example/attempt/service/CurrentMemberServiceTest.java`

**Interfaces:**
- Consumes: `PasswordEncoder`(기존 `SecurityConfig` 빈, `BCryptPasswordEncoder`), `Member.findByEmployeeId`, `Member.findMaxEmployeeIdOrDefault`(Task 1)
- Produces: `POST /api/v1/member-auth/login { employeeId: number, phoneNumber: string } -> 200 { accessToken, memberId } | 401 { error }`. `CurrentMemberService.getCurrentMember()`는 그대로 시그니처 유지(내부 조회 키만 변경) — Task 3에서 이걸 그대로 쓰는 다른 컨트롤러들은 수정 불필요.

- [ ] **Step 1: `MemberLoginRequest` DTO 작성**
  ```java
  package com.example.attempt.dto.memberauth;

  import jakarta.validation.constraints.NotNull;
  import lombok.Data;

  @Data
  public class MemberLoginRequest {
      @NotNull(message = "직번은 필수입니다.")
      private Long employeeId;

      @NotNull(message = "전화번호는 필수입니다.")
      private String phoneNumber;
  }
  ```

- [ ] **Step 2: `MemberAuthControllerIntegrationTest`를 새 로그인 계약 기준으로 재작성** — 아래 4개 테스트로 교체:
  - `login_withMatchingEmployeeIdAndPhoneNumber_returns200AndAccessToken` — `PasswordEncoder`로 해시한 전화번호를 가진 Member를 저장해두고 평문 전화번호로 로그인, `accessToken` 존재 확인
  - `login_withWrongPhoneNumber_returns401`
  - `login_withUnknownEmployeeId_returns401`
  - `login_withInactiveMember_returns401` — `active=false`인 Member로 로그인 시도
  각 테스트는 `@Autowired MemberRepository`, `@Autowired PasswordEncoder`로 직접 Member를 세팅한다(등록 API는 Task 4에서 생기므로 아직 리포지토리 직접 저장).

- [ ] **Step 3:** 테스트 실행해서 실패 확인 (아직 `/login` 없음)
  ```bash
  cd backend && ./gradlew test --tests "*.MemberAuthControllerIntegrationTest" 2>&1 | tail -30
  ```
  Expected: FAIL (404 or compile error referencing `MemberLoginRequest`)

- [ ] **Step 4: `MemberAuthController` 수정** — `otp/request`, `otp/verify` 메서드와 `MemberOtpService memberOtpService` 필드 삭제, 아래 추가 (생성자에 `PasswordEncoder passwordEncoder` 주입 추가):
  ```java
  @PostMapping("/login")
  public ResponseEntity<?> login(@Valid @RequestBody MemberLoginRequest request) {
      Optional<Member> opt = memberRepository.findByEmployeeId(request.getEmployeeId());
      if (opt.isEmpty() || !opt.get().isActive()
              || !passwordEncoder.matches(request.getPhoneNumber(), opt.get().getPhoneNumberHash())) {
          return ResponseEntity.status(401).body(Map.of("error", "직번 또는 전화번호가 올바르지 않습니다"));
      }
      Member member = opt.get();
      String subject = member.getEmployeeId().toString();
      String accessToken = jwtTokenProvider.createAccessToken(subject, Map.of("roles", new String[]{"ROLE_MEMBER"}));
      String rawRefresh = refreshTokenService.createRefreshToken(subject, null, Duration.ofMillis(refreshExpMs));
      ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", rawRefresh)
              .httpOnly(true).secure(cookieSecure).path("/api/v1/member-auth/refresh")
              .maxAge(refreshExpMs / 1000).sameSite("Strict").build();
      return ResponseEntity.ok().header("Set-Cookie", refreshCookie.toString())
              .body(Map.of("accessToken", accessToken, "memberId", member.getId()));
  }
  ```
  `refresh()` 메서드 내부에서 `memberRepository.findByPhoneNumber(phoneNumber)` 호출부를 `memberRepository.findByEmployeeId(Long.parseLong(subject))`로 교체하고, `createAccessToken`에 넘기던 `member.getPhoneNumber()`를 `member.getEmployeeId().toString()`으로 교체.

- [ ] **Step 5:** 테스트 재실행, 통과 확인
  ```bash
  cd backend && ./gradlew test --tests "*.MemberAuthControllerIntegrationTest" 2>&1 | tail -30
  ```
  Expected: PASS (4/4)

- [ ] **Step 6: `CurrentMemberService` 수정**
  ```java
  public Member getCurrentMember() {
      String employeeId = SecurityContextHolder.getContext().getAuthentication().getName();
      return memberRepository.findByEmployeeId(Long.parseLong(employeeId))
              .orElseThrow(() -> new ResourceNotFoundException("인증된 회원을 찾을 수 없습니다. employeeId=" + employeeId));
  }
  ```
  `CurrentMemberServiceTest`도 `findByPhoneNumber`→`findByEmployeeId`, 인증 주체 문자열을 `"1001"` 같은 employeeId 문자열로 바꿔 재작성.

- [ ] **Step 7: `MemberSelfController.me()`에서 `"phoneNumber", member.getPhoneNumber()` 라인 삭제** (해당 필드 자체가 없어졌으므로). 나머지(`assignedPlaceId`, `locationConsentAgreed`)는 그대로.

- [ ] **Step 8: OTP 관련 파일 전부 삭제**
  ```bash
  cd backend
  git rm src/main/java/com/example/attempt/service/MemberOtpService.java \
         src/main/java/com/example/attempt/domain/MemberOtpCode.java \
         src/main/java/com/example/attempt/repository/MemberOtpCodeRepository.java \
         src/main/java/com/example/attempt/dto/memberauth/OtpRequestRequest.java \
         src/main/java/com/example/attempt/dto/memberauth/OtpVerifyRequest.java \
         src/test/java/com/example/attempt/service/MemberOtpServiceTest.java \
         src/test/java/com/example/attempt/repository/MemberOtpCodeRepositoryTest.java
  ```

- [ ] **Step 9:** 백엔드 전체 테스트 실행 (Task 3 이전이라 OTP 헬퍼를 쓰던 7개 파일은 아직 컴파일 에러 — 이 시점엔 그 7개 파일만 `--tests` 로 제외하고 확인하거나, Task 3과 함께 커밋해도 됨. 여기서는 변경한 파일만 우선 검증):
  ```bash
  cd backend && ./gradlew test --tests "com.example.attempt.controller.MemberAuthControllerIntegrationTest" --tests "com.example.attempt.service.CurrentMemberServiceTest" --tests "com.example.attempt.controller.MemberSelfControllerIntegrationTest" 2>&1 | tail -40
  ```
  Expected: PASS. (`MemberSelfControllerIntegrationTest`는 Task 3에서 헬퍼를 바꿔야 완전히 통과 — 지금은 컴파일 에러가 나면 정상, Task 3에서 해결)

- [ ] **Step 10:** Commit
  ```bash
  git add -A
  git commit -m "feat(member-auth): replace SMS OTP login with employeeId+phone login"
  ```

---

## Task 3: 회원 인증 통합 테스트 헬퍼 스윕

**Files:**
- Create: `backend/src/test/java/com/example/attempt/support/MemberAuthTestSupport.java`
- Modify (헬퍼 교체): `backend/src/test/java/com/example/attempt/controller/MemberSelfControllerIntegrationTest.java`, `AdminAttendanceControllerIntegrationTest.java`, `AdminAttendControllerIntegrationTest.java`, `AttendControllerIntegrationTest.java`, `AdminPlaceControllerIntegrationTest.java`(멤버 토큰 사용 여부 확인 후 필요시), `PlaceControllerIntegrationTest.java`, `ScheduleControllerIntegrationTest.java`

**Interfaces:**
- Produces: `MemberAuthTestSupport.loginAsMember(TestRestTemplate restTemplate, int port, MemberRepository memberRepository, PasswordEncoder passwordEncoder, String username, String phoneNumber): String` — Member를 employeeId 자동채번(`findMaxEmployeeIdOrDefault()+1`)으로 저장하고 `/api/v1/member-auth/login`을 호출해 accessToken을 반환.

- [ ] **Step 1: 공용 헬퍼 작성**
  ```java
  package com.example.attempt.support;

  import com.example.attempt.domain.Member;
  import com.example.attempt.repository.MemberRepository;
  import org.springframework.boot.test.web.client.TestRestTemplate;
  import org.springframework.http.*;
  import org.springframework.security.crypto.password.PasswordEncoder;

  import java.util.Map;

  public class MemberAuthTestSupport {
      public static String loginAsMember(TestRestTemplate restTemplate, int port,
              MemberRepository memberRepository, PasswordEncoder passwordEncoder,
              String username, String phoneNumber) {
          Member member = new Member(username, passwordEncoder.encode(phoneNumber));
          member.setEmployeeId(memberRepository.findMaxEmployeeIdOrDefault() + 1);
          member = memberRepository.save(member);

          HttpHeaders headers = new HttpHeaders();
          headers.setContentType(MediaType.APPLICATION_JSON);
          HttpEntity<Map<String, Object>> req = new HttpEntity<>(
                  Map.of("employeeId", member.getEmployeeId(), "phoneNumber", phoneNumber), headers);
          ResponseEntity<Map> resp = restTemplate.postForEntity(
                  "http://localhost:" + port + "/api/v1/member-auth/login", req, Map.class);
          return (String) resp.getBody().get("accessToken");
      }
  }
  ```

- [ ] **Step 2:** 위 7개 파일에서 자체 `obtainMemberAccessToken(phoneNumber)` 사설 메서드(OTP request/verify + `ArgumentCaptor`로 문자 코드 캡처하던 부분)를 삭제하고, 호출부를 `MemberAuthTestSupport.loginAsMember(restTemplate, port, memberRepository, passwordEncoder, "김할매", phoneNumber)` 호출로 교체. `MemberRepository`, `PasswordEncoder`가 아직 `@Autowired`로 없는 파일은 추가.

- [ ] **Step 3:** 백엔드 전체 테스트 실행
  ```bash
  cd backend && ./gradlew test 2>&1 | tail -20
  find backend/build/test-results -name "*.xml" | xargs grep -oh 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' | awk -F'"' '{t+=$2; f+=$6; e+=$8} END {print "tests="t, "failures="f, "errors="e}'
  ```
  Expected: `failures=0 errors=0`

- [ ] **Step 4:** Commit
  ```bash
  git add -A
  git commit -m "test(member-auth): sweep integration tests onto employeeId login helper"
  ```

---

## Task 4: 관리자 회원 API (등록/목록/활성화 토글)

**Files:**
- Create: `backend/src/main/java/com/example/attempt/dto/admin/RegisterMemberRequest.java`, `RegisterMemberResponse.java`, `MemberSummaryResponse.java`, `UpdateMemberActiveRequest.java`
- Create: `backend/src/main/java/com/example/attempt/service/AdminMemberService.java`
- Create: `backend/src/main/java/com/example/attempt/controller/AdminMemberController.java`
- Create (test): `backend/src/test/java/com/example/attempt/controller/AdminMemberControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `MemberRepository`, `PlaceRepository`, `PasswordEncoder` (기존 빈)
- Produces:
  - `POST /api/admin/members { name, phoneNumber, placeId } -> 200 { employeeId, name, placeId, qrPayload }` (`qrPayload` = `"{employeeId}:{phoneNumber}"`, 이 응답 전용, 저장 안 함)
  - `GET /api/admin/members -> [{ employeeId, name, placeId, placeName, active }]`
  - `PATCH /api/admin/members/{employeeId} { active } -> 200`, 없으면 404

- [ ] **Step 1: DTO 4개 작성** (기존 `dto/admin/*` 스타일 그대로: `@Data @NoArgsConstructor @AllArgsConstructor`)
  ```java
  // RegisterMemberRequest.java
  @Data
  public class RegisterMemberRequest {
      @NotBlank private String name;
      @NotBlank private String phoneNumber;
      @NotNull private Long placeId;
  }
  ```
  ```java
  // RegisterMemberResponse.java / MemberSummaryResponse.java / UpdateMemberActiveRequest.java
  // 필드는 위 계약표 그대로, Lombok @Data + 생성자
  ```

- [ ] **Step 2: `AdminMemberControllerIntegrationTest` 작성** (기존 `AdminPlaceControllerIntegrationTest` 패턴 그대로 — `obtainAdminAccessToken()` 재사용):
  - `register_withValidRequest_createsMemberAndReturnsQrPayload` — 응답의 `employeeId`가 1001 이상, `qrPayload`가 `"{employeeId}:01000000000"` 형식인지 확인, DB에 평문 전화번호가 없는지(`memberRepository.findByEmployeeId(...).getPhoneNumberHash()`가 원문과 다른지) 확인
  - `register_withNonExistentPlace_returns400`
  - `list_returnsAllMembersWithPlaceName`
  - `updateActive_setsFalse_thenLoginFails` — PATCH 후 `MemberAuthTestSupport` 대신 직접 `/login` 호출해 401 확인

- [ ] **Step 3:** 테스트 실행, 실패 확인 (컨트롤러 없음)
  ```bash
  cd backend && ./gradlew test --tests "*.AdminMemberControllerIntegrationTest" 2>&1 | tail -30
  ```
  Expected: FAIL

- [ ] **Step 4: `AdminMemberService` 작성** — `register()`(employeeId 자동채번은 Task 3과 동일하게 `findMaxEmployeeIdOrDefault()+1`, `phoneNumberHash = passwordEncoder.encode(phoneNumber)`, `placeRepository.findById` 없으면 `IllegalArgumentException`→컨트롤러에서 400 처리), `list()`, `updateActive(employeeId, active)`(없으면 `ResourceNotFoundException`→404).

- [ ] **Step 5: `AdminMemberController` 작성** (`@RequestMapping("/api/admin/members")`, 기존 `AdminAttendanceController` 스타일).

- [ ] **Step 6:** 테스트 재실행, 통과 확인
  ```bash
  cd backend && ./gradlew test --tests "*.AdminMemberControllerIntegrationTest" 2>&1 | tail -30
  ```
  Expected: PASS (4/4)

- [ ] **Step 7:** 백엔드 전체 테스트
  ```bash
  cd backend && ./gradlew test 2>&1 | tail -10
  ```
  Expected: 전체 통과

- [ ] **Step 8:** Commit
  ```bash
  git add -A
  git commit -m "feat(admin): add member registration/list/active-toggle API"
  ```

---

## Task 5: admin-web 회원 관리 화면

**Files:**
- Modify: `admin-web/package.json` (`qrcode.react` 의존성 추가)
- Create: `admin-web/src/features/member-management/types.ts`, `MemberManagementPage.tsx`, `MemberManagementPage.test.tsx`
- Modify: `admin-web/src/App.tsx` (라우트 추가), `admin-web/src/components/AdminLayout.tsx` (사이드바 링크 추가)

**Interfaces:**
- Consumes: `apiFetch`(기존 `api/client.ts`), `/api/admin/places`(기존), `/api/admin/members`(Task 4)
- Produces: `MemberManagementPage` — 라우트 `/member-management`

- [ ] **Step 1:** 의존성 추가
  ```bash
  cd admin-web && npm install qrcode.react
  ```

- [ ] **Step 2: `types.ts`**
  ```ts
  export interface MemberSummary {
    employeeId: number;
    name: string;
    placeId: number;
    placeName: string;
    active: boolean;
  }
  export interface RegisterMemberResult {
    employeeId: number;
    name: string;
    placeId: number;
    qrPayload: string;
  }
  ```

- [ ] **Step 3: `MemberManagementPage.test.tsx` 작성** (기존 `AttendManagementPage.test.tsx` 패턴 그대로 — `vi.mock('../../api/client', ...)`, `vi.mock('../auth/AuthContext', ...)`):
  - `등록 폼 제출 성공 시 QR을 표시한다` — `screen.findByRole('img', { name: /QR/ })` 또는 `qrcode.react`가 렌더하는 `<canvas>`/`<svg>` 존재 확인
  - `장소 미선택 시 등록 버튼이 비활성화된다`
  - `회원 목록을 렌더링하고 활성 토글을 누르면 PATCH를 호출한다`

- [ ] **Step 4:** 테스트 실행, 실패 확인
  ```bash
  cd admin-web && npx vitest run src/features/member-management 2>&1 | tail -40
  ```
  Expected: FAIL (모듈 없음)

- [ ] **Step 5: `MemberManagementPage.tsx` 작성** — `AttendManagementPage.tsx`와 동일한 상태관리 패턴(`useState`+`useCallback`+`useEffect`), `AdminLayout`으로 감싸기, 등록 폼 제출 성공 시 `QRCodeSVG value={result.qrPayload}` 렌더 + 다운로드 버튼(`<a download>` + canvas/svg를 데이터 URL로), 회원 목록 테이블(기존 `.data-table` 클래스 재사용).

- [ ] **Step 6:** 테스트 재실행, 통과 확인
  ```bash
  cd admin-web && npx vitest run src/features/member-management 2>&1 | tail -20
  ```
  Expected: PASS

- [ ] **Step 7: `App.tsx`에 라우트 추가, `AdminLayout.tsx`의 `NAV_ITEMS`에 `{ to: '/member-management', label: '회원 관리' }` 추가**

- [ ] **Step 8:** admin-web 전체 테스트 + 빌드
  ```bash
  cd admin-web && npx vitest run 2>&1 | tail -20 && npx tsc --noEmit && npx vite build 2>&1 | tail -10
  ```
  Expected: 전체 통과, 빌드 성공

- [ ] **Step 9:** Commit
  ```bash
  git add -A
  git commit -m "feat(admin-web): add member management screen with QR issuance"
  ```

---

## Task 6: mobile 로그인 화면 통합 (수동 입력)

**Files:**
- Delete: `mobile/lib/features/auth/otp_verify_screen.dart`, `mobile/test/features/auth/otp_verify_screen_test.dart`
- Modify → Create 대체: `mobile/lib/features/auth/phone_login_screen.dart` → `mobile/lib/features/auth/login_screen.dart` (파일명 변경 + 재작성), `mobile/test/features/auth/phone_login_screen_test.dart` → `mobile/test/features/auth/login_screen_test.dart`
- Modify: `mobile/lib/features/auth/auth_repository.dart` (`requestOtp`/`verifyOtp` 제거, `login(employeeId, phoneNumber)` 추가), `mobile/lib/main.dart`(라우팅에서 옛 화면 참조 제거는 Task 8에서)

**Interfaces:**
- Produces: `AuthRepository.login(int employeeId, String phoneNumber): Future<String>` (accessToken 반환 + 저장), `LoginScreen` 위젯(직번 입력 필드 + 전화번호 입력 필드 + 로그인 버튼 + "QR로 로그인" 버튼 — QR 버튼은 Task 7에서 동작 연결, 이번 태스크에선 자리만 배치)

- [ ] **Step 1: `login_screen_test.dart` 작성** (기존 `phone_login_screen_test.dart`의 디바이스 사이즈/구조 패턴 재사용). 로그인 성공 시 이동 대상은 **항상 `ConsentScreen`**이다 — 로그인은 로그아웃 후 다시 하는 드문 경우뿐이라, 이미 예전에 동의했던 회원이라도 다시 한번 동의 화면을 보여주는 편이 "로그인 화면 자체가 동의 여부까지 알아야 하는" 복잡도보다 낫다(동의 여부에 따른 분기는 앱 재실행 시의 `AuthGate`만 담당 — Task 8):
  ```dart
  testWidgets('직번과 전화번호를 입력하고 로그인을 누르면 위치동의 화면으로 이동한다', (tester) async {
    await tester.binding.setSurfaceSize(const Size(390, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(ProviderScope(
      overrides: [apiClientProvider.overrideWithValue(fakeApiClient((options) async {
        return jsonResponse('{"accessToken":"tok","memberId":1}');
      }))],
      child: const MaterialApp(home: LoginScreen()),
    ));
    await tester.enterText(find.byKey(const Key('employeeIdField')), '1001');
    await tester.enterText(find.byKey(const Key('phoneNumberField')), '01012345678');
    await tester.tap(find.widgetWithText(ElevatedButton, '로그인'));
    await tester.pumpAndSettle();
    expect(find.byType(ConsentScreen), findsOneWidget);
  });
  ```
  실패 케이스(`401`→에러 메시지 표시, 화면 유지)도 1개 추가.

- [ ] **Step 2:** 테스트 실행, 실패 확인
  ```bash
  cd mobile && flutter test test/features/auth/login_screen_test.dart 2>&1 | tail -30
  ```
  Expected: FAIL (LoginScreen 없음)

- [ ] **Step 3: `auth_repository.dart` 수정** — `requestOtp`/`verifyOtp` 삭제, 아래 추가:
  ```dart
  Future<String> login(int employeeId, String phoneNumber) async {
    final response = await dio.post('/api/v1/member-auth/login', data: {
      'employeeId': employeeId,
      'phoneNumber': phoneNumber,
    });
    final accessToken = response.data['accessToken'] as String;
    await tokenStorage.saveAccessToken(accessToken);
    return accessToken;
  }
  ```

- [ ] **Step 4: `login_screen.dart` 작성** — `phone_login_screen.dart`의 디자인 시스템 사용(`AtmColors`, `AtmPrimaryButton`, 헤더 스타일)을 유지하되 숫자 키패드 대신 두 개의 일반 텍스트필드(직번=숫자만, 전화번호)와 하단에 QR 버튼(자리만, onPressed는 Task 7에서 채움) 배치. 로그인 성공 시 `Navigator.of(context).pushAndRemoveUntil(MaterialPageRoute(builder: (_) => const ConsentScreen()), (route) => false)`.

- [ ] **Step 5:** 테스트 재실행, 통과 확인
  ```bash
  cd mobile && flutter test test/features/auth/login_screen_test.dart 2>&1 | tail -30
  ```
  Expected: PASS

- [ ] **Step 6:** 옛 파일 삭제
  ```bash
  cd mobile
  git rm lib/features/auth/otp_verify_screen.dart lib/features/auth/phone_login_screen.dart \
         test/features/auth/otp_verify_screen_test.dart test/features/auth/phone_login_screen_test.dart
  ```

- [ ] **Step 7:** Commit
  ```bash
  git add -A
  git commit -m "feat(mobile): replace phone+OTP login with employeeId+phone login screen"
  ```

---

## Task 7: mobile QR 스캔 로그인

**Files:**
- Modify: `mobile/pubspec.yaml` (`mobile_scanner` 추가)
- Create: `mobile/lib/features/auth/qr_login_screen.dart`, `mobile/test/features/auth/qr_login_screen_test.dart`
- Modify: `mobile/lib/features/auth/login_screen.dart` ("QR로 로그인" 버튼 `onPressed` 연결)

**Interfaces:**
- Consumes: `AuthRepository.login`(Task 6)
- Produces: `QrLoginScreen` — 카메라로 `"{employeeId}:{phoneNumber}"` 형식 문자열을 읽어 파싱, `employeeId:`가 정수로 안 읽히면 로컬에서 에러 표시(API 호출 안 함), 파싱 성공 시 `login()` 호출 후 체크인 화면 이동

- [ ] **Step 1:** 의존성 추가
  ```yaml
  # pubspec.yaml dependencies에 추가
  mobile_scanner: ^7.1.5
  ```
  ```bash
  cd mobile && flutter pub get
  ```

- [ ] **Step 2: QR 페이로드 파싱 단위 테스트 작성** (`qr_login_screen_test.dart` 최상단, 위젯 테스트 전에 순수 함수 테스트로) — `qr_login_screen.dart`에 `({int employeeId, String phoneNumber})? parseQrPayload(String raw)` 최상위 함수를 두고:
  ```dart
  test('정상 포맷 파싱', () {
    final result = parseQrPayload('1001:01012345678');
    expect(result?.employeeId, 1001);
    expect(result?.phoneNumber, '01012345678');
  });
  test('콜론 없으면 null', () {
    expect(parseQrPayload('invalid'), isNull);
  });
  test('직번이 숫자가 아니면 null', () {
    expect(parseQrPayload('abc:01012345678'), isNull);
  });
  ```

- [ ] **Step 3:** 테스트 실행, 실패 확인
  ```bash
  cd mobile && flutter test test/features/auth/qr_login_screen_test.dart 2>&1 | tail -30
  ```
  Expected: FAIL

- [ ] **Step 4: `qr_login_screen.dart` 작성** — `parseQrPayload` 함수 + `MobileScanner` 위젯을 감싸 첫 유효 바코드 감지 시 `onDetect` 콜백에서 파싱→로그인 호출. 카메라 권한 없음/파싱 실패는 화면에 안내 문구 + "직접 입력으로 전환" 버튼(뒤로가기로 `LoginScreen` 복귀).

- [ ] **Step 5:** 테스트 재실행, 통과 확인
  ```bash
  cd mobile && flutter test test/features/auth/qr_login_screen_test.dart 2>&1 | tail -30
  ```
  Expected: PASS

- [ ] **Step 6:** `login_screen.dart`의 QR 버튼에서 `Navigator.push(... QrLoginScreen())` 연결.

- [ ] **Step 7:** Commit
  ```bash
  git add -A
  git commit -m "feat(mobile): add QR scan login"
  ```

---

## Task 8: mobile AuthGate 자동 로그인 + 구화면 제거

**Files:**
- Modify: `mobile/lib/main.dart` (`AuthGate` 재작성, 라우트 테이블에서 `unit-selection` 제거)
- Modify: `mobile/lib/features/auth/auth_provider.dart` (`isLoggedInProvider`를 `/api/v1/members/me` 조회 결과까지 포함하도록 확장)
- Modify: `mobile/lib/features/auth/auth_repository.dart` (`me()` 메서드 추가)
- Delete: `mobile/lib/features/unit_selection/unit_selection_screen.dart`, `mobile/lib/features/job_search/job_search_screen.dart`, `mobile/lib/features/job_search/job_repository.dart`, `mobile/lib/core/unit_type.dart`
- Delete (테스트): `mobile/test/features/unit_selection/unit_selection_screen_test.dart`, `mobile/test/features/job_search/job_search_screen_test.dart`, `mobile/test/features/job_search/job_repository_test.dart`(있다면)
- Modify: `mobile/test/widget_test.dart`

**Interfaces:**
- Consumes: `GET /api/v1/members/me -> { assignedPlaceId, locationConsentAgreed }` (기존 `MemberSelfController.me()`, Task 2에서 phoneNumber만 제거하고 그대로 유지된 계약)
- Produces: `AuthRepository.me(): Future<({bool locationConsentAgreed, int? assignedPlaceId})>`, `meProvider`(`FutureProvider`, `auth_provider.dart`), `AuthGate`가 셋 중 하나로 분기: `LoginScreen` / `ConsentScreen` / `CheckinScreen`

- [ ] **Step 1: `auth_repository.dart`에 `me()` 추가**
  ```dart
  Future<({bool locationConsentAgreed, int? assignedPlaceId})> me() async {
    final response = await dio.get('/api/v1/members/me');
    final data = response.data as Map<String, dynamic>;
    return (
      locationConsentAgreed: data['locationConsentAgreed'] as bool,
      assignedPlaceId: data['assignedPlaceId'] is int ? data['assignedPlaceId'] as int : null,
    );
  }
  ```

- [ ] **Step 2: `auth_provider.dart`에 `meProvider` 추가** (`FutureBuilder`에 인라인 `future:`를 넘기면 위젯 리빌드마다 새 요청이 나가는 문제가 있어, Riverpod 프로바이더로 캐싱):
  ```dart
  final meProvider = FutureProvider<({bool locationConsentAgreed, int? assignedPlaceId})>((ref) {
    return ref.watch(authRepositoryProvider).me();
  });
  ```

- [ ] **Step 3: `main.dart`의 `AuthGate` 재작성** — `isLoggedInProvider`가 `true`일 때 `meProvider`를 watch해 `locationConsentAgreed`로 분기:
  ```dart
  class AuthGate extends ConsumerWidget {
    const AuthGate({super.key});
    @override
    Widget build(BuildContext context, WidgetRef ref) {
      final isLoggedIn = ref.watch(isLoggedInProvider);
      return isLoggedIn.when(
        data: (loggedIn) {
          if (!loggedIn) return const LoginScreen();
          final me = ref.watch(meProvider);
          return me.when(
            data: (info) => info.locationConsentAgreed ? const CheckinScreen() : const ConsentScreen(),
            loading: () => const Scaffold(body: Center(child: CircularProgressIndicator())),
            error: (_, _) => const LoginScreen(),
          );
        },
        loading: () => const Scaffold(body: Center(child: CircularProgressIndicator())),
        error: (_, _) => const LoginScreen(),
      );
    }
  }
  ```
  `routes` 맵에서 `'/unit-selection': ...` 항목 삭제.

- [ ] **Step 4: `widget_test.dart` 수정** — `PhoneLoginScreen`/`'ATTENDANCE'` 기대값을 `LoginScreen` 기준(예: `'직번'`, `'전화번호'` 라벨 존재 확인)으로 교체. `apiFetch`가 실패(401)할 때 `LoginScreen`이 보이는 케이스로 단순화.

- [ ] **Step 5:** 옛 화면/리포지토리 삭제
  ```bash
  cd mobile
  git rm lib/features/unit_selection/unit_selection_screen.dart \
         lib/features/job_search/job_search_screen.dart \
         lib/features/job_search/job_repository.dart \
         lib/core/unit_type.dart \
         test/features/unit_selection/unit_selection_screen_test.dart \
         test/features/job_search/job_search_screen_test.dart
  ```
  (`job_repository_test.dart`가 존재하면 함께 삭제 — `find test -iname "job_repository_test.dart"`로 확인 후 처리)

- [ ] **Step 6:** 전체 모바일 테스트
  ```bash
  cd mobile && flutter test 2>&1 | tail -30
  ```
  Expected: 전체 통과 (unit_selection/job_search 관련 테스트는 삭제되어 카운트에서 빠짐)

- [ ] **Step 7:** `dart analyze lib test` — 이슈 없음 확인.

- [ ] **Step 8:** Commit
  ```bash
  git add -A
  git commit -m "feat(mobile): persist login across restarts, drop self-service unit/job selection"
  ```

---

## Self-Review 체크

- [ ] 스펙의 모든 계약(로그인/등록/목록/토글/QR/자동로그인)에 대응하는 태스크가 있는가 — 있음(Task 2,4,5,6,7,8)
- [ ] `TBD`/`TODO` 없음 확인
- [ ] 태스크 간 타입/시그니처 일치 확인: `Member.getEmployeeId()`(Task1) ↔ 로그인 subject(Task2) ↔ 테스트 헬퍼(Task3) ↔ 관리자 등록 응답(Task4) ↔ QR payload(Task5,7) 전부 `Long`/문자열 표현 일관

## 검증 기준 (스펙에서 재확인)

- 직번+전화번호 정확히 일치할 때만 로그인 성공 (Task 2 테스트)
- 비활성 회원은 정확한 정보로도 로그인 실패 (Task 2, 4 테스트)
- 전화번호 평문이 DB에 저장되지 않음 (Task 4 테스트에서 해시값이 원문과 다름을 확인)
- QR 스캔과 수동 입력이 동일 결과 (Task 6, 7 — 같은 `AuthRepository.login` 호출)
- 앱 재시작 시 로그인/동의 화면 건너뛰고 체크인 화면 진입 (Task 8)
- 기존 체크인/이력 조회가 회귀 없이 동작 (Task 1~3 이후 전체 백엔드 테스트 그린으로 확인)
