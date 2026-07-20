# 회원 로그인을 직번+전화번호(QR) 방식으로 전환 — 설계 (짧은 형식, constitution.md 준수)

## 배경

기존 회원(참여자) 로그인은 전화번호+SMS OTP였다. 모바일 실사용자 테스트를 위해 CoolSMS 실연동이 필요했는데, 검토 과정에서 "전화번호를 로그인 아이디로 노출하는 것 자체가 위치정보 수집보다 개인정보 유출 위험이 크다"는 문제가 제기되어 로그인 방식 자체를 재설계했다.

## 이번 범위

- 회원 로그인을 **직번(employeeId, 자동 채번) + 전화번호** 방식으로 전환. 전화번호는 서버에 해시로만 저장.
- 관리자(admin-web)가 회원을 등록하며 사업단/일자리(장소)까지 지정. 등록 시점에만 평문 전화번호를 받아 QR(직번+전화번호)을 생성해 화면에 표시 — 이 응답 이후 평문은 어디에도 저장되지 않는다.
- 관리자가 회원을 비활성화하면 해당 직번으로 로그인이 즉시 불가능해진다.
- 모바일 로그인 화면에 QR 스캔 로그인 + 직번/전화번호 수동 입력을 함께 제공.
- 로그인 상태를 기기에 영구 저장해, 로그아웃 전까지는 앱 재실행 시 로그인·동의 화면을 건너뛰고 체크인 화면으로 바로 진입.
- 관리자가 등록 시 사업단/일자리를 지정하므로, 회원이 직접 고르던 사업단 선택·일자리 검색 화면은 더 이상 필요 없어 제거한다.

## 제외 범위

- SMS 인프라 자체(CoolSMS `SmsService`)는 유지한다 — 보호자 알림(출결 알림) 용도로는 계속 쓰이므로, 로그인용 OTP 발송 코드만 제거한다.
- 회원 등록/비활성화 화면 외의 회원 정보 수정(이름 변경, 장소 재배정 등)은 이번 범위에 넣지 않는다. 필요해지면 별도 작업.
- 직번 형식 변경(문자+숫자 조합 등)이나 QR 재발급 이력 관리는 다루지 않는다 — 분실 시 관리자가 전화번호를 다시 입력해 새 QR을 생성하는 것으로 충분하다.
- 모바일 카메라 권한 UX 세부 처리(권한 거부 시 안내 문구 등)는 최소 수준으로만 다룬다.
- 기존 전화번호 기반 Member 데이터 마이그레이션은 다루지 않는다 — 아직 실사용자가 없는 사전 테스트 단계이므로, 기존 방식으로 만들어진 회원 데이터는 그냥 폐기하고 새 스키마로 시작한다.

## 데이터 모델 (Member)

| 필드 | 변경 |
|---|---|
| `phoneNumber` | 제거 |
| `phoneNumberHash` | 신규, `String`, bcrypt(`PasswordEncoder` 재사용) |
| `employeeId` | 신규, `Long`, unique, not null, DB 시퀀스로 자동 채번 |
| `assignedPlaceId` | 기존 필드 유지, 등록 시 필수값으로 전환 |
| `active` | 신규, `boolean`, 기본 `true` |
| `guardianPhone`, `unit`(embedded), `locationConsentAgreedAt` | 그대로 유지 (`unit`은 더 이상 채워지지 않지만 스키마만 유지, 별도 정리는 범위 밖) |

`MemberOtpCode` 엔티티·테이블·리포지토리·`MemberOtpService`는 삭제한다.

## 주요 계약

**`POST /api/v1/member-auth/login`**
`{ employeeId, phoneNumber }` → employeeId로 Member 조회 → `active=false`면 실패 → 전화번호를 저장된 해시와 bcrypt 비교 → 성공 시 `accessToken`(subject=employeeId 문자열, `ROLE_MEMBER`) 발급 + `refreshToken` 쿠키(`/api/v1/member-auth/refresh` path, 기존 `MemberAuthController`의 쿠키 설정 그대로 재사용). 실패(직번 없음/전화번호 불일치/비활성) 시 전부 동일하게 `401 { "error": "직번 또는 전화번호가 올바르지 않습니다" }` — 비활성 여부를 별도로 노출하지 않는다.

`/api/v1/member-auth/otp/request`, `/api/v1/member-auth/otp/verify`는 삭제한다. `/refresh`는 유지하되 내부적으로 subject를 employeeId로 해석하도록 수정.

**`CurrentMemberService`**: `SecurityContextHolder`의 인증 주체(employeeId 문자열)로 `MemberRepository.findByEmployeeId(...)` 조회하도록 변경 (기존 `findByPhoneNumber` 대체).

