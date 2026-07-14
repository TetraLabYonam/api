# 관리자 웹 대시보드 Phase 1 — 로그인 + 사업단별 출석률 로비 화면

## 배경 및 범위

`senior-attendance-app`에는 현재 백엔드(Spring Boot)와 모바일(Flutter) 앱만 있고, 관리자용 웹 프론트엔드가 존재하지 않는다. 백엔드에는 이미 관리자 로그인 API(`/api/auth/login`, JWT + HttpOnly 리프레시 쿠키)와 `Admin` 도메인, 기본 관리자 계정 시딩(`AdminDataInitializer`)이 갖춰져 있지만, 이를 사용할 UI가 없다.

이번 설계는 아래 세 화면 요청 중 **로그인 + 로비(=모니터링) 화면만**을 다룬다:

> "먼저 간단한 로그인 기능... 이후 근태 관리를 위한 담당자의 모니터링 화면... 사업단 별 출석률 및 특정 사업단별로 요구사항이 다른점을 고려하여 로비 화면을 만들꺼야."

브레인스토밍 과정에서 "사업단별 요구사항 차이"를 구체화한 결과, 아래 항목들은 별도 백엔드 도메인(재고 등)이 필요하거나 완전히 새로운 대형 기능이라 이번 스펙 범위에서 제외하고 후속 설계로 분리했다:

- **Phase 2 (후속)**: 관리자가 스케줄표를 엑셀 스타일로 작성하고 한글 `.xlsx`로 추출하는 기능
- **Phase 3 (후속)**: 시장형(MARKET) 사업단의 재고 관리 — 모바일 CRUD + 신규 `Inventory` 백엔드 도메인 + 웹 대시보드 조회
- **별도 트랙 (웹 대시보드 범위 아님)**: 모바일 앱을 사업단 유형별로 차등화 (공익형 근태 전용 단순화, 사회서비스형 캘린더식 스케줄 추가/삭제, 시장형 재고 기능)

Phase 1 완료 후, Phase 2를 시작할지는 사용자에게 다시 확인한다.

## 아키텍처

```
senior-attendance-app/
├── backend/     (기존 Spring Boot — API만 추가, 아래 참조)
├── mobile/      (기존 Flutter — 이번 작업과 무관)
└── admin-web/   (신규 React + Vite + TypeScript SPA)
```

- `admin-web`은 `mobile`과 나란히 위치하는 완전히 새로운 최상위 디렉터리다. 백엔드가 서빙하지 않는 독립 정적 SPA로 빌드/배포한다.
- 인증은 백엔드의 기존 JWT 체계를 그대로 재사용한다: 로그인 성공 시 accessToken(응답 바디)과 refreshToken(HttpOnly 쿠키, `/api/auth/refresh` 경로에만 유효)을 받는 기존 구조 그대로 사용한다.
- accessToken은 React 메모리(Context)에만 보관하고 localStorage에는 저장하지 않는다. 새로고침으로 메모리가 초기화되면 앱 시작 시 `/api/auth/refresh`(쿠키 기반)를 한 번 시도해 로그인 상태를 복원한다 — 실패하면 로그인 화면으로 이동한다. 이는 모바일 앱의 `AuthGate`/`isLoggedInProvider` 패턴과 동일한 개념이다.
- 신규 관리자 전용 API(`/api/admin/**`)는 `SecurityConfig`에 `hasRole("ADMIN")` matcher를 명시적으로 추가해 잠근다. 현재 `SecurityConfig`는 `MEMBER` 역할 경로만 명시하고 나머지는 `anyRequest().authenticated()`(역할 무관, 인증만 되면 통과)이므로, 이 규칙이 없으면 회원(MEMBER) 토큰으로도 관리자 API에 접근 가능한 상태다.

## 백엔드 변경사항

1. **시드 계정 변경**: `app.default-admin.username`/`app.default-admin.password` 기본값을 `admin@example.com`/`1234`로 변경한다. `AdminDataInitializer`는 해당 username이 이미 존재하면 재생성하지 않으므로, 로컬 DB에 기존 `admin`/`admin` 계정이 남아있다면 개발 환경 DB를 초기화하거나 계정을 수동 갱신해야 한다.

2. **`SecurityConfig` 수정**: `/api/admin/**` 경로에 `.requestMatchers("/api/admin/**").hasRole("ADMIN")` 추가.

3. **`AttendRepository`에 집계 쿼리 추가**:
   ```java
   @Query("SELECT p.unitType, a.status, COUNT(a) FROM Attend a " +
          "JOIN a.schedule s JOIN s.place p " +
          "WHERE s.scheduleDate BETWEEN :start AND :end " +
          "GROUP BY p.unitType, a.status")
   List<Object[]> getAttendanceStatsByUnitTypeAndDateRange(
       @Param("start") LocalDate start, @Param("end") LocalDate end);
   ```

