# 일정(Schedule) 생성 + 출석(Attend) 자동 생성 — 백엔드 설계

## 배경 및 범위

attend(출석) 기능 점검 결과, 가장 치명적인 공백은 **일정(Schedule)을 만들고 그 일정에 배정된 회원들의 최초 출석(Attend, `SCHEDULED`) 레코드를 생성하는 경로가 프로덕션에 전혀 없다**는 점이었다. `Schedule`/`ScheduleRepository`는 존재하지만 이를 다루는 서비스/컨트롤러가 없고, `AttemptApplication`의 데모 시더는 `Attend` 객체를 생성만 하고 저장조차 하지 않는 죽은 코드다. 그 결과 현재 체크인(`/api/v1/attend/check-in`)·오늘 조회(`/api/v1/attend/today`) API는 누군가 DB에 수동으로 넣어둔 레코드에만 의존한다.

이번 설계는 이 공백 중 **"일정 생성 → 배정 회원 전원에게 Attend(SCHEDULED) 자동 생성"** 부분만 다룬다. 아래는 이번 범위 밖이며 별도 설계로 분리한다:

- 결석/사유인정 처리 API (관리자가 개별 회원 출석을 수동으로 변경) — attend 공백 목록의 2번 항목
- 관리자용 개별 출석 조회/관리 화면 및 API — 3번 항목
- 회원 본인 출석 이력 조회 API — 4번 항목
- admin-web에서 관리자가 직접 일정을 등록하는 화면 — 이번엔 백엔드 API까지만 (사용자가 명시적으로 범위를 백엔드로 한정함)

## 아키텍처

신규 컴포넌트 3개, 리포지토리 쿼리 메서드 2개를 추가한다. 기존 `SecurityConfig`의 `/api/admin/**` → `hasRole("ADMIN")` 매처가 신규 엔드포인트에도 그대로 적용되므로 보안 설정 변경은 불필요하다.

- **`ScheduleController`** (신규) — `POST /api/admin/schedules` 단일 엔드포인트.
- **`ScheduleService`** (신규) — 날짜 계산, 중복 스킵, `Schedule`/`Attend` 생성을 하나의 트랜잭션으로 오케스트레이션.
- **`CurrentAdminService`** (신규) — `CurrentMemberService`와 동일한 패턴으로, `SecurityContextHolder`의 인증 주체(username)로 `Admin`을 조회해 `Schedule.createdBy`에 채운다.
- **`ScheduleRepository.existsByPlaceIdAndScheduleDate(Long placeId, LocalDate date)`** (신규) — 중복 판단용.
- **`MemberRepository.findByAssignedPlaceId(Long placeId)`** (신규) — 장소에 배정된 회원 전원 조회용.

### API 계약

**요청** `CreateScheduleRequest`:
```
placeId: Long              (필수)
title: String               (필수)
description: String?        (선택)
startDate: LocalDate         (필수)
endDate: LocalDate?           (선택, 생략 시 startDate와 동일 — 단건 생성)
daysOfWeek: Set<DayOfWeek>?    (선택, startDate==endDate면 무시. 구간이 1일 초과면 필수)
startTime: LocalTime           (필수)
endTime: LocalTime              (필수)
```

`title`/`description`/`startTime`/`endTime`은 이번 요청으로 생성되는 모든 날짜의 Schedule에 동일하게 적용된다 (날짜별로 다른 값을 줄 수 없음).

**응답** `CreateScheduleResponse`:
```
createdDates: List<LocalDate>      // 실제로 Schedule이 생성된 날짜
skippedDates: List<LocalDate>       // 같은 장소에 이미 Schedule이 있어 건너뛴 날짜
attendCreatedCount: int               // 이번 요청으로 생성된 Attend 총 개수
```

## 데이터 흐름 / 핵심 로직

```
1. placeId로 Place 조회 — 없으면 404 (ResourceNotFoundException)
2. 유효성 검증 (위반 시 400 IllegalArgumentException):
   - startDate > endDate
   - startTime >= endTime
   - endDate가 startDate와 다른데 daysOfWeek가 비어있음
   - (startDate ~ endDate) 기간이 최대 허용치(기본 180일, @Value로 조정 가능) 초과
3. 대상 날짜 목록 계산:
   - startDate == endDate → [startDate] (daysOfWeek 무시)
   - 그 외 → startDate~endDate 사이 날짜 중 daysOfWeek에 해당하는 날짜만 필터링
4. MemberRepository.findByAssignedPlaceId(placeId)로 대상 회원 목록을 한 번만 조회해 재사용
5. 각 대상 날짜마다:
   a. ScheduleRepository.existsByPlaceIdAndScheduleDate(placeId, date) == true → skippedDates에 추가, 다음 날짜로
   b. false → Schedule 생성(place, date, startTime, endTime, title, description, createdBy=현재 Admin) 저장
   c. 4에서 조회한 회원 전원에 대해 Attend.builder().member(m).schedule(schedule).status(SCHEDULED).build() 생성 후 일괄 저장
   d. createdDates에 날짜 추가, attendCreatedCount += 이번 날짜에 생성된 Attend 수
6. 전체를 하나의 @Transactional 안에서 처리 (부분 실패 시 전체 롤백)
```

