# 장소(Place) 완전한 CRUD + 관리 화면 — 설계 (짧은 형식, constitution.md 준수)

## 이번 범위

장소는 지금까지 조회(`GET /api/admin/places`)만 가능하고 생성·수정·삭제 API/화면이 전혀 없었다.
관리자가 admin-web에서 장소를 등록·수정·비활성화(소프트 삭제)할 수 있게 한다.

## 제외 범위

- 위도/경도 자동 변환(Google Geocoding 연동) — 관리자가 직접 숫자로 입력 (좌표는 관리자가 별도로 확인해서 입력)
- imageUrl, phoneNumber 필드 — 현재 어떤 API 응답에도 노출되지 않는 죽은 필드라 폼에서 제외
- 하드 삭제 — 소프트 삭제(active 플래그)만 지원
- 장소별 이름 중복 검사 — 현재 unique 제약 없음, 이번 범위에서도 추가 안 함

## 데이터 모델 변경

`Place`에 `active`(boolean, not null, default true) 컬럼 추가.
신규 Flyway 마이그레이션 `V2__add_place_active.sql`: `ALTER TABLE place ADD COLUMN active BIT(1) NOT NULL DEFAULT 1;` (기존 행 전부 active=true로 백필됨).
`E2eSeedDataInitializer`가 만드는 시드 장소도 자동으로 active=true (필드 기본값).

## 주요 계약

**`POST /api/admin/places`** (신규)
`{ name, address, unitType, description?, latitude, longitude }` — name/address/unitType/latitude/longitude 필수(위경도는 출석 반경 검증에 실제로 쓰이므로 필수). 생성된 장소를 `active=true`로 저장하고 `PlaceAdminSummaryDto` 반환.

**`PATCH /api/admin/places/{id}`** (신규)
`{ name, address, unitType, description?, latitude, longitude, active }` — 전체 필드 세트로 갱신(부분 patch 아님, 수정 모달이 통째로 저장). placeId 없으면 404.

**`GET /api/admin/places`** (기존, 응답에 `active` 필드 추가)
관리자 화면용이므로 active 무관하게 전체 반환. `AdminPlaceController`가 반환하는 DTO를 `PlaceSummaryDto`에서 `active`를 포함하는 `PlaceAdminSummaryDto`로 교체(또는 `PlaceSummaryDto`에 `active` 필드 추가 — 회원용 `/api/v1/places` 쪽 DTO와 공유 여부는 구현 시 판단).

**일반 사용자 노출 경로는 active=true만** — `PlaceRepository.findByUnitType`, `searchByUnitTypeAndKeyword`에 `active = true` 조건 추가. `/api/v1/places`(회원 자기서비스 검색)에 자동 반영.

**기존 admin-web 화면 2곳도 비활성 장소 숨김** (신규 API가 active 필드를 실어 나르면서 생기는 자연스러운 결과):
- `MemberManagementPage`의 장소 select — `places?.filter(p => p.active)`
- `AttendManagementPage`의 장소 select — 동일하게 필터

## 화면 구성

**신규 페이지** `admin-web/src/features/place-management/PlaceManagementPage.tsx`, 라우트 `/place-management`, 사이드바 "장소 관리" 메뉴(회원 관리 다음).

- 상단 카드: 인라인 등록 폼 (이름/주소/유형 select/설명/위도/경도 입력 → 등록). 필수값 미입력 시 등록 버튼 비활성화 (`MemberManagementPage`와 동일 패턴).
- 하단: 장소 목록 테이블 (이름/주소/유형/활성상태 배지/작업열). 각 행에 **수정**(모달 오픈) + **활성화/비활성화** 원클릭 토글 버튼.
- 신규 **Modal** 컴포넌트(`admin-web/src/components/Modal.tsx`) — 오버레이+센터 패널, ESC/배경클릭으로 닫기. 수정 모달에 등록 폼과 동일한 필드 세트 + 활성 체크박스, 저장 시 `PATCH /api/admin/places/{id}` 호출 후 목록 재조회.

## 데이터 흐름 / 상태 변화

```
1. 등록 폼 제출 → POST /api/admin/places → 201/200, 목록 재조회
2. 목록의 "수정" 클릭 → 모달에 현재 값 채워서 오픈
3. 모달 저장 → PATCH /api/admin/places/{id} → 성공 시 모달 닫고 목록 재조회
   - placeId 없음 → 404
4. 목록의 "비활성화"/"활성화" 클릭 → PATCH /api/admin/places/{id} (active만 토글, 나머지 필드는 현재 값 그대로 재전송)
5. 비활성화된 장소 → 회원 등록/일정별 출석 관리의 장소 select, 회원 자기서비스 검색(`/api/v1/places`)에서 제외
```

## 에러 처리

| 상황 | 응답 |
|---|---|
| 인증 없음 / MEMBER 토큰 | 401 / 403 (기존 `/api/admin/**` 매처) |
| 필수 필드 누락 (name/address/unitType/latitude/longitude) | 400 (Bean Validation) |
| 존재하지 않는 placeId로 PATCH | 404 |
| 정상 처리 | 200 |

## 테스트 계획

- 백엔드: `AdminPlaceController`/`AdminPlaceService`(신규 분리 여부는 구현 시 판단) 단위 테스트 — 생성/수정/비활성화, 검증 실패 케이스
- admin-web 단위: `PlaceManagementPage.test.tsx` — 기존 `MemberManagementPage.test.tsx` 패턴 (등록 성공/필수값 누락 시 버튼 비활성화/목록 렌더링/토글/수정 모달)
- admin-web e2e: `place-management.spec.ts` — 등록→목록 반영, 필수값 검증, 수정 모달 저장 반영, 활성/비활성 토글 후 새로고침 유지, 비활성 장소가 회원 등록 드롭다운에서 사라지는지

## 검증 기준

- 장소 생성/수정이 실제 DB에 반영되는가 (새로고침 후에도 유지)
- 위경도 필수 검증이 걸리는가 (없으면 400)
- 비활성 장소가 회원 등록/일정별 출석 관리 드롭다운과 회원 자기서비스 검색에서 실제로 사라지는가
- 관리자 화면(장소 관리)에서는 비활성 장소도 계속 보이고 재활성화 가능한가
- 401/403 권한 경계가 지켜지는가
