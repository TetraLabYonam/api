# 프로젝트 분할: 시니어 근태관리 / 번호표 큐 — 설계

## 배경 및 목표

현재 저장소 하나에 서로 다른 두 시스템이 섞여 있다.

1. **시니어 근태관리** — 노인 일자리 참여자의 출석을 관리한다. 관리자(선생님)는 공익형/시장형/사회서비스형 3개 사업단을 맡는다. 백엔드(`src/`)와 Flutter 참여자 앱(`mobile/`)이 최근 세션에서 완성되어 정상 작동한다.
2. **번호표 큐** — 방문객이 번호표를 뽑고 관리자가 순번을 호출하는 시스템. Room/TicketIssuance 엔티티와 서비스 계층(`RoomService`, `QueueService`, `TicketService`)은 남아있지만, 이를 노출하던 컨트롤러 전체(`RoomController`, `QueueController` 등)가 2025년 12월 커밋(`9cc1533`, "컨트롤러 재구조화")에서 삭제되었다. React 프론트엔드(`frontend/`)는 그 사실을 모른 채 지금도 삭제된 엔드포인트 10곳을 호출하고 있어 사실상 전부 작동하지 않는다.

이 설계는 두 시스템을 저장소 안에서 물리적으로 분리하는 것을 다룬다. **번호표 백엔드를 실제로 재구현하거나 두 시스템의 신규 모바일 앱을 만드는 작업은 이번 범위에 포함하지 않는다** — 그 작업들은 이 분할이 끝난 뒤 각자의 폴더 안에서 별도 계획으로 진행한다.

## 목표 아키텍처

두 시스템은 최종적으로 완전히 다른 클라이언트 형태를 갖는다.

| | 관리자 화면 | 회원(사용자) 화면 |
|---|---|---|
| 시니어 근태관리 | 웹앱 (신규 개발 예정) | Flutter 모바일 (기존, 작동함) |
| 번호표 큐 | 모바일 (신규 개발 예정) | 모바일 (신규 개발 예정) |

## 범위

이번 설계가 다루는 것:
1. 저장소 폴더 구조 분리 (`senior-attendance-app/`, `queue-app/`)
2. 기존 코드를 어디로 옮기고 무엇을 버릴지 인벤토리
3. 두 시스템의 관리자 인증을 독립적으로 분리
4. `queue-app`의 신규 데이터 모델 설계 (스키마 문서화까지 — 구현은 제외)
5. `queue-app`의 DB 엔진 선택

이번 설계가 다루지 않는 것 (별도 계획 필요):
- `queue-app` 백엔드 컨트롤러 실제 구현
- `queue-app` 관리자/회원 모바일 앱 신규 개발
- `senior-attendance-app` 관리자 웹앱 신규 개발
- CI, Docker, 루트 `README.md` 등 메타 파일 정리

## 저장소 구조

한 저장소 안에 최상위 폴더 2개로 나눈다 (완전히 별도 저장소로 쪼개지 않음). 이유: git 히스토리를 유지하면서, 나중에 정말 독립 배포가 필요해지면 폴더 단위로 쉽게 새 저장소를 뽑아낼 수 있다.

```
senior/  (저장소 루트)
├── senior-attendance-app/
│   ├── backend/     ← 기존 src/ 중 시니어 근태관리 관련 코드
│   ├── mobile/      ← 기존 mobile/ 그대로
│   └── docs/        ← 기존 docs/superpowers/ (지난 세션 스펙/플랜)
├── queue-app/
│   ├── backend/         ← Room/TicketIssuance 등 참고, 스키마는 새로 설계된 것으로 교체 (미구현)
│   ├── admin-mobile/    (플레이스홀더 — 신규 개발은 별도 계획)
│   └── member-mobile/   (플레이스홀더 — 신규 개발은 별도 계획)
```

