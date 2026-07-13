# 실제 호출 경로 기준 테스트 커버리지 확대 — 설계 (정답지)

## 배경 및 목표

현재 백엔드/모바일 모두 핵심 동작 경로에 테스트가 없는 곳이 남아 있고, 반대로 어디서도 호출되지 않는 죽은 코드에는 테스트가 붙어 있어 커버리지 숫자와 실질 검증 가치가 어긋나 있었다. 이 스펙은 "실제로 호출되는 경로"만을 기준으로 커버리지를 높이기 위한 정답지(기대 동작 명세)를 정의하고, 이를 사용자가 검증한 뒤 TDD로 구현하기 위한 근거 문서다.

## 사전 조사 결과 및 정리

### 죽은 코드 제거 (완료)

- `ScheduleService`, `ScheduleServiceAutoAttendTest`, `dto/schedule/ScheduleCreateRequest`, `ScheduleCreateResponse`, `ScheduleDetailResponse`, `repository/ScheduleRepository`를 삭제했다.
- 근거: `ScheduleController`가 커밋 이력 전체에 걸쳐 한 번도 존재한 적이 없고, `@Scheduled`/`CommandLineRunner` 등 어떤 진입점도 `ScheduleService`를 호출하지 않았다. 유일한 참조는 자기 자신의 단위 테스트뿐이었다.
- `domain/Schedule.java`는 `Attend`/`AttendService`가 실제로 사용하므로 유지한다.
- 삭제 후 `./gradlew test` 전체 통과 확인함 (BUILD SUCCESSFUL).

### 범위에서 제외한 항목 (별도 이슈로 기록)

- `AttendService.markAbsent` / `markExcused` / `updateAttendStatus`: 어떤 컨트롤러에서도 호출되지 않는다. 다만 `ScheduleService`와 달리 `Attend` 도메인 메서드·`AttendStatus` enum과 확실히 연결된, 관리자용 결석 처리 기능이 컨트롤러만 아직 없는 상태로 보인다. 삭제하지 않고 이번 커버리지 확대 대상에서도 제외한다. 추후 관리자 API가 붙을 때 별도 스펙/테스트로 다룬다.

## 범위

**포함**
1. 백엔드: `AttendService.checkIn()` (지각 유예시간 신규 로직 포함), `PlaceController` 인증된 성공 경로, `LlmJobSearchClient` 실 HTTP 호출/파싱/에러 처리
2. 모바일: `PhoneLoginScreen`, `OtpVerifyScreen`, `UnitSelectionScreen`, `JobSearchScreen`, `ConsentScreen`, `CheckinScreen` 위젯 테스트

**제외**
- `ScheduleService` (삭제됨)
- `AttendService`의 관리자용 상태 변경 메서드 (위 사유로 보류)
- 코드 커버리지 측정 도구(JaCoCo 등) 도입 — 별도 요청 시 진행

## 신규 기능: 지각 판정 유예 시간

**현재 동작**: `AttendService.isLate(Schedule)`은 현재 시각이 `schedule.getStartTime()`보다 조금이라도 늦으면 무조건 지각으로 판정한다.

**변경 사항**: `attendance.location.radius`와 동일한 패턴으로 환경변수 오버라이드 가능한 설정값 `attendance.late.grace-minutes`(기본값 **10**)을 추가한다. 시작 시간부터 유예 시간 이내에 체크인하면 정시 출석(PRESENT)으로, 유예 시간을 초과하면 지각(LATE)으로 처리한다.

```
now.isAfter(startTime.plusMinutes(graceMinutes))  // true면 지각
```

## 정답지: 백엔드

### 1. 위치기반 출석 체크 — `AttendService.checkIn()` (신규 `AttendServiceTest.java`, Mockito)

| # | 고객 시나리오 | 기술 조건 (Given) | 기대 결과 (Then) |
|---|---|---|---|
| T1 | 어르신이 출근 시간 전에 근무지 500m 이내에서 "출석 체크"를 누른다 | Attend=SCHEDULED, 근무 시작시간 아직 안 됨, 위치 반경 이내 | "출석 처리되었습니다" 표시, status=PRESENT, isLate=false |
| T2a | 어르신이 시작시간을 조금 지나서(유예시간 10분 이내) 체크한다 | 시작시간은 지났지만 10분 이내, 위치 반경 이내 | 지각 아닌 정시 출석(PRESENT)으로 처리 |
| T2b | 어르신이 유예시간 10분까지 넘겨서 체크한다 | 시작시간+10분을 초과, 위치 반경 이내 | "출석 처리되었습니다 (지각)", status=LATE, isLate=true, note="지각" |
| T3 | 어르신이 근무지에서 500m보다 멀리 떨어진 곳에서 체크를 시도한다 | 위치가 반경 밖 | `IllegalStateException`(409), 출석 기록은 바뀌지 않음, save 호출 안 됨 |
| T4 | 이미 출석 체크를 마친 어르신이 실수로 버튼을 한 번 더 누른다 | Attend가 이미 PRESENT/LATE | 예외 없이 success=false, "이미 출석 처리되었습니다", 기존 status 유지, save 호출 안 됨 |
| T5 | 오늘 일정에 등록되지 않은 사람이 체크를 시도한다 | 해당 scheduleId+memberId 조합의 Attend가 없음 | `ResourceNotFoundException`(404) |
| T6 | 출석은 정상 처리됐는데 알림 문자가 통신 장애로 발송 실패한다 | smsService가 예외를 던짐 | 예외가 전파되지 않고 success=true로 정상 반환 |