배정된 회원이 0명인 장소도 에러가 아니다 — Schedule은 정상 생성되고 그 날짜의 Attend 생성 수만 0이다.

## 에러 처리

| 상황 | 응답 |
|---|---|
| 인증 없음 | 401 (기존 `/api/admin/**` 공통 처리) |
| MEMBER 토큰으로 호출 | 403 (기존 공통 처리) |
| `placeId`에 해당하는 Place 없음 | 404 `ResourceNotFoundException` |
| `startDate > endDate` | 400 `IllegalArgumentException` |
| `startTime >= endTime` | 400 |
| 다일 구간인데 `daysOfWeek` 비어있음 | 400 |
| 기간이 최대 허용치(기본 180일) 초과 | 400 |
| 대상 날짜가 계산 결과 0건(패턴이 구간과 전혀 안 겹침) | 200, `createdDates`/`skippedDates` 둘 다 빈 배열 — 에러 아님 |
| 모든 대상 날짜가 이미 존재해 전부 스킵됨 | 200, `createdDates` 빈 배열, `skippedDates`에 전부 나열 — 에러 아님 |

새 예외 타입 없이 기존 `GlobalExceptionHandler`의 `IllegalArgumentException`→400, `ResourceNotFoundException`→404 매핑을 그대로 재사용한다.

## 테스트 전략

**`ScheduleServiceTest` (단위, Mock 리포지토리)**
- 단건 생성: `startDate == endDate` → 날짜 1개, `daysOfWeek` 값이 있어도 무시됨
- 반복 생성: 2주 구간 + `[MON, WED]` → 정확히 해당 요일 날짜만 대상
- 중복 스킵: 대상 날짜 중 일부가 이미 존재 → `skippedDates`에 들어가고 `createdDates`에서 빠짐, 그 날짜엔 Attend 생성 안 됨
- 배정 회원 0명인 장소 → Schedule은 생성되지만 `attendCreatedCount` 0
- 배정 회원 N명인 장소 → 생성된 날짜 수 × N 만큼 Attend 생성 확인
- 검증 실패 경계값: `startDate > endDate`, `startTime >= endTime`, 다일 구간인데 `daysOfWeek` 빈 값, 기간 180일 초과 — 각각 `IllegalArgumentException`
- 존재하지 않는 `placeId` → `ResourceNotFoundException`

**`ScheduleControllerIntegrationTest` (통합, 기존 `AdminAttendanceControllerIntegrationTest` 패턴 재사용)**
- 인증 없음 → 401
- MEMBER 토큰 → 403
- ADMIN 토큰 + 정상 단건 요청 → 200, 응답 필드 검증 + DB에 Schedule/Attend 실제 생성 확인
- ADMIN 토큰 + 정상 반복 요청(일부 날짜 이미 존재) → 200, `createdDates`/`skippedDates` 분리 확인
- ADMIN 토큰 + 잘못된 `placeId` → 404
- ADMIN 토큰 + `startDate > endDate` → 400

**`CurrentAdminService`**: 별도 단위 테스트보다는 컨트롤러 통합 테스트에서 `Schedule.createdBy`가 요청한 admin으로 정확히 저장됐는지 확인하는 것으로 충분하다 (로직이 `CurrentMemberService`와 동일해 신규 분기가 거의 없음).

## 결정 사항 요약 (브레인스토밍 중 확정)

- 범위: 백엔드 API까지만, admin-web 화면은 이번 범위 밖
- 배정 방식: 장소에 배정된(assignedPlaceId) 회원 전원 자동 배정 (관리자가 개별 선택하지 않음)
- 생성 범위: 단건 생성 + 반복 일정(요일 패턴 + 기간) 모두 지원
- 중복 처리: 같은 장소·같은 날짜에 이미 Schedule이 있으면 그 날짜만 건너뛰고 나머지는 생성
- 중복 판단 기준: 장소+날짜 단위 (하루 1건 — 같은 날 시간대가 다른 여러 일정은 지원하지 않음)
