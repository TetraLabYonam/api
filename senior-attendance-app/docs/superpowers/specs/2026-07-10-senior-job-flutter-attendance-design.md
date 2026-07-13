# 시니어 일자리 근태 관리 Flutter 앱 — 설계

## 배경 및 목표

기존 시스템은 Spring Boot 백엔드 + React 관리자 웹으로 구성된 노인 일자리 출석/번호표 관리 시스템이다. 이 설계는 참여자(어르신)와 팀장이 직접 사용할 **Flutter 모바일 앱**을 신설하여 기존 백엔드에 연결하는 것을 다룬다. 관리자는 기존 React 웹을 계속 사용하되, 이번 기능에 필요한 화면만 확장한다.

핵심 시나리오: 참여자가 첫 화면에서 사업단 유형(공익형/시장형/사회서비스형)을 고르고, 본인 일자리를 목록 또는 키워드 검색으로 찾아 연결한 뒤, 근무지 500m 이내에서만 위치기반 출석 체크를 할 수 있다. 사업단 유형별로 다음 전용 기능이 추가된다.

- **공익형**: 참여자 다수가 80대 이상으로 스마트폰이 없는 경우가 많아, 팀장이 명단을 보고 대리로 출석 체크를 할 수 있다.
- **시장형**: 근태 관리는 동일하되, 품목별 재고 현황 파악 기능이 추가된다.
- **사회서비스형**: 근태 관리는 동일하다. 국가 지침 스케줄표에 없는 휴무일/돌발 행사를 엑셀로 관리하고 있으나, **실제 사용 중인 엑셀 양식이 아직 없어 이번 스펙에서는 제외하고 추후 별도로 설계한다.** 이번 스펙에서 사회서비스형은 공통 코어 플로우만 적용된다.

## 범위

이번 스펙은 아래를 포함한다.
1. 공통 코어: 사업단 유형 선택 → 일자리 검색/선택 → 위치정보 동의 → 위치기반 출석 체크
2. 공익형: 팀장 대리 출석
3. 시장형: 재고 관리 (품목별 현재 수량 조회 + 수동 입출고 기록)
4. 관리자(React 웹) 확장: 사업단 유형 관리, 팀장 지정, 재고 품목 관리, 동의 현황 조회
5. 개발자 모니터링 대시보드 (운영 지표)

다음은 이번 스펙에서 다루지 않는다.
- 사회서비스형 엑셀 스케줄 조회 (추후 실제 양식 확정 후 별도 스펙)
- 시장형 POS/바코드 연동
- 채용공고형 일자리 검색(아직 배정되지 않은 일자리에 지원하는 기능)
- 개발자 전용 별도 로그인 체계 (기존 Admin 계정으로 접근)

## 사용자 역할 & 인증

| 역할 | 클라이언트 | 인증 방식 |
|---|---|---|
| 참여자(어르신) | Flutter 앱 | 휴대폰번호 + SMS OTP (기존 `SmsService` 재사용) |
| 팀장(공익형) | Flutter 앱 (참여자와 동일 앱, 역할만 다름) | 휴대폰번호 + SMS OTP |
| 관리자 | 기존 React 웹 | 기존 Admin 계정(ID/PW) |

팀장은 별도 계정 체계가 아니라 `Member`에 역할 플래그(`role: PARTICIPANT | TEAM_LEADER`)를 추가하는 형태다. 관리자가 특정 참여자를 특정 Schedule/Place의 팀장으로 지정한다. 하나의 Flutter 앱이 로그인 계정의 role에 따라 화면을 분기한다.

## 공통 코어 플로우

1. **사업단 유형 선택** — 공익형 / 시장형 / 사회서비스형 (첫 화면)
2. **본인 일자리 찾기** — 선택한 유형에 속한 Place 목록에서 이름순 탐색 또는 검색. 선택 시 해당 Place를 본인 계정에 최초 1회 연결(온보딩)한다.
3. **위치정보 수집 동의** — 최초 가입/첫 출석 시 1회, `Member.locationConsentAgreedAt` 타임스탬프로 저장한다. 동의 전에는 출석 체크 기능이 비활성화된다.
4. **위치기반 출석 체크** — 기존 `AttendService.checkIn()`의 Haversine 거리 계산 로직을 그대로 재사용한다. `attendance.location.radius` 설정값을 500(m)으로 변경한다. 반경 밖이면 명확한 실패 사유("현재 위치가 근무지에서 OOOm 떨어져 있습니다")를 반환한다.

### 스키마 보강: Place ↔ 사업단 유형 연결 (중요)
현재 코드에서 `Unit`은 별도 테이블이 아니라 `Member`에 임베디드된 값 타입(UNIT_NAME, UNIT_TYPE 컬럼)이며, `Place`는 사업단 유형과 아무 연결이 없다. "사업단 유형 선택 → 해당 유형의 일자리 목록" 플로우가 동작하려면 **`Place`에 `unitType`(`UnitType` enum) 컬럼을 신규로 추가**해야 한다. 검색/필터는 이 `Place.unitType` 기준으로 동작한다. (`Member.unit.type`은 회원이 소속된 사업단명을 나타내는 기존 필드로 그대로 두되, 동일한 `UnitType` enum으로 문자열을 정리한다.)