### 2. 사업단 유형별 일자리 목록/검색 — `PlaceController` (기존 `PlaceControllerIntegrationTest`에 케이스 추가)

| # | 고객 시나리오 | 기술 조건 | 기대 결과 |
|---|---|---|---|
| T7 | 참여자가 "공익형"을 고르면 공익형 일자리 목록만 뜬다 | 인증 O, `unitType=PUBLIC_INTEREST` | 200, 해당 unitType 장소만 반환 |
| T8 | 참여자가 "청소"라고 검색하면 이름/설명/동의어에 "청소"가 들어간 일자리만 뜬다 | 인증 O, `unitType=X&q=청소` | 200, 매칭되는 결과만 반환 |

### 3. AI 보조 검색 — `LlmJobSearchClient` (신규 `LlmJobSearchClientTest.java`)

**선행 리팩터링 (승인됨)**: 내부에서 직접 생성하던 `RestTemplate`을 생성자 주입으로 변경한다. 동작 변화 없이 테스트 가능성만 개선한다.

| # | 고객 시나리오 | 기술 조건 | 기대 결과 |
|---|---|---|---|
| T9 | 검색할 후보 자체가 하나도 없는 사업단 유형 | candidates=[] | AI 호출 없이 바로 결과 없음 (`Optional.empty()`) |
| T10 | 어르신이 "학교 앞에서 깃발 흔드는 일"이라고 설명 → AI가 알맞은 일자리를 찾아줌 | mock 응답 `{"content":[{"text":"7"}]}` | `Optional.of(7L)` |
| T11 | AI도 확신 못 하는 애매한 설명 | mock 응답 text="0" | `Optional.empty()` |
| T12 | AI 서버 자체가 응답 없음/장애 | RestTemplate 호출이 예외 발생 | catch되어 `Optional.empty()`, 예외 전파 안 됨 |
| T13 | AI가 예상 못한 형식으로 응답 | 응답 JSON 구조가 다름 | catch되어 `Optional.empty()` |

## 정답지: 모바일 (Flutter 위젯 테스트)

기존 관례(`job_repository_test.dart`: `Dio()` + 가짜 `HttpClientAdapter`)를 확장 — `apiClientProvider`를 오버라이드해 `ApiClient` 내부 `dio.httpClientAdapter`와 `tokenStorage`를 교체하는 방식. `CheckinScreen`은 추가로 `GeolocatorPlatform.instance`를 가짜 구현으로 교체한다.

| 화면 | 고객 시나리오 | 기대 결과 |
|---|---|---|
| PhoneLoginScreen | 전화번호 입력 후 "인증번호 받기" 누름 | 인증번호 요청 API 호출됨, 인증번호 입력 화면으로 이동 |
| PhoneLoginScreen | 요청 처리 중 다시 누름 | 버튼이 "전송 중..."으로 바뀌고 비활성화 |
| OtpVerifyScreen | 정확한 인증번호 입력 후 확인 | 사업단 유형 선택 화면으로 이동 |
| OtpVerifyScreen | 틀린 인증번호 입력 후 확인 | "인증번호가 올바르지 않습니다" 표시, 화면 유지 |
| UnitSelectionScreen | 유형 하나 탭 | 해당 unitType의 일자리 검색 화면으로 이동 |
| JobSearchScreen | 화면 진입 시 | 해당 유형 일자리 목록 자동 로드 |
| JobSearchScreen | 목록 로드 실패 | "일자리 목록을 불러오지 못했습니다" 안내 |
| JobSearchScreen | 검색어 입력 후 검색, 0건 | 결과 갱신 + "AI로 더 찾아보기" 버튼 노출 |
| JobSearchScreen | "AI로 더 찾아보기" 탭 | AI 폴백 검색 결과로 목록 갱신 |
| JobSearchScreen | 목록에서 항목 탭 | 위치정보 동의 화면으로 이동 |
| ConsentScreen | 미동의 상태 | "동의하고 계속하기" 버튼 비활성화 |
| ConsentScreen | 동의 체크 후 제출 성공 | 출석 체크 화면으로 이동 |
| ConsentScreen | 제출 중 서버 오류 | 에러 안내, 화면 유지 |
| CheckinScreen | 위치 권한 거부 | "위치 권한이 필요합니다" 안내, 서버 호출 안 함 |
| CheckinScreen | 위치 확인 + 체크인 성공 | 서버 응답 메시지 그대로 노출 |
| CheckinScreen | 위치 서비스 꺼짐/체크인 실패 | "위치 확인에 실패했습니다" 안내 |

## 검증 절차

각 항목을 TDD로 진행한다: 실패하는 테스트 작성 → 실행해서 실패 확인 → 구현 → 통과 확인 → 커밋. 전체 항목 완료 후 `./gradlew test`(백엔드 전체) 및 `flutter test`(모바일 전체)를 재실행해 회귀가 없음을 확인한다.

## Out of scope / 후속 이슈

- `AttendService.markAbsent/markExcused/updateAttendStatus`를 노출할 관리자용 컨트롤러 — 별도 스펙 필요
- 코드 커버리지 수치화 도구(JaCoCo, `flutter test --coverage`) 도입 여부
