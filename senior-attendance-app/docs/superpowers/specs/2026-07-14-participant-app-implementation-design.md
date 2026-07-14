# 참여자용 모바일 프론트 및 백엔드 구현 — 설계

## 배경 및 목표

참여자용 Flutter 화면(`phone_login`, `otp_verify`, `unit_selection`, `job_search`, `consent`, `checkin`)은 이미 기능 단위로 구현되어 있고, 참여자용 백엔드 API(`member-auth`, `members/me`, `places`, `attend/check-in`)도 대부분 갖춰져 있다. 담당자(관리자)용 페이지는 별도로 진행 중이며 이 문서의 범위가 아니다.

이 문서는 두 가지를 다룬다.

1. `docs/superpowers/specs/2026-07-14-atm-style-app-redesign-design.md`에서 승인된 ATM 스타일 디자인을 실제 Flutter 위젯 코드로 구현
2. 구현 과정에서 발견된 백엔드 공백 — 체크인 화면이 `scheduleId`를 `1`로 하드코딩하고 있어, 로그인한 회원의 "오늘 일정"을 조회하는 API를 신규로 추가해야 함

## 범위

포함:
- `mobile/lib/design_system/` 신규 공용 위젯 5종 구현
- 6개 화면을 공용 위젯 기반으로 리팩토링(디자인 스펙의 와이어프레임대로)
- 백엔드 `GET /api/v1/attend/today` 신규 엔드포인트
- 위 변경에 대한 프론트/백엔드 테스트 추가·갱신

제외:
- 담당자(관리자)용 화면/API — 별도 진행 중
- 신규 도메인/스키마 변경 (기존 `Attend`/`Schedule`/`Member` 테이블 그대로 사용)
- 출석 이력 조회, 로그아웃 등 추가 참여자 기능
- `senior-job-search-kiosk-planning.md`가 다루는 검색 로직 변경

## 프론트엔드 아키텍처 — 공용 위젯

`mobile/lib/design_system/`에 5개 위젯을 추가한다. 각 위젯은 상태나 API 호출 로직 없이 콜백으로만 화면과 통신하는 순수 표현 컴포넌트다.

| 위젯 | 파일 | 역할 | 주요 파라미터 |
|---|---|---|---|
| `AtmColors` | `atm_colors.dart` | 색상 상수 (그린 `#2E9E4F`, 오렌지 `#F5821F`, 배경 `#F7F7F7`) | - |
| `AtmPrimaryButton` | `atm_primary_button.dart` | 그린 진행 버튼 | `label`, `onPressed` |
| `AtmSecondaryButton` | `atm_secondary_button.dart` | 오렌지 취소/이전 버튼 | `label`, `onPressed` |
| `AtmBottomActionBar` | `atm_bottom_action_bar.dart` | 하단 고정바. `.single(label, onPressed)`(취소/이전) 또는 `.confirm(onYes, onNo)`(네/아니오) | 생성자 2종 |
| `AtmOptionListItem` | `atm_option_list_item.dart` | 그린 배경 목록 항목(제목 + 선택적 부제목) | `title`, `subtitle`, `onTap` |
| `AtmNumericKeypad` | `atm_numeric_keypad.dart` | 0~9 + 지우기 + 확인 커스텀 키패드 | `onDigit(String)`, `onBackspace()`, `confirmLabel`, `onConfirm()` |

## 화면별 통합 매핑

| 화면 | 변경 내용 |
|---|---|
| `phone_login` | `TextField`+`ElevatedButton` → 입력 표시 박스 + `AtmNumericKeypad` + `AtmBottomActionBar.single('취소')` |
| `otp_verify` | 6칸 OTP 표시 박스 + `AtmNumericKeypad` + `AtmBottomActionBar.single('이전')` |
| `unit_selection` | `ElevatedButton` 목록 → `AtmOptionListItem` 3개 + `AtmBottomActionBar.single('이전')`. 질문 문구 22sp 한 줄 고정 |
| `job_search` | 검색창 스타일만 변경, 결과 `ListTile` → `AtmOptionListItem`(title=일자리명, subtitle=장소·설명). 자주 쓰는 단어 버튼 없음. `AtmBottomActionBar.single('이전')` |
| `consent` | 약관 전문+체크박스 → 요약 1~2문장 + "자세히 보기"(전문은 별도 화면) + `AtmBottomActionBar.confirm(네, 아니오)` |
| `checkin` | 버튼 1개 → 확인질문 + 위치/시간 요약 + `AtmBottomActionBar.confirm(네, 아니오)`. "네" 성공 시 결과 화면(✓ 아이콘 + 완료 메시지 + `AtmBottomActionBar.single('확인')`)으로 전환 |