**신규 `POST /api/admin/members`**
`{ name, phoneNumber, placeId }` → Member 생성(`phoneNumberHash` 계산, `employeeId` 자동 채번, `assignedPlaceId=placeId`, `active=true`) → 응답 `{ employeeId, name, placeId, qrPayload }`. `qrPayload`는 이 응답에 한해서만 평문 전화번호를 포함하는 문자열(`employeeId:phoneNumber`) — 서버에 저장하지 않고 admin-web이 QR 이미지로 렌더링하는 데만 쓴다.

**신규 `GET /api/admin/members`**
전체 회원 목록: `[{ employeeId, name, placeId, placeName, active }]`. admin-web 회원 목록 화면용.

**신규 `PATCH /api/admin/members/{employeeId}`**
`{ active: boolean }` → 활성/비활성 토글만 지원. 다른 필드 변경은 이번 범위 밖.

## admin-web

**"회원 관리" 화면 1개** (사이드바에 진입 링크 추가):
- 등록 폼: 이름 입력 + 전화번호 입력 + 장소 선택(`GET /api/admin/places` 재사용) → 등록 버튼 → 성공 시 QR 코드 표시(다운로드 버튼)
- 회원 목록 테이블: 이름/직번/장소/활성여부 + 행마다 활성↔비활성 토글 버튼

## mobile

- `phone_login_screen.dart` + `otp_verify_screen.dart` → 단일 로그인 화면(직번+전화번호 입력 폼, "QR로 로그인" 버튼)으로 통합. QR 스캔은 카메라로 읽은 문자열(`employeeId:phoneNumber`)을 파싱해 동일 로그인 API 호출. QR 스캔 패키지 신규 추가 필요(`mobile_scanner` 등, 구현 계획 단계에서 확정).
- `unit_selection_screen.dart`, `job_search_screen.dart`, `job_repository.dart`, `core/unit_type.dart`의 회원용 사용처 삭제.
- `AuthGate`(`main.dart`): 로그인 여부 + 위치동의 여부를 `flutter_secure_storage`에서 읽어 로그인 화면 / 동의 화면 / 체크인 화면 중 하나로 분기. 로그인 성공 시 이 상태를 저장, 로그아웃 시에만 초기화.

## 데이터 흐름 / 상태 변화

```
[관리자] 이름+전화번호+장소 입력 → POST /api/admin/members
  → Member 생성(해시 저장, employeeId 채번) → QR 표시(이 순간만 평문 노출)

[회원] QR 스캔 또는 직번+전화번호 수동 입력 → POST /api/v1/member-auth/login
  → 실패(직번 없음/불일치/비활성) → 401
  → 성공 → accessToken + refreshToken 쿠키, 기기에 로그인 상태 영구 저장

[앱 재실행 시]
  → 저장된 로그인 상태 확인
    → 없음 → 로그인 화면
    → 있음, 위치동의 안 함 → 동의 화면
    → 있음, 위치동의 함 → 체크인 화면 (사업단 선택/일자리 검색 단계 없음)

[관리자] 회원 비활성화 → PATCH /api/admin/members/{employeeId} { active: false }
  → 이후 해당 직번 로그인 시도는 401
```

## 에러 처리

| 상황 | 응답 |
|---|---|
| employeeId 없음 | 401 (일치 실패와 동일 메시지) |
| 전화번호 불일치 | 401 (동일 메시지) |
| `active=false` | 401 (동일 메시지, 비활성 사유 비노출) |
| QR 파싱 실패(형식 오류) | 모바일 로컬에서 처리, API 호출 없이 "QR을 인식하지 못했습니다. 다시 시도하거나 직접 입력해주세요" |
| 관리자 등록 시 장소 미선택 | 400 (필수값 누락) |
| 관리자가 아닌 토큰으로 `/api/admin/members/**` 접근 | 403 (기존 `hasRole("ADMIN")` 매처 재사용) |

## 검증 기준

- 직번+전화번호가 정확히 일치할 때만 로그인 성공하는가 (직번만 맞고 전화번호 틀리면 실패)
- 비활성화된 회원은 정확한 직번+전화번호를 입력해도 로그인 실패하는가
- 전화번호 평문이 DB 어디에도 저장되지 않는가 (회원 등록 API 응답 바디 제외)
- QR 스캔 로그인과 수동 입력 로그인이 동일한 결과(같은 토큰 발급 경로)를 내는가
- 앱을 강제 종료 후 재실행했을 때 로그인 화면을 거치지 않고 체크인 화면으로 바로 가는가 (동의 여부에 따라 분기 포함)
- 로그아웃 후에는 다시 로그인 화면부터 시작하는가
- 기존 체크인/결석/이력 조회(`AttendService`)가 `assignedPlaceId` 기반으로 그대로 동작하는가 (회귀 확인)