### 일자리 검색 (2단계)
실제 일자리명(예: "공원안전지킴이", "동네마당재활용", "스쿨존실버봉사단")에는 참여자가 기억하는 일상어("청소", "쓰레기 줍기", "화단")가 문자 그대로 포함되지 않는 경우가 대부분이라, 단순 LIKE 검색만으로는 매칭에 실패한다. 이를 2단계로 해결한다.

1. **1단계 — 동의어 매핑 + LIKE 검색**: 신규 테이블 `JobKeywordSynonym`(`place_id` FK, `keyword`)에 관리자가 각 Place별 일상어 키워드를 미리 등록해둔다 (예: 공원안전지킴이 → "청소", "쓰레기 줍기", "순찰"). 검색 시 `Place.name`/`description`뿐 아니라 이 동의어 테이블도 함께 LIKE 매칭한다. 결정적이고 추가 인프라가 필요 없어 대부분의 케이스를 여기서 해결한다.
2. **2단계 — LLM 폴백**: 1단계 결과가 0건일 때만 호출한다. 선택된 사업단 유형에 속한 Place 후보 목록(이름+description+동의어, 보통 사업단당 수십 개 이내)과 사용자의 자유서술 입력을 LLM 프롬프트에 함께 전달해 가장 가까운 후보를 추천받는다. 후보군이 항상 작아 벡터DB나 임베딩 인프라 없이 텍스트 프롬프트 하나로 충분하다. 신규 엔드포인트: `POST /api/v1/places/search/fallback`.
   - 비용/지연 관리를 위해 1단계 실패 시에만 호출하고, 동일 (사업단유형, 입력어) 조합에 대한 결과는 캐싱한다.

### Attend 레코드 생성 시점
관리자가 특정 Place에 대해 신규 Schedule을 생성하면, 해당 Place에 `assignedPlaceId`로 연결된 모든 Member에 대해 `SCHEDULED` 상태의 `Attend` 레코드를 자동 생성한다 (기존 `ScheduleService`에 로직 추가). 참여자가 "본인 일자리 찾기"로 Place에 연결하는 것은 이 자동 생성 대상에 포함되기 위한 등록 행위이며, 그 자체로 즉시 출석 가능한 Attend가 생기는 것은 아니다.

### 보안 보강: memberId 위조 방지
현재 `AttendCheckInRequest.memberId`는 클라이언트가 그대로 지정하는 구조라 다른 사람 ID로 위조 요청이 가능하다. 본인 체크인은 JWT 인증 주체(principal)에서 memberId를 서버가 직접 추출하도록 변경한다. 팀장의 대리 출석만 예외적으로 대상 memberId를 명시적으로 받되, 아래 권한 검증을 거친다.

## 공익형 — 팀장 대리 출석

- **화면**: 팀장 로그인 시 "우리 팀 출석 체크" 메뉴 노출 → 본인이 담당하는 Schedule에 배정된 참여자 명단(이름, 출석 상태) 표시
- **동작**: 팀장이 명단에서 이름을 탭 → 팀장 본인의 현재 GPS 좌표로 500m 이내 여부 판정 → 통과 시 해당 참여자의 `Attend`를 PRESENT로 처리. 팀장 본인 출석도 같은 명단의 항목 중 하나로 처리한다.
- **권한 검증**: 요청자가 `TEAM_LEADER`이고, 대상 memberId가 요청자와 **같은 Schedule에 배정**된 경우에만 대리 출석을 허용한다. 그 외에는 본인 memberId만 허용(JWT principal 기준).
- **데이터 모델**: 신규 테이블 불필요. `Attend`에 감사 추적용 `checkedByMemberId`(nullable, 본인 체크 시 null) 컬럼 추가.

## 시장형 — 재고 관리

- **화면**: 참여자/팀장이 소속 Place(매장) 진입 시 "재고 현황" 탭 — 품목별 현재 수량 리스트, 품목 탭 시 입출고 이력 표시
- **입출고 기록**: 참여자가 판매/입고 시 `+`/`-` 버튼으로 수량 조정, 사유(선택) 입력
- **데이터 모델**:
  - `InventoryItem` (id, place_id FK, name, unit(개/kg 등), current_quantity, updated_at)
  - `InventoryTransaction` (id, item_id FK, member_id FK, delta(+/-), reason, created_at)
  - 트랜잭션 저장 시 `InventoryItem.current_quantity`를 함께 갱신하며, 동시 조정 충돌 방지를 위해 기존 `TicketService`의 Row 락(`findByRoomUidForUpdate` 패턴)을 재사용한다.
- **API**: `GET /api/v1/inventory/place/{placeId}`, `POST /api/v1/inventory/{itemId}/transactions`, `GET /api/v1/inventory/{itemId}/transactions`

## 관리자(React 웹) 확장