세부 레이아웃(ASCII 와이어프레임)은 `2026-07-14-atm-style-app-redesign-design.md`를 따른다.

## 백엔드 — 오늘 일정 조회 API

**엔드포인트**: `GET /api/v1/attend/today`
**인증**: 기존 `CurrentMemberService` 패턴 재사용(JWT에서 회원 식별)

**서비스 로직** (`AttendService`, `AttendController`가 위임하는 기존 패턴과 동일):

```java
public Optional<Attend> findTodayAttend(Long memberId) {
    LocalDate today = LocalDate.now();
    List<Attend> attends = attendRepository.findByMemberIdAndDateRange(memberId, today, today);
    return attends.stream().findFirst(); // 하루 여러 건이면 첫 건, 통상 1건
}
```

**응답 DTO** (`AttendTodayResponse`):

```json
// 일정 있음
{ "hasSchedule": true, "scheduleId": 12, "placeName": "중앙공원", "startTime": "09:00", "endTime": "13:00", "status": "SCHEDULED" }

// 일정 없음 (200 OK — 정상 상태이지 에러가 아님)
{ "hasSchedule": false }
```

체크인 화면은 이 응답의 `scheduleId`를 실제 체크인 요청(`POST /api/v1/attend/check-in`)에 사용한다. 하드코딩된 `scheduleId: 1`은 제거한다.

## 에러 처리 및 엣지 케이스

| 상황 | 처리 |
|---|---|
| 인증번호 불일치/만료 | 기존 에러 메시지 유지, 입력 박스 아래 빨간 텍스트로 표시(스타일만 변경) |
| 일자리 검색 결과 0건 | 기존과 동일하게 "AI로 더 찾아보기" 버튼 노출 |
| 약관동의에서 "아니오" 선택 | `job_search` 화면으로 되돌아감 |
| 체크인 — 오늘 배정된 일정 없음(`hasSchedule: false`) | "오늘은 예정된 출석이 없습니다" 안내만 표시, 네/아니오 버튼 비활성화 |
| 체크인 — 위치 반경 밖 | 기존 `AttendService`가 던지는 메시지 그대로("출석 가능한 위치가 아닙니다. 거리: OOOm") |
| 체크인 — 이미 출석 완료 | 기존 메시지("이미 출석 처리되었습니다") 그대로, 결과 화면에 안내 아이콘으로 표시 |
| 위치 권한 거부 | 기존 메시지 유지 |
| 네트워크/서버 오류 전반 | 화면 내 빨간 텍스트로 노출. `AtmBottomActionBar`는 항상 표시되어 사용자가 갇히지 않음 |

기존 에러 메시지 문구·판단 로직은 그대로 재사용하고, 표시 위치/스타일만 새 디자인에 맞춘다.

## 테스트 전략

**프론트엔드**
- `design_system` 위젯 5종 각각 독립 위젯 테스트: `AtmPrimaryButton`/`AtmSecondaryButton` 탭 콜백, `AtmNumericKeypad` 숫자/지우기/확인 입력, `AtmBottomActionBar`의 단일/분할 변형, `AtmOptionListItem` 탭 콜백
- 기존 6개 화면 위젯 테스트는 새 위젯 트리 기준으로 갱신(테스트 의도는 유지, 파인더만 교체)
- `checkin` 테스트에 "오늘 일정 없음" 케이스(버튼 비활성화) 추가

**백엔드**
- `AttendServiceTest`(기존 파일)에 `findTodayAttend()` 케이스 추가: 있음/없음/복수 존재 시 첫 건 반환
- `AttendControllerIntegrationTest`(기존 파일)에 `GET /api/v1/attend/today` 케이스 추가: 정상 응답, 일정 없음, 미인증 거부

## 성공 기준

- 6개 화면이 모두 `design_system` 공용 위젯을 사용하고, 색상/모양이 승인된 디자인 스펙과 일치한다.
- 체크인 화면이 하드코딩 없이 실제 오늘 일정을 조회해서 체크인한다.
- 오늘 일정이 없는 회원은 명확한 안내를 받고 비활성화된 버튼을 본다.
- 기존 프론트/백엔드 테스트가 새 구조에서도 모두 통과한다.

## 다음 단계

이 스펙을 바탕으로 구현 계획(writing-plans)을 작성한다.