4. **신규 `AdminAttendanceService`**:
   - `period`(`today`/`week`/`month`) 파라미터를 `LocalDate` 범위로 변환: `today`는 당일 하루, `week`는 이번 주 월요일(ISO 기준, `DayOfWeek.MONDAY`)부터 오늘까지, `month`는 이번 달 1일부터 오늘까지.
   - 위 쿼리 결과를 `UnitType`별로 묶어 출석률(%)을 계산: `(PRESENT + LATE) / 전체 * 100`. 이는 `Schedule.getAttendanceRate()`와 동일한 정의를 재사용한다.
   - 응답의 `label` 필드는 하드코딩하지 않고 `UnitType.getDescription()`을 그대로 사용한다 (한글 라벨이 두 곳에서 따로 관리되지 않도록).
   - `UnitType.values()` 3종을 항상 결과에 포함시킨다 (데이터가 없는 유형은 0%로 채움) — 프론트가 빈 배열/누락 유형을 별도 처리하지 않아도 되게 한다.

5. **신규 `AdminAttendanceController`**:
   ```
   GET /api/admin/attendance/summary?period=today|week|month
   → 200 OK
   [
     { "unitType": "PUBLIC_INTEREST", "label": "공익형", "attendanceRate": 87.3 },
     { "unitType": "MARKET", "label": "시장형", "attendanceRate": 92.0 },
     { "unitType": "SOCIAL_SERVICE", "label": "사회서비스형", "attendanceRate": 78.5 }
   ]
   ```
   `period` 값이 위 세 가지가 아니면 400 Bad Request.

## 프론트엔드 구조 (`admin-web`)

```
admin-web/
├── src/
│   ├── main.tsx
│   ├── App.tsx                  (라우팅: /login, / (로비))
│   ├── api/
│   │   └── client.ts             (fetch 래퍼: Bearer 헤더 부착, 401 시 refresh 후 재시도)
│   ├── features/
│   │   ├── auth/
│   │   │   ├── LoginPage.tsx
│   │   │   └── AuthContext.tsx   (accessToken 상태, isLoggedIn, refresh 로직)
│   │   └── lobby/
│   │       ├── LobbyPage.tsx
│   │       ├── UnitTypeCard.tsx
│   │       └── PeriodSelector.tsx
│   └── types/
│       └── attendance.ts
```

- **라우팅 가드**: `AuthContext`에 accessToken이 없으면 `/login`으로 리다이렉트.
- **`LoginPage`**: 아이디(이메일)/비밀번호 입력 폼 → `POST /api/auth/login` → 성공 시 `/`(로비)로 이동, 실패(401) 시 폼 아래 "아이디 또는 비밀번호가 올바르지 않습니다" 표시.
- **`LobbyPage`**: 마운트 시 `period=today`로 자동 조회. 상단에 기간 선택(오늘/이번주/이번달) 탭, 선택 변경 시 재조회. 사업단 유형 카드 3개(공익형/시장형/사회서비스형)를 고정 배치하고 출석률(%)만 표시. 수동 새로고침 버튼 포함.
- **상태관리**: 화면이 2개뿐이고 상태가 단순하므로 별도 전역 상태관리 라이브러리 없이 React Context + `useState`/`useEffect`로 구현한다.

## 데이터 흐름

```
로그인 폼 제출
  → POST /api/auth/login { username, password }
  → 200: accessToken(body) + refreshToken(Set-Cookie) 수신
  → AuthContext에 accessToken 저장, "/" 로 이동
  → LobbyPage 마운트 → GET /api/admin/attendance/summary?period=today
  → 카드 3개 렌더링
  → 기간 탭 클릭 → 같은 API를 다른 period로 재호출 → 카드 갱신
```

## 에러 처리

| 상황 | 처리 |
|---|---|
| 로그인 401 (잘못된 계정) | 폼 아래 에러 메시지 표시, 화면 유지 |
| 로비 화면에서 API 401 (accessToken 만료) | `/api/auth/refresh` 자동 시도(쿠키 기반) → 성공 시 원래 요청 재시도, 실패 시 `/login`으로 강제 이동 |
| 로비 화면에서 API 500/네트워크 오류 | 카드 대신 "출석률을 불러오지 못했습니다" 메시지 + 재시도 버튼 |
| 새로고침(F5) 직후 | accessToken이 메모리에서 사라짐 → 앱 시작 시 `/api/auth/refresh` 한 번 시도해 로그인 상태 복원 여부 판단 |

## 테스트 전략

**백엔드**
- `AdminAttendanceServiceTest` (단위): `period` → 날짜 범위 변환 경계값(오늘/이번주/이번달), 유형별 출석률 계산(0건일 때 0% 처리 포함), `UnitType` 3종이 항상 포함되는지 검증
- `AdminAttendanceControllerIntegrationTest` (통합): 인증 없이 호출 시 401, MEMBER 토큰으로 호출 시 403(ROLE_ADMIN 아님), ADMIN 토큰으로 정상 호출 시 응답 형식 검증. `@SpringBootTest` + 실제 로그인 플로우로 토큰을 발급받는 기존 패턴(`PlaceControllerIntegrationTest` 등)을 따른다.

**프론트엔드**
- Vitest + React Testing Library 사용 (Vite 생태계 표준)
- `LoginPage`: 성공 시 리다이렉트, 실패 시 에러 메시지 렌더링 — API는 mock 처리
- `LobbyPage`: 기간 탭 전환 시 재조회 여부, API 실패 시 에러+재시도 버튼 렌더링
- 초반에 API 클라이언트 mock 테스트 헬퍼를 하나 만들어 두 화면 테스트에서 재사용 (모바일 작업의 `fake_api_client.dart`와 동일한 개념)