기존 `AdminPage.jsx` 계열에 화면을 추가하며, 로그인 체계는 그대로 사용한다.
- **사업단 유형 관리**: Place 등록/수정 화면에 `unitType`(공익형/시장형/사회서비스형) 드롭다운 추가
- **검색 동의어 관리**: Place별로 참여자가 쓸 법한 일상어 키워드(청소, 화단, 쓰레기 줍기 등)를 등록/수정하는 화면 (`JobKeywordSynonym`)
- **팀장 지정 화면**: Schedule/Place별 배정 참여자 목록에서 한 명을 TEAM_LEADER로 지정/해제
- **재고 품목 관리**: 시장형 Place에 대해 InventoryItem 등록/수정/삭제
- **동의 현황 조회**: 참여자별 위치정보 동의 여부·일시 조회(감사 목적, 읽기 전용)

## 개발자 모니터링 대시보드

현재 `checkIn()`은 반경 밖이면 예외만 던지고 기록을 남기지 않아, 출석 실패율 등의 운영 수치를 추후에 뽑을 수 없다. 이를 위해 모든 체크인 시도를 기록하는 감사 로그를 추가한다.

- **`AttendanceAttemptLog`**: 모든 체크인 시도(성공/실패 불문)를 기록 — `member_id`, `schedule_id`, `distance_meters`, `success`, `failure_reason`, `created_at`
- **집계 API** `GET /api/v1/admin/metrics/attendance-summary?days=7`: 총 시도/성공/거부 건수, 평균 거리, 평균 소요시간
- **시스템 지표**: 기존 CI 헬스체크(`/actuator/health`)에 쓰이던 Spring Boot Actuator/Micrometer를 재사용해 API 응답시간·에러율·JVM 메모리 등을 노출하는 프록시 엔드포인트 `GET /api/v1/admin/metrics/system-summary` 추가 (raw actuator를 그대로 공개하지 않고 필요한 값만 선별)
- **화면**: React에 `DeveloperMetricsPage.jsx` 신규 추가(기존 Admin 로그인으로 접근, 별도 계정 체계 불필요). 카드/표 형태로 수치 표시하며, 차트 라이브러리 도입 여부는 구현 단계에서 결정한다.

## 데이터 모델 변경 요약

- 신규 `UnitType` enum(`PUBLIC_INTEREST`, `MARKET`, `SOCIAL_SERVICE`) 정의
- `Place`: `unitType`(`UnitType`, not null) 컬럼 신규 추가 — 사업단 유형별 일자리 검색의 기준
- `Member.unit.type`(임베디드 필드): 기존 String을 `UnitType` enum으로 정리
- `Member`: `role`(PARTICIPANT/TEAM_LEADER), `locationConsentAgreedAt`, `assignedPlaceId` 추가
- `Attend`: `checkedByMemberId`(nullable) 추가
- `ScheduleService`: Schedule 생성 시 해당 Place에 배정된(`assignedPlaceId` 일치) 전 Member에 대해 `SCHEDULED` Attend 자동 생성 로직 추가
- 신규 테이블: `InventoryItem`, `InventoryTransaction`, `AttendanceAttemptLog`, `JobKeywordSynonym`
- `Place` 리포지토리에 `unitType` 필터 + name/description/동의어 통합 LIKE 검색 쿼리 추가
- 신규 엔드포인트 `POST /api/v1/places/search/fallback` (LLM 기반 폴백 검색)
- `attendance.location.radius` 설정값을 500으로 변경

## Flutter 앱 아키텍처

- **상태관리**: Riverpod
- **화면 구조**: 단일 앱, 로그인 계정의 `role`(PARTICIPANT/TEAM_LEADER)에 따라 화면 분기. 별도 앱을 만들지 않는다.
- **위치 검증**: 클라이언트는 GPS 좌표 수집과 UX 안내(사전 거리 표시 등)만 담당하고, 최종 500m 판정은 항상 서버가 수행한다.

## 비기능 요구사항

- **보안**: 팀장 대리출석 권한 검증(같은 Schedule 배정자만), 위치정보 최초 1회 동의 필수, JWT 기반 memberId 추출로 체크인 위조 방지
- **에러 처리**: 반경 밖 실패 시 사유 명시, OS 위치 권한 거부 시 안내 화면, 네트워크 실패 시 재시도 유도
- **비용/지연**: LLM 폴백 검색은 1단계(동의어+LIKE) 실패 시에만 호출하고 (사업단유형, 입력어) 단위로 결과를 캐싱해 호출 빈도와 비용을 제한한다
- **테스트**: 기존 `AttendServiceTest` 패턴 확장(팀장 대리출석 권한 케이스 포함), 재고 동시성 테스트, 검색 동의어 매칭/폴백 트리거 조건 테스트 추가

## Out of scope (재확인)

- 사회서비스형 엑셀 스케줄 조회 — 실제 사용 중인 엑셀 양식이 없어 추후 별도 설계
- 시장형 POS/바코드 연동
- 채용공고형 일자리 검색
- 개발자 전용 별도 로그인 체계
