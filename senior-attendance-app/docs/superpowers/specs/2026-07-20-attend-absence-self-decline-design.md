# 회원 자기-서비스 결석 처리 — 백엔드 + 모바일 설계

## 배경 및 범위

attend(출석) 기능 점검에서 발견한 두 번째 공백: **결석 처리 경로가 없다.** `AttendService`에 이미 `markAbsent`/`markExcused`/`updateAttendStatus` 메서드가 구현돼 있지만 이를 호출하는 컨트롤러가 하나도 없고, 모바일 체크인 화면(`checkin_screen.dart`)의 "아니오" 버튼(`_declineCheckIn`)도 API 호출 없이 화면만 닫아서 결석이 실제로는 절대 기록되지 않는다.

이번 설계는 **회원이 체크인 화면에서 직접 "아니오"를 눌러 본인 결석을 처리하는 자기-서비스 플로우**만 다룬다. 아래는 이번 범위 밖이며 별도 항목으로 남긴다:

- 관리자가 다른 회원의 출석을 개별 조회하거나 수동으로 결석/사유인정 처리하는 API/화면 (공백 목록 3번)
- 사유 인정(EXCUSED) 처리 — 이번 흐름은 결석(ABSENT)만 다루며, 사유 인정은 관리자 개입이 필요한 별도 워크플로로 간주해 3번 범위로 넘긴다
- 일정 종료 후 자동으로 결석 처리하는 배치 잡 — 이번 설계는 회원의 명시적 액션(버튼)에 의한 결석만 다룬다

## 아키텍처

신규 컨트롤러/서비스 클래스 없이 기존 `AttendController`/`AttendService`에 메서드를 추가한다. 체크인(`check-in`) 흐름과 구조적으로 대칭이다.

- **`AttendController`**: `POST /api/v1/attend/decline` 신규 매핑. `check-in`과 달리 위치정보 동의(`locationConsentAgreedAt`) 검사는 하지 않는다 — 결석 처리에는 위치가 관여하지 않는다.
- **`AttendService.decline(Long scheduleId, Long memberId): AttendDeclineResponse`** (신규 메서드) — 기존 `Attend.markAbsent()` 도메인 메서드를 재사용한다.

### API 계약

**요청** `AttendDeclineApiRequest`:
```
scheduleId: Long   (필수)
```
`memberId`는 체크인과 동일하게 클라이언트가 보내지 않고, 서버가 `CurrentMemberService`로 인증된 회원을 해석한다.

**응답** `AttendDeclineResponse`:
```
attendId: Long
status: AttendStatus
message: String
success: boolean
```

## 데이터 흐름 / 핵심 로직

```
1. scheduleId + 인증된 memberId로 Attend 조회
   → 없으면 404 ResourceNotFoundException
     (메시지 패턴은 checkIn()의 "해당 일정의 출석 정보를 찾을 수 없습니다"와 동일하게)

2. 현재 상태에 따라 분기:
   a. attend.isAttended() (PRESENT 또는 LATE)
      → 상태 변경 없음, success=false, message="이미 출석 처리되었습니다."
   b. attend.isAbsent() (ABSENT 또는 EXCUSED — 이미 결석/사유인정 처리됨)
      → 상태 변경 없음, SMS 재전송 없음(중복 방지), success=true, message="이미 결석 처리되었습니다."
   c. 그 외 (SCHEDULED)
      → attend.markAbsent("회원 자가 결석") 호출 → 저장
      → SMS 알림 전송 시도 (실패해도 결석 처리 자체엔 영향 없음 — 기존 markAbsent/checkIn과 동일한 try/catch 패턴)
      → success=true, message="결석 처리되었습니다."

3. AttendDeclineResponse 반환
```

## 에러 처리

| 상황 | 응답 |
|---|---|
| 인증 없음 | 401 |
| `scheduleId`에 해당하는 본인 Attend 없음 | 404 `ResourceNotFoundException` |
| 이미 출석(PRESENT/LATE) 상태 | 200, `success=false`, message="이미 출석 처리되었습니다." |
| 이미 결석/사유인정(ABSENT/EXCUSED) 상태 | 200, `success=true`(멱등), message="이미 결석 처리되었습니다." |
| 정상 결석 처리(SCHEDULED → ABSENT) | 200, `success=true`, message="결석 처리되었습니다." |
| SMS 전송 실패 | 결석 처리 자체엔 영향 없음 — 로그만 남기고 무시 |