`frontend/`(React)는 두 폴더 어디에도 포함하지 않고 저장소에서 삭제한다. 이유: 시니어 근태관리 관리자 웹은 아직 없으므로 이 코드는 재사용 대상이 아니고(신규 개발 예정), 번호표 쪽은 애초에 모바일 전용으로 갈 것이므로 React 웹 UI 자체가 목표 아키텍처와 맞지 않는다. 필요하면 git 히스토리에서 언제든 다시 꺼내볼 수 있다.

## 이관 인벤토리

| 항목 | 처리 | 비고 |
|---|---|---|
| `Place`, `Schedule`, `Attend`, `AttendStatus`, `Member`, `MemberOtpCode`, `UnitType`, `JobKeywordSynonym`, `Unit` | → `senior-attendance-app/backend/` | 정상 작동 중 |
| `PlaceController`, `MemberAuthController`, `MemberSelfController`, `AttendController` | → `senior-attendance-app/backend/` | 정상 작동 중 |
| `PlaceSearchService`, `LlmJobSearchClient`, `MemberOtpService`, `CurrentMemberService`, `ScheduleService`, `AttendService` | → `senior-attendance-app/backend/` | 정상 작동 중 |
| `SmsService`, `DeviceController` | → `senior-attendance-app/backend/` | OTP 발송에 사용 |
| `mobile/` (Flutter 전체) | → `senior-attendance-app/mobile/` | 정상 작동 중 |
| `docs/superpowers/` (지난 세션 스펙·플랜·리뷰 산출물) | → `senior-attendance-app/docs/` | 시니어 근태관리 전용 문서 |
| `Room`, `TicketIssuance`, `RoomService`, `QueueService`, `TicketService`, `WebSocketService`, `RoomRepository`, `TicketIssuanceRepository`, 관련 DTO | → `queue-app/backend/` (참고용) | 아래 신규 스키마로 교체 예정, 컨트롤러는 애초에 없음 |
| `frontend/` 전체 (React) | 저장소에서 삭제 | 두 시스템 다 목표 클라이언트가 아님 |
| `MemberService`, `PlaceService`, `PlaceCrawlingService`, `ExcelService` (삭제된 구버전 컨트롤러가 쓰던 서비스 계층) | 버림 (이관 안 함) | 신규 서비스(`CurrentMemberService`/`PlaceSearchService` 등)가 실질적으로 대체 |
| `Admin`, `AuthController`, `RefreshToken`, `RefreshTokenService`/`Impl`, `RefreshTokenCleanupJob`, `SecurityConfig`, `JwtTokenProvider`, `JwtAuthenticationFilter` | 양쪽에 **각각 독립적으로 복제** | 아래 "관리자 인증 분리" 참고 |

## 관리자 인증 분리

두 시스템의 "관리자"는 역할이 다르다 — 근태관리 선생님은 사업단을 담당하는 고정 역할이지만, 번호표 방을 만드는 관리자는 그때그때 다른 사람일 가능성이 높고 역할 자체가 일시적이다. 두 시스템을 독립 배포하는 것이 목표이므로, 관리자 인증(Admin 엔티티, JWT 발급/검증, RefreshToken, SecurityConfig)을 하나의 인증 서버로 공유(SSO)하지 않고 **각 앱이 완전히 별개의 서비스로 복제**해서 가진다. 한쪽의 보안 이슈나 스키마 변경이 다른 쪽에 영향을 주지 않는다.

## queue-app 신규 데이터 모델 (설계만, 미구현)

기존 `Room`/`TicketIssuance`는 "사업단을 고르고 번호표를 뽑는" 단순 대기열 모델이었지만, 실제 업무는 다르다: 3개 사업단에 각각 미배정 일자리가 여러 개 있고, 번호표 방은 그 일자리 하나하나에 대한 접수 창구다. 관리자가 방을 만들면 그 관리자가 방의 소유자가 되고, 어르신이 그 일자리(방)를 선택해 들어가는 순간 방 소유자가 그 어르신의 접수 기록에 그대로 남는다. 방은 일시적이라 언제든 닫히고 삭제될 수 있으므로, 접수 이력은 방을 FK로 참조하지 않고 그 시점의 값을 그대로 복사해서 영구 보존한다.