체크인의 위치정보 동의 미완료(409)나 위치 반경 이탈 관련 에러는 이 흐름에 존재하지 않는다.

## 모바일 변경사항 (`mobile/lib/features/checkin/`)

- **`checkin_repository.dart`**: `Future<CheckinResult> decline({required int scheduleId})` 추가 — `checkIn()`과 동일한 방식으로 `POST /api/v1/attend/decline` 호출 후 `CheckinResult`로 파싱 (`success`/`message` 필드는 기존 `CheckinResult` 클래스를 그대로 재사용).
- **`checkin_screen.dart`**:
  - `_declineCheckIn()`을 비동기 메서드로 변경. 기존처럼 화면을 바로 닫지 않고, `CheckinRepository.decline()`을 호출한 뒤 결과를 체크인 성공 화면과 동일한 `_result` 표시 경로로 보여준다 (별도의 결석 전용 UI를 새로 만들지 않고 기존 `_result != null` 분기를 재사용).
  - 중복 탭 방지를 위한 `_declining` 로딩 플래그 추가 (체크인의 `_checkingIn`과 대칭 — 두 플래그가 동시에 true가 되지 않도록 각 버튼은 반대쪽 처리 중엔 비활성화).
  - API 호출 실패(네트워크 오류 등) 시 기존 `_errorMessage` 표시 경로를 재사용 (예: "결석 처리에 실패했습니다. 다시 시도해주세요.").

## 테스트 전략

**백엔드**
- `AttendServiceTest`에 `decline()` 케이스 추가 (기존 파일에 메서드만 추가, 신규 파일 아님):
  - 정상 결석 처리: SCHEDULED 상태 → ABSENT로 변경, SMS 발송 시도 확인
  - 이미 출석(PRESENT/LATE)인 경우: 상태 변경 없이 success=false 반환
  - 이미 결석/사유인정(ABSENT/EXCUSED)인 경우: 상태 변경 없이 success=true 반환, SMS 재전송 안 됨(mock 호출 횟수로 검증)
  - 존재하지 않는 Attend: `ResourceNotFoundException`
  - SMS 전송 실패해도 결석 처리 자체는 성공하는지 (체크인의 `checkIn_smsSendFails_stillReturnsSuccess`와 대칭)
- `AttendControllerIntegrationTest`에 `decline` 관련 테스트 추가:
  - 인증 없이 호출 → 401
  - 본인 소유가 아닌/존재하지 않는 scheduleId → 404
  - 정상 케이스 → 200, DB에서 Attend 상태가 ABSENT로 실제 변경됐는지 확인
  - 이미 출석 처리된 상태에서 호출 → 200, success=false

**모바일**
- `checkin_repository_test.dart`(신규 또는 기존 테스트 파일 확장, 리포지토리 테스트가 이미 있다면 거기에 추가): `decline()`이 올바른 엔드포인트/바디로 요청하고 응답을 파싱하는지
- `checkin_screen_test.dart`: "아니오" 탭 시 실제로 `decline()`이 호출되고 결과 화면이 표시되는지, 실패 시 에러 메시지가 표시되는지 (기존 체크인 성공/실패 테스트와 대칭 케이스로 추가)

## 결정 사항 요약 (브레인스토밍 중 확정)

- 범위: 백엔드 API + 모바일 앱 연결까지 (공백 1번과 달리 이번엔 모바일도 포함)
- 대상: 회원 본인 자기-서비스 결석만. 관리자의 타인 결석/사유인정 처리는 별도 항목(3번)으로 분리
- 사유 입력: 없음 — 버튼 한 번으로 고정 사유("회원 자가 결석")로 즉시 결석 처리
- 이미 출석된 상태에서 결석 시도: 에러 대신 success=false로 안내, 상태는 변경하지 않음
- 이미 결석 처리된 상태에서 중복 호출: 멱등 처리, SMS 재전송 없음