```
TicketRoom (방 — 삭제 가능)
- id
- roomUid
- unitType (varchar)       -- 이 방이 속한 사업단
- createdByAdmin (varchar) -- 방을 만든 관리자
- createdAt
- closedAt                 -- null이면 열린 상태

TicketDrawRecord (접수 이력 — 영구 보존, 방 삭제와 무관)
- id
- phoneLastDigits (varchar) -- 어르신 전화번호 뒷자리
- unitType (varchar)        -- 접수 시점 사업단 스냅샷
- ticketNumber (Long)
- adminName (varchar)       -- 접수 시점 방 소유 관리자 스냅샷
- drawnAt
```

`TicketDrawRecord`가 `TicketRoom`을 FK로 참조하지 않는 것이 핵심이다 — 방이 삭제되어도 "누가 언제 몇 번을 받았고 어느 사업단/어느 관리자였는지"는 영구히 조회 가능해야 하기 때문이다.

## queue-app DB 엔진: PostgreSQL

`senior-attendance-app`은 기존 MariaDB를 그대로 유지한다 (이미 정상 작동 중이라 바꿀 이유가 없다). `queue-app`은 백엔드를 사실상 새로 만드는 것이므로 PostgreSQL을 채택한다.

- **대소문자 문제 회피**: MariaDB는 `lower_case_table_names=0` 환경(리눅스 기본값)에서 테이블명이 대소문자를 구분해 파일시스템 파일명과 매핑된다. 지난 세션에서 마이그레이션 파일에 소문자 테이블명을 썼다가 리뷰에서 반복적으로 잡힌 버그가 정확히 이 문제였다 (V3/V4 마이그레이션 사례). PostgreSQL은 따옴표 없는 식별자를 전부 소문자로 정규화해서 이 문제 자체가 발생하지 않는다.
- **나머지 기술적 차이는 이 프로젝트 규모에서 실질적 장벽이 아니다**: `@GeneratedValue(strategy = IDENTITY)`는 두 엔진 모두 문제없이 동작하고, 이 프로젝트는 이미 전부 `@Enumerated(EnumType.STRING)` + `VARCHAR` 패턴을 쓰고 있어 PostgreSQL 고유 ENUM 타입도 필요 없다. `spring.datasource.url`/드라이버 교체와 Flyway 마이그레이션 문법 조정 정도면 충분하다.

## 검증 계획

이번 작업은 코드 이동이 중심이므로, "옮긴 뒤에도 그대로 작동하는가"를 확인하는 것이 핵심 검증이다.

1. `senior-attendance-app/backend/`로 이동 후 `./gradlew test` 전체 통과 확인 (이동 전 마지막 상태: 백엔드 36개 테스트 통과)
2. `senior-attendance-app/mobile/`로 이동 후 `flutter test` + `dart analyze lib/` 클린 확인 (이동 전 마지막 상태: 8개 테스트 통과, analyze 클린)
3. `queue-app/backend/`는 이번 범위에서 독립적으로 빌드 가능한 Gradle 프로젝트로 구성하지 않는다 — Room/TicketIssuance 관련 파일을 참고용으로 옮겨두는 것까지만 하고, 실제 프로젝트 스캐폴드(신규 `build.gradle`, PostgreSQL 연결, 컨트롤러 구현)는 "번호표 백엔드 재구현" 별도 계획에서 진행한다. 따라서 이번 검증 대상에서 제외한다
4. 패키지 이동에 따른 import 경로, 빌드 스크립트(`build.gradle`, `settings.gradle`) 경로 참조가 깨지지 않았는지 확인
