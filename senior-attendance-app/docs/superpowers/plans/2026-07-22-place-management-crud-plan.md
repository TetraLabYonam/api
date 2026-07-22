# 장소(Place) 완전한 CRUD + 관리 화면 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자가 admin-web에서 장소를 등록·수정·비활성화(소프트 삭제)할 수 있게 하고, 비활성 장소가 회원 등록/일정 관리/회원 자기서비스 검색에서 자동으로 숨겨지게 한다.

**Architecture:** 백엔드는 기존 `AdminPlaceController`(`/api/admin/places`)에 POST/PATCH를 추가하고, `Place`에 `active` 컬럼을 신설한다. 회원 노출 경로(`PlaceRepository`의 검색 메서드)는 `active=true`만 반환하도록 필터를 추가한다. 프런트엔드는 기존 `MemberManagementPage`/`AttendManagementPage` 패턴을 그대로 따르는 신규 `PlaceManagementPage`를 만들고, 수정은 새로 만드는 재사용 가능한 `Modal` 컴포넌트로 처리한다.

**Tech Stack:** Spring Boot 3.5.6 / Java 17 / MariaDB(prod)+H2(local,test) / Flyway, React 19 + TypeScript + Vite (admin-web), Playwright(e2e), Vitest(unit)

## Global Constraints

- 모든 신규 API는 `/api/admin/**` 하위 — `SecurityConfig`가 이미 `ROLE_ADMIN`으로 강제하므로 컨트롤러에 별도 권한 애노테이션 불필요
- Java DTO는 기존 컨벤션 그대로: `@Data @NoArgsConstructor @AllArgsConstructor`, Bean Validation(`@NotBlank`/`@NotNull`) 메시지는 한국어
- 예외는 기존 `GlobalExceptionHandler`가 처리 — `ResourceNotFoundException` → 404, `MethodArgumentNotValidException`(`@Valid` 실패) → 400. 컨트롤러에서 try/catch 직접 하지 않음
- admin-web 컴포넌트는 기존 CSS 클래스(`card`, `btn`/`btn-primary`/`btn-secondary`/`btn-sm`, `field`/`field-label`/`input`, `data-table`, `badge-*`)만 사용 — 새 클래스는 모달에 한해서만 추가
- 각 태스크 끝에 커밋 1개. 커밋 메시지는 `feat(place-management): ...` / `test(...)` 형식

---

## Task 1: Place 엔티티에 active 컬럼 추가 + 마이그레이션

**Files:**
- Modify: `backend/src/main/java/com/example/attempt/domain/Place.java`
- Create: `backend/src/main/resources/db/migration/V2__add_place_active.sql`

**Interfaces:**
- Produces: `Place.isActive()` / `Place.setActive(boolean)` (Lombok `@Data`가 자동 생성) — Task 2, 3, 4가 사용

- [ ] **Step 1: `Place.java`에 `active` 필드 추가**

`backend/src/main/java/com/example/attempt/domain/Place.java`의 `unitType` 필드 바로 다음에 추가:

```java
    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", length = 20)
    private UnitType unitType;

    @Column(name = "active", nullable = false)
    private boolean active = true;
```

- [ ] **Step 2: 마이그레이션 파일 작성**

`backend/src/main/resources/db/migration/V2__add_place_active.sql` (신규 파일):

```sql
ALTER TABLE place ADD COLUMN active BIT(1) NOT NULL DEFAULT 1;
```

(V1이 Hibernate가 실제로 생성하는 DDL을 캡처해 만든 것이라 boolean 필드가 전부 `BIT(1)`로 매핑되는 것을 이미 확인함 — `member.active`, `schedule.is_active` 등과 동일 타입)

- [ ] **Step 3: 로컬(H2)에서 애플리케이션이 정상 기동하는지 확인**

```bash
cd backend
REFRESH_TOKEN_HASH_SECRET=verify-secret MEMBER_OTP_HASH_SECRET=verify-otp-secret \
  ./gradlew bootRun --args="--spring.profiles.active=local,e2e-seed" &
sleep 15
curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"admin@example.com","password":"1234"}' -w "\n%{http_code}\n"
kill %1
```

Expected: `200`과 함께 `accessToken` 응답 (H2는 `ddl-auto=update`라 마이그레이션 파일과 무관하게 `active` 컬럼이 자동 생성됨 — 여기서는 엔티티 필드 자체에 오타/컴파일 에러가 없는지만 확인)

- [ ] **Step 4: 실제 MariaDB(docker-compose)에 마이그레이션이 깨끗하게 적용되는지 확인**

기존 `docker-compose.yml`의 mariadb 볼륨에는 V1이 이미 적용되어 있으므로, 여기서 V2 하나만 추가 적용되는 실제 업그레이드 시나리오를 검증한다.

```bash
cd senior-attendance-app
docker compose up -d --build backend
sleep 10
docker logs senior-attendance-app-backend-1 2>&1 | grep -iE "flyway|schemamanagement|started attemptapplication|error|exception" | grep -v p6spy
```

Expected: `Successfully applied 1 migration ... now at version v2` 로그와 `Started AttemptApplication` 로그, `SchemaManagementException` 없음

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/example/attempt/domain/Place.java \
        backend/src/main/resources/db/migration/V2__add_place_active.sql
git commit -m "feat(place-management): add active column to Place entity"
```

---

## Task 2: 관리자용 장소 생성/수정 API

**Files:**
- Create: `backend/src/main/java/com/example/attempt/dto/place/CreatePlaceRequest.java`
- Create: `backend/src/main/java/com/example/attempt/dto/place/UpdatePlaceRequest.java`
- Modify: `backend/src/main/java/com/example/attempt/dto/place/PlaceSummaryDto.java`
- Modify: `backend/src/main/java/com/example/attempt/controller/AdminPlaceController.java`
- Modify: `backend/src/test/java/com/example/attempt/controller/AdminPlaceControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `Place.isActive()`/`setActive(boolean)` (Task 1)
- Produces: `POST /api/admin/places`, `PATCH /api/admin/places/{id}` — 둘 다 `PlaceSummaryDto` 반환. `PlaceSummaryDto`에 `active`(boolean) 필드 추가 — Task 3(리포지토리), Task 4(프런트) 이 필드를 사용

- [ ] **Step 1: `CreatePlaceRequest` 작성**

`backend/src/main/java/com/example/attempt/dto/place/CreatePlaceRequest.java` (신규 파일):

```java
package com.example.attempt.dto.place;

import com.example.attempt.domain.UnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 관리자가 신규 장소를 등록할 때 사용하는 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePlaceRequest {
    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    @NotBlank(message = "주소는 필수입니다.")
    private String address;

    @NotNull(message = "장소 유형(unitType)은 필수입니다.")
    private UnitType unitType;

    private String description;

    @NotNull(message = "위도는 필수입니다.")
    private Double latitude;

    @NotNull(message = "경도는 필수입니다.")
    private Double longitude;
}
```

- [ ] **Step 2: `UpdatePlaceRequest` 작성**

`backend/src/main/java/com/example/attempt/dto/place/UpdatePlaceRequest.java` (신규 파일):

```java
package com.example.attempt.dto.place;

import com.example.attempt.domain.UnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 관리자가 장소 정보를 수정할 때 사용하는 요청 DTO. 전체 필드를 갱신한다(부분 수정 아님).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePlaceRequest {
    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    @NotBlank(message = "주소는 필수입니다.")
    private String address;

    @NotNull(message = "장소 유형(unitType)은 필수입니다.")
    private UnitType unitType;

    private String description;

    @NotNull(message = "위도는 필수입니다.")
    private Double latitude;

    @NotNull(message = "경도는 필수입니다.")
    private Double longitude;

    @NotNull(message = "active 값은 필수입니다.")
    private Boolean active;
}
```

- [ ] **Step 3: `PlaceSummaryDto`에 `active` 필드 추가**

`backend/src/main/java/com/example/attempt/dto/place/PlaceSummaryDto.java` 전체를 아래로 교체:

```java
package com.example.attempt.dto.place;

import com.example.attempt.domain.UnitType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceSummaryDto {
    private Long id;
    private String name;
    private String address;
    private UnitType unitType;
    private String description;
    private Double latitude;
    private Double longitude;
    private boolean active;
}
```

- [ ] **Step 4: 실패하는 통합 테스트 작성**

`backend/src/test/java/com/example/attempt/controller/AdminPlaceControllerIntegrationTest.java`의 마지막 `}` (클래스 닫는 괄호) 바로 앞에 아래 4개 테스트와 헬퍼 메서드를 추가:

```java
    @Test
    void create_withValidRequest_persistsPlaceAsActive() {
        String accessToken = obtainAdminAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "name", "행복경로당",
                "address", "서울시 종로구",
                "unitType", "PUBLIC_INTEREST",
                "description", "청소 봉사",
                "latitude", 37.57,
                "longitude", 126.97);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/admin/places", req, Map.class);

        assertEquals(200, resp.getStatusCodeValue());
        Map<String, Object> respBody = resp.getBody();
        assertEquals("행복경로당", respBody.get("name"));
        assertEquals(true, respBody.get("active"));

        Number idNum = (Number) respBody.get("id");
        Place saved = placeRepository.findById(idNum.longValue()).orElse(null);
        assertNotNull(saved);
        assertTrue(saved.isActive());
        assertEquals(37.57, saved.getLatitude());
    }

    @Test
    void create_withMissingLatitude_returns400() {
        String accessToken = obtainAdminAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", "행복경로당");
        body.put("address", "서울시 종로구");
        body.put("unitType", "PUBLIC_INTEREST");
        body.put("longitude", 126.97);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/admin/places", req, Map.class);

        assertEquals(400, resp.getStatusCodeValue());
    }

    @Test
    void update_changesFieldsAndCanDeactivate() {
        Place place = new Place("수정전이름", "수정전주소", 35.0, 129.0);
        place.setUnitType(UnitType.PUBLIC_INTEREST);
        place = placeRepository.save(place);

        String accessToken = obtainAdminAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "name", "수정후이름",
                "address", "수정후주소",
                "unitType", "MARKET",
                "description", "수정된 설명",
                "latitude", 36.0,
                "longitude", 128.0,
                "active", false);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/admin/places/" + place.getId(),
                HttpMethod.PATCH, req, Map.class);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals("수정후이름", resp.getBody().get("name"));
        assertEquals(false, resp.getBody().get("active"));

        Place updated = placeRepository.findById(place.getId()).orElseThrow();
        assertEquals("수정후이름", updated.getName());
        assertEquals(UnitType.MARKET, updated.getUnitType());
        assertFalse(updated.isActive());
    }

    @Test
    void update_nonExistentId_returns404() {
        String accessToken = obtainAdminAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "name", "이름", "address", "주소", "unitType", "MARKET",
                "latitude", 36.0, "longitude", 128.0, "active", true);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/admin/places/999999",
                HttpMethod.PATCH, req, Map.class);

        assertEquals(404, resp.getStatusCodeValue());
    }
```

(이 파일은 이미 `import com.example.attempt.domain.UnitType;`를 갖고 있으므로 추가 임포트는 필요 없다.)

- [ ] **Step 5: 테스트 실행해서 실패 확인 (컴파일 에러 또는 404/501)**

```bash
cd backend
./gradlew test --tests "com.example.attempt.controller.AdminPlaceControllerIntegrationTest" 2>&1 | tail -40
```

Expected: FAIL — `AdminPlaceController`에 `create`/`update` 메서드가 없어 컴파일 에러 또는 404

- [ ] **Step 6: `AdminPlaceController`에 POST/PATCH 구현**

`backend/src/main/java/com/example/attempt/controller/AdminPlaceController.java` 전체를 아래로 교체:

```java
package com.example.attempt.controller;

import com.example.attempt.domain.Place;
import com.example.attempt.dto.place.CreatePlaceRequest;
import com.example.attempt.dto.place.PlaceSummaryDto;
import com.example.attempt.dto.place.UpdatePlaceRequest;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.PlaceRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/places")
@RequiredArgsConstructor
public class AdminPlaceController {

    private final PlaceRepository placeRepository;

    @GetMapping
    public List<PlaceSummaryDto> list() {
        return placeRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @PostMapping
    public PlaceSummaryDto create(@Valid @RequestBody CreatePlaceRequest request) {
        Place place = new Place(request.getName(), request.getAddress(), request.getLatitude(), request.getLongitude());
        place.setUnitType(request.getUnitType());
        place.setDescription(request.getDescription());
        place = placeRepository.save(place);
        return toDto(place);
    }

    @PatchMapping("/{id}")
    public PlaceSummaryDto update(@PathVariable Long id, @Valid @RequestBody UpdatePlaceRequest request) {
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("장소를 찾을 수 없습니다. ID: " + id));

        place.setName(request.getName());
        place.setAddress(request.getAddress());
        place.setUnitType(request.getUnitType());
        place.setDescription(request.getDescription());
        place.setLatitude(request.getLatitude());
        place.setLongitude(request.getLongitude());
        place.setActive(request.getActive());
        place = placeRepository.save(place);
        return toDto(place);
    }

    private PlaceSummaryDto toDto(Place place) {
        return PlaceSummaryDto.builder()
                .id(place.getId())
                .name(place.getName())
                .address(place.getAddress())
                .unitType(place.getUnitType())
                .description(place.getDescription())
                .latitude(place.getLatitude())
                .longitude(place.getLongitude())
                .active(place.isActive())
                .build();
    }
}
```

- [ ] **Step 7: 테스트 재실행 확인**

```bash
cd backend
./gradlew test --tests "com.example.attempt.controller.AdminPlaceControllerIntegrationTest" 2>&1 | tail -40
```

Expected: 7개 테스트(기존 3 + 신규 4) 전부 PASS

- [ ] **Step 8: 커밋**

```bash
git add backend/src/main/java/com/example/attempt/dto/place/CreatePlaceRequest.java \
        backend/src/main/java/com/example/attempt/dto/place/UpdatePlaceRequest.java \
        backend/src/main/java/com/example/attempt/dto/place/PlaceSummaryDto.java \
        backend/src/main/java/com/example/attempt/controller/AdminPlaceController.java \
        backend/src/test/java/com/example/attempt/controller/AdminPlaceControllerIntegrationTest.java
git commit -m "feat(place-management): add place create/update admin API"
```

---

## Task 3: 비활성 장소를 회원 노출 경로에서 제외

**Files:**
- Modify: `backend/src/main/java/com/example/attempt/repository/PlaceRepository.java`
- Modify: `backend/src/main/java/com/example/attempt/service/PlaceSearchService.java`
- Modify: `backend/src/test/java/com/example/attempt/service/PlaceSearchServiceFallbackTest.java`
- Modify: `backend/src/test/java/com/example/attempt/controller/PlaceControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `Place.isActive()` (Task 1), `PlaceSummaryDto.active` (Task 2)
- Produces: `PlaceRepository.findByUnitTypeAndActiveTrue(UnitType)` — `findByUnitType`를 대체하는 이름. 이 리포지토리 메서드 밖에서 참조하는 곳은 `PlaceSearchService.listByUnitType`뿐

- [ ] **Step 1: 실패하는 통합 테스트 작성**

`backend/src/test/java/com/example/attempt/controller/PlaceControllerIntegrationTest.java`의 마지막 `}` 바로 앞에 추가:

```java
    @Test
    void listByUnitType_excludesInactivePlaces() {
        Place active = new Place("활성장소", "주소1", 35.3, 129.0);
        active.setUnitType(UnitType.PUBLIC_INTEREST);
        placeRepository.save(active);

        Place inactive = new Place("비활성장소", "주소2", 35.4, 129.1);
        inactive.setUnitType(UnitType.PUBLIC_INTEREST);
        inactive.setActive(false);
        placeRepository.save(inactive);

        String accessToken = MemberAuthTestSupport.loginAsMember(
                restTemplate, port, memberRepository, passwordEncoder, "김할매", "01012340003");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        String url = "http://localhost:" + port + "/api/v1/places?unitType=PUBLIC_INTEREST";
        ResponseEntity<Map[]> resp = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Map[].class);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(1, resp.getBody().length);
        assertEquals("활성장소", resp.getBody()[0].get("name"));
    }
```

기존 테스트들은 개수만 확인해 `ResponseEntity<Object[]>`를 썼지만, 이번엔 이름까지 확인해야 하므로 `ResponseEntity<Map[]>`을 사용한다(위 코드에 이미 반영됨). 이 파일은 이미 `import java.util.Map;`을 갖고 있다.

- [ ] **Step 2: 테스트 실행해서 실패 확인**

```bash
cd backend
./gradlew test --tests "com.example.attempt.controller.PlaceControllerIntegrationTest" 2>&1 | tail -40
```

Expected: FAIL — `listByUnitType_excludesInactivePlaces`에서 2건이 반환되어 `assertEquals(1, ...)` 실패

- [ ] **Step 3: `PlaceRepository` 수정**

`backend/src/main/java/com/example/attempt/repository/PlaceRepository.java` 전체를 아래로 교체:

```java
package com.example.attempt.repository;

import com.example.attempt.domain.Place;
import com.example.attempt.domain.UnitType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    List<Place> findByUnitTypeAndActiveTrue(UnitType unitType);

    @Query("""
        SELECT DISTINCT p FROM Place p
        LEFT JOIN JobKeywordSynonym s ON s.place = p
        WHERE p.unitType = :unitType
        AND p.active = true
        AND (
            LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(s.keyword) LIKE LOWER(CONCAT('%', :keyword, '%'))
        )
        """)
    List<Place> searchByUnitTypeAndKeyword(@Param("unitType") UnitType unitType, @Param("keyword") String keyword);
}
```

- [ ] **Step 4: `PlaceSearchService.listByUnitType`/`toDto` 수정**

`backend/src/main/java/com/example/attempt/service/PlaceSearchService.java`에서 두 곳 수정:

```java
    public List<PlaceSummaryDto> listByUnitType(UnitType unitType) {
        return placeRepository.findByUnitTypeAndActiveTrue(unitType).stream()
                .map(this::toDto)
                .toList();
    }
```

그리고 `toDto` 메서드:

```java
    private PlaceSummaryDto toDto(Place place) {
        return PlaceSummaryDto.builder()
                .id(place.getId())
                .name(place.getName())
                .address(place.getAddress())
                .unitType(place.getUnitType())
                .description(place.getDescription())
                .latitude(place.getLatitude())
                .longitude(place.getLongitude())
                .active(place.isActive())
                .build();
    }
```

- [ ] **Step 5: `PlaceSearchServiceFallbackTest`의 mock 메서드명 갱신**

`backend/src/test/java/com/example/attempt/service/PlaceSearchServiceFallbackTest.java`에서 `placeRepository.findByUnitType(` 3곳(51, 65, 82번째 줄 부근)을 전부 `placeRepository.findByUnitTypeAndActiveTrue(`로 치환. (문자열 치환만 하면 되고 나머지 인자/리턴값은 그대로.)

- [ ] **Step 6: 전체 테스트 재실행**

```bash
cd backend
./gradlew test --tests "com.example.attempt.controller.PlaceControllerIntegrationTest" \
                --tests "com.example.attempt.service.PlaceSearchServiceFallbackTest" \
                --tests "com.example.attempt.service.PlaceSearchServiceTest" 2>&1 | tail -40
```

Expected: 전부 PASS

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/example/attempt/repository/PlaceRepository.java \
        backend/src/main/java/com/example/attempt/service/PlaceSearchService.java \
        backend/src/test/java/com/example/attempt/service/PlaceSearchServiceFallbackTest.java \
        backend/src/test/java/com/example/attempt/controller/PlaceControllerIntegrationTest.java
git commit -m "feat(place-management): exclude inactive places from member-facing search"
```

---

## Task 4: 기존 admin-web 장소 드롭다운 2곳에 active 필터 적용

**Files:**
- Modify: `admin-web/src/features/attend-management/types.ts`
- Modify: `admin-web/src/features/member-management/MemberManagementPage.tsx`
- Modify: `admin-web/src/features/member-management/MemberManagementPage.test.tsx`
- Modify: `admin-web/src/features/attend-management/AttendManagementPage.tsx`
- Modify: `admin-web/src/features/attend-management/AttendManagementPage.test.tsx`

**Interfaces:**
- Consumes: `GET /api/admin/places` 응답에 이제 `active`(boolean) 포함 (Task 2)
- Produces: `PlaceSummary.active`(boolean) — Task 6(`PlaceManagementPage`)도 이 타입을 그대로 재사용

- [ ] **Step 1: `PlaceSummary` 타입에 `active` 추가**

`admin-web/src/features/attend-management/types.ts`의 `PlaceSummary` 인터페이스:

```ts
export interface PlaceSummary {
  id: number;
  name: string;
  address: string;
  unitType: string;
  description: string;
  latitude: number;
  longitude: number;
  active: boolean;
}
```

- [ ] **Step 2: 기존 단위 테스트의 mock 데이터에 `active: true` 추가 (필터 적용 전에 먼저 고쳐서 회귀 방지)**

`admin-web/src/features/member-management/MemberManagementPage.test.tsx`의 `PLACES` 상수:

```ts
const PLACES = [
  { id: 1, name: '행복경로당', address: '서울시', unitType: 'PUBLIC_INTEREST', description: '', latitude: 0, longitude: 0, active: true },
];
```

`admin-web/src/features/attend-management/AttendManagementPage.test.tsx`의 `PLACES` 상수:

```ts
const PLACES = [
  { id: 1, name: '행복경로당', address: '서울시', unitType: 'PUBLIC_INTEREST', description: '', latitude: 0, longitude: 0, active: true },
  { id: 2, name: '사랑경로당', address: '서울시', unitType: 'MARKET', description: '', latitude: 0, longitude: 0, active: true },
];
```

- [ ] **Step 3: 두 테스트 실행해서 (아직) 통과하는지 확인 — 이 시점엔 필터가 없으니 그대로 통과해야 함**

```bash
cd admin-web
npx vitest run src/features/member-management/MemberManagementPage.test.tsx \
                src/features/attend-management/AttendManagementPage.test.tsx
```

Expected: 기존과 동일하게 전부 PASS (타입에 필드만 추가했고 아직 필터링 코드는 없음)

- [ ] **Step 4: `MemberManagementPage`의 장소 select에 필터 적용**

`admin-web/src/features/member-management/MemberManagementPage.tsx`에서 `<option value="">장소 선택</option>` 바로 다음 줄:

```tsx
            <option value="">장소 선택</option>
            {places?.filter((place) => place.active).map((place) => (
              <option key={place.id} value={place.id}>
                {place.name}
              </option>
            ))}
```

- [ ] **Step 5: `AttendManagementPage`의 장소 select에 동일하게 필터 적용**

`admin-web/src/features/attend-management/AttendManagementPage.tsx`에서 `<option value="">장소 선택</option>` 바로 다음 줄:

```tsx
            <option value="">장소 선택</option>
            {places?.filter((place) => place.active).map((place) => (
              <option key={place.id} value={place.id}>
                {place.name}
              </option>
            ))}
```

- [ ] **Step 6: 테스트 재실행 (필터 적용 후에도 active:true 데이터라 여전히 PASS해야 함)**

```bash
cd admin-web
npx vitest run src/features/member-management/MemberManagementPage.test.tsx \
                src/features/attend-management/AttendManagementPage.test.tsx
```

Expected: 전부 PASS

- [ ] **Step 7: 커밋**

```bash
git add admin-web/src/features/attend-management/types.ts \
        admin-web/src/features/member-management/MemberManagementPage.tsx \
        admin-web/src/features/member-management/MemberManagementPage.test.tsx \
        admin-web/src/features/attend-management/AttendManagementPage.tsx \
        admin-web/src/features/attend-management/AttendManagementPage.test.tsx
git commit -m "feat(place-management): hide inactive places from existing dropdowns"
```

---

## Task 5: 재사용 가능한 Modal 컴포넌트

**Files:**
- Create: `admin-web/src/components/Modal.tsx`
- Modify: `admin-web/src/index.css`

**Interfaces:**
- Produces: `Modal({ title: string, onClose: () => void, children: ReactNode })` — Task 6이 장소 수정 폼을 감싸는 데 사용

- [ ] **Step 1: CSS 추가**

`admin-web/src/index.css` 맨 끝에 추가:

```css

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
}

.modal-panel {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
  width: 480px;
  max-width: calc(100vw - var(--space-lg) * 2);
  max-height: calc(100vh - var(--space-lg) * 2);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
```

- [ ] **Step 2: `Modal` 컴포넌트 작성**

`admin-web/src/components/Modal.tsx` (신규 파일):

```tsx
import { useEffect } from 'react';
import type { ReactNode } from 'react';

interface ModalProps {
  title: string;
  onClose: () => void;
  children: ReactNode;
}

export function Modal({ title, onClose, children }: ModalProps) {
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        onClose();
      }
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="modal-panel"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <div className="modal-header">
          <h2 style={{ fontSize: 18 }}>{title}</h2>
          <button type="button" className="btn btn-secondary btn-sm" onClick={onClose}>
            닫기
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
```

- [ ] **Step 3: 간단한 렌더 테스트 작성**

`admin-web/src/components/Modal.test.tsx` (신규 파일):

```tsx
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Modal } from './Modal';

describe('Modal', () => {
  afterEach(() => {
    cleanup();
  });

  it('제목과 children을 렌더링한다', () => {
    render(
      <Modal title="테스트 모달" onClose={vi.fn()}>
        <p>내용</p>
      </Modal>
    );
    expect(screen.getByRole('dialog', { name: '테스트 모달' })).toBeInTheDocument();
    expect(screen.getByText('내용')).toBeInTheDocument();
  });

  it('닫기 버튼 클릭 시 onClose를 호출한다', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(
      <Modal title="테스트 모달" onClose={onClose}>
        <p>내용</p>
      </Modal>
    );
    await user.click(screen.getByRole('button', { name: '닫기' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('ESC 키 입력 시 onClose를 호출한다', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(
      <Modal title="테스트 모달" onClose={onClose}>
        <p>내용</p>
      </Modal>
    );
    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
```

- [ ] **Step 4: 테스트 실행**

```bash
cd admin-web
npx vitest run src/components/Modal.test.tsx
```

Expected: 3개 테스트 PASS

- [ ] **Step 5: 커밋**

```bash
git add admin-web/src/index.css admin-web/src/components/Modal.tsx admin-web/src/components/Modal.test.tsx
git commit -m "feat(place-management): add reusable Modal component"
```

---

## Task 6: PlaceManagementPage 화면 (등록/목록/수정/토글) + 라우팅

**Files:**
- Create: `admin-web/src/features/place-management/PlaceManagementPage.tsx`
- Create: `admin-web/src/features/place-management/PlaceManagementPage.test.tsx`
- Modify: `admin-web/src/App.tsx`
- Modify: `admin-web/src/components/AdminLayout.tsx`

**Interfaces:**
- Consumes: `apiFetch`(`admin-web/src/api/client.ts`), `useAuth`(`admin-web/src/features/auth/AuthContext.tsx`), `AdminLayout`, `Modal`(Task 5), `PlaceSummary`(Task 4)
- Produces: 라우트 `/place-management`, 사이드바 메뉴 "장소 관리"

- [ ] **Step 1: 실패하는 컴포넌트 테스트 작성**

`admin-web/src/features/place-management/PlaceManagementPage.test.tsx` (신규 파일):

```tsx
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { PlaceManagementPage } from './PlaceManagementPage';
import * as client from '../../api/client';
import * as authContext from '../auth/AuthContext';

function renderPage() {
  return render(
    <MemoryRouter>
      <PlaceManagementPage />
    </MemoryRouter>
  );
}

vi.mock('../../api/client', async () => {
  const actual = await vi.importActual<typeof client>('../../api/client');
  return { ...actual, apiFetch: vi.fn() };
});

vi.mock('../auth/AuthContext', async () => {
  const actual = await vi.importActual<typeof authContext>('../auth/AuthContext');
  return { ...actual, useAuth: vi.fn() };
});

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const PLACES = [
  {
    id: 1,
    name: '행복경로당',
    address: '서울시',
    unitType: 'PUBLIC_INTEREST',
    description: '',
    latitude: 37.5,
    longitude: 127.0,
    active: true,
  },
];

describe('PlaceManagementPage', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  beforeEach(() => {
    vi.mocked(authContext.useAuth).mockReturnValue({
      isLoggedIn: true,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });
  });

  it('등록 폼 제출 성공 시 목록에 반영한다', async () => {
    vi.mocked(client.apiFetch)
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(
        jsonResponse({
          id: 2,
          name: '새장소',
          address: '부산시',
          unitType: 'MARKET',
          description: '',
          latitude: 35.1,
          longitude: 129.0,
          active: true,
        })
      )
      .mockResolvedValueOnce(
        jsonResponse([
          {
            id: 2,
            name: '새장소',
            address: '부산시',
            unitType: 'MARKET',
            description: '',
            latitude: 35.1,
            longitude: 129.0,
            active: true,
          },
        ])
      );

    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText('이름'), '새장소');
    await user.type(screen.getByLabelText('주소'), '부산시');
    await user.selectOptions(screen.getByRole('combobox', { name: '유형' }), 'MARKET');
    await user.type(screen.getByLabelText('위도'), '35.1');
    await user.type(screen.getByLabelText('경도'), '129.0');
    await user.click(screen.getByRole('button', { name: '등록' }));

    expect(await screen.findByText('새장소')).toBeInTheDocument();
    expect(client.apiFetch).toHaveBeenCalledWith('/api/admin/places', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: '새장소',
        address: '부산시',
        unitType: 'MARKET',
        description: null,
        latitude: 35.1,
        longitude: 129.0,
      }),
    });
  });

  it('필수값 미입력 시 등록 버튼이 비활성화된다', async () => {
    vi.mocked(client.apiFetch).mockResolvedValueOnce(jsonResponse([]));

    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText('이름'), '새장소');

    expect(screen.getByRole('button', { name: '등록' })).toBeDisabled();
  });

  it('목록을 렌더링하고 활성 토글을 누르면 PATCH를 호출한다', async () => {
    vi.mocked(client.apiFetch)
      .mockResolvedValueOnce(jsonResponse(PLACES))
      .mockResolvedValueOnce(jsonResponse({ ...PLACES[0], active: false }))
      .mockResolvedValueOnce(jsonResponse([{ ...PLACES[0], active: false }]));

    const user = userEvent.setup();
    renderPage();

    await screen.findByText('행복경로당');
    await user.click(screen.getByRole('button', { name: '행복경로당 비활성화' }));

    await waitFor(() => {
      expect(client.apiFetch).toHaveBeenCalledWith('/api/admin/places/1', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: '행복경로당',
          address: '서울시',
          unitType: 'PUBLIC_INTEREST',
          description: '',
          latitude: 37.5,
          longitude: 127.0,
          active: false,
        }),
      });
    });

    expect(await screen.findByRole('button', { name: '행복경로당 활성화' })).toBeInTheDocument();
  });

  it('수정 버튼을 누르면 모달에 현재 값이 채워지고, 저장하면 PATCH를 호출한다', async () => {
    vi.mocked(client.apiFetch)
      .mockResolvedValueOnce(jsonResponse(PLACES))
      .mockResolvedValueOnce(jsonResponse({ ...PLACES[0], name: '변경된이름' }))
      .mockResolvedValueOnce(jsonResponse([{ ...PLACES[0], name: '변경된이름' }]));

    const user = userEvent.setup();
    renderPage();

    await screen.findByText('행복경로당');
    await user.click(screen.getByRole('button', { name: '수정' }));

    const dialog = screen.getByRole('dialog', { name: '장소 수정' });
    const nameInput = within(dialog).getByLabelText('이름') as HTMLInputElement;
    expect(nameInput.value).toBe('행복경로당');

    await user.clear(nameInput);
    await user.type(nameInput, '변경된이름');
    await user.click(within(dialog).getByRole('button', { name: '저장' }));

    await waitFor(() => {
      expect(client.apiFetch).toHaveBeenCalledWith('/api/admin/places/1', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: '변경된이름',
          address: '서울시',
          unitType: 'PUBLIC_INTEREST',
          description: '',
          latitude: 37.5,
          longitude: 127.0,
          active: true,
        }),
      });
    });

    expect(await screen.findByText('변경된이름')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

```bash
cd admin-web
npx vitest run src/features/place-management/PlaceManagementPage.test.tsx
```

Expected: FAIL — `PlaceManagementPage` 모듈이 없어 실패

- [ ] **Step 3: `PlaceManagementPage` 구현**

`admin-web/src/features/place-management/PlaceManagementPage.tsx` (신규 파일):

```tsx
import { useCallback, useEffect, useState } from 'react';
import { apiFetch } from '../../api/client';
import { useAuth } from '../auth/AuthContext';
import { AdminLayout } from '../../components/AdminLayout';
import { Modal } from '../../components/Modal';
import type { PlaceSummary } from '../attend-management/types';

const UNIT_TYPE_OPTIONS = [
  { value: 'PUBLIC_INTEREST', label: '공익형' },
  { value: 'MARKET', label: '시장형' },
  { value: 'SOCIAL_SERVICE', label: '사회서비스형' },
];

function unitTypeLabel(value: string): string {
  return UNIT_TYPE_OPTIONS.find((opt) => opt.value === value)?.label ?? value;
}

interface PlaceFormState {
  name: string;
  address: string;
  unitType: string;
  description: string;
  latitude: string;
  longitude: string;
}

const EMPTY_FORM: PlaceFormState = {
  name: '',
  address: '',
  unitType: '',
  description: '',
  latitude: '',
  longitude: '',
};

function formIsValid(f: PlaceFormState): boolean {
  return (
    f.name.trim() !== '' &&
    f.address.trim() !== '' &&
    f.unitType !== '' &&
    f.latitude.trim() !== '' &&
    f.longitude.trim() !== ''
  );
}

function toRequestBody(f: PlaceFormState) {
  return {
    name: f.name,
    address: f.address,
    unitType: f.unitType,
    description: f.description || null,
    latitude: Number(f.latitude),
    longitude: Number(f.longitude),
  };
}

export function PlaceManagementPage() {
  const { logout } = useAuth();
  const [places, setPlaces] = useState<PlaceSummary[] | null>(null);
  const [placesError, setPlacesError] = useState(false);

  const [form, setForm] = useState<PlaceFormState>(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [registerError, setRegisterError] = useState(false);

  const [editingPlace, setEditingPlace] = useState<PlaceSummary | null>(null);
  const [editForm, setEditForm] = useState<PlaceFormState>(EMPTY_FORM);
  const [editSubmitting, setEditSubmitting] = useState(false);
  const [editError, setEditError] = useState(false);

  const loadPlaces = useCallback(async () => {
    setPlacesError(false);
    try {
      const res = await apiFetch('/api/admin/places');
      if (!res.ok) {
        if (res.status === 401) {
          logout();
          return;
        }
        setPlacesError(true);
        return;
      }
      setPlaces(await res.json());
    } catch {
      setPlacesError(true);
    }
  }, [logout]);

  useEffect(() => {
    loadPlaces();
  }, [loadPlaces]);

  const handleRegister = useCallback(async () => {
    if (!formIsValid(form)) {
      return;
    }
    setSubmitting(true);
    setRegisterError(false);
    try {
      const res = await apiFetch('/api/admin/places', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(toRequestBody(form)),
      });
      if (!res.ok) {
        if (res.status === 401) {
          logout();
          return;
        }
        setRegisterError(true);
        return;
      }
      setForm(EMPTY_FORM);
      await loadPlaces();
    } catch {
      setRegisterError(true);
    } finally {
      setSubmitting(false);
    }
  }, [form, logout, loadPlaces]);

  const openEdit = useCallback((place: PlaceSummary) => {
    setEditingPlace(place);
    setEditForm({
      name: place.name,
      address: place.address,
      unitType: place.unitType,
      description: place.description ?? '',
      latitude: String(place.latitude),
      longitude: String(place.longitude),
    });
    setEditError(false);
  }, []);

  const handleEditSave = useCallback(async () => {
    if (!editingPlace || !formIsValid(editForm)) {
      return;
    }
    setEditSubmitting(true);
    setEditError(false);
    try {
      const res = await apiFetch(`/api/admin/places/${editingPlace.id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...toRequestBody(editForm), active: editingPlace.active }),
      });
      if (!res.ok) {
        if (res.status === 401) {
          logout();
          return;
        }
        setEditError(true);
        return;
      }
      setEditingPlace(null);
      await loadPlaces();
    } catch {
      setEditError(true);
    } finally {
      setEditSubmitting(false);
    }
  }, [editingPlace, editForm, logout, loadPlaces]);

  const handleToggleActive = useCallback(
    async (place: PlaceSummary, active: boolean) => {
      try {
        const res = await apiFetch(`/api/admin/places/${place.id}`, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            name: place.name,
            address: place.address,
            unitType: place.unitType,
            description: place.description,
            latitude: place.latitude,
            longitude: place.longitude,
            active,
          }),
        });
        if (!res.ok) {
          if (res.status === 401) {
            logout();
            return;
          }
          setPlacesError(true);
          return;
        }
        await loadPlaces();
      } catch {
        setPlacesError(true);
      }
    },
    [loadPlaces, logout]
  );

  return (
    <AdminLayout>
      <div>
        <h1 style={{ fontSize: 24 }}>장소 관리</h1>
        <p style={{ color: 'var(--color-text-muted)', fontSize: 14, marginTop: 4 }}>
          신규 장소를 등록하고 정보를 관리하세요
        </p>
      </div>

      <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-md)' }}>
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 'var(--space-md)', flexWrap: 'wrap' }}>
          <div className="field">
            <label className="field-label" htmlFor="place-name-input">
              이름
            </label>
            <input
              id="place-name-input"
              className="input"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor="place-address-input">
              주소
            </label>
            <input
              id="place-address-input"
              className="input"
              value={form.address}
              onChange={(e) => setForm({ ...form, address: e.target.value })}
            />
          </div>
          <div className="field" style={{ minWidth: 160 }}>
            <label className="field-label" htmlFor="place-unittype-select">
              유형
            </label>
            <select
              id="place-unittype-select"
              className="input"
              value={form.unitType}
              onChange={(e) => setForm({ ...form, unitType: e.target.value })}
            >
              <option value="">유형 선택</option>
              {UNIT_TYPE_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label className="field-label" htmlFor="place-latitude-input">
              위도
            </label>
            <input
              id="place-latitude-input"
              className="input"
              type="number"
              step="any"
              value={form.latitude}
              onChange={(e) => setForm({ ...form, latitude: e.target.value })}
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor="place-longitude-input">
              경도
            </label>
            <input
              id="place-longitude-input"
              className="input"
              type="number"
              step="any"
              value={form.longitude}
              onChange={(e) => setForm({ ...form, longitude: e.target.value })}
            />
          </div>
        </div>
        <div className="field">
          <label className="field-label" htmlFor="place-description-input">
            설명
          </label>
          <input
            id="place-description-input"
            className="input"
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
        </div>
        <div>
          <button
            className="btn btn-primary"
            onClick={handleRegister}
            disabled={!formIsValid(form) || submitting}
          >
            등록
          </button>
        </div>
      </div>

      {placesError && <p className="alert-error">장소 목록을 불러오지 못했습니다</p>}
      {registerError && <p className="alert-error">장소 등록에 실패했습니다</p>}

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead>
            <tr>
              <th>이름</th>
              <th>주소</th>
              <th>유형</th>
              <th>상태</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {places?.map((place) => (
              <tr key={place.id}>
                <td>{place.name}</td>
                <td>{place.address}</td>
                <td>{unitTypeLabel(place.unitType)}</td>
                <td>
                  <span className={`badge ${place.active ? 'badge-success' : 'badge-neutral'}`}>
                    {place.active ? '활성' : '비활성'}
                  </span>
                </td>
                <td style={{ display: 'flex', gap: 'var(--space-xs)' }}>
                  <button className="btn btn-secondary btn-sm" onClick={() => openEdit(place)}>
                    수정
                  </button>
                  <button
                    className="btn btn-secondary btn-sm"
                    onClick={() => handleToggleActive(place, !place.active)}
                  >
                    {place.name} {place.active ? '비활성화' : '활성화'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {editingPlace && (
        <Modal title="장소 수정" onClose={() => setEditingPlace(null)}>
          <div className="field">
            <label className="field-label" htmlFor="edit-name-input">
              이름
            </label>
            <input
              id="edit-name-input"
              className="input"
              value={editForm.name}
              onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor="edit-address-input">
              주소
            </label>
            <input
              id="edit-address-input"
              className="input"
              value={editForm.address}
              onChange={(e) => setEditForm({ ...editForm, address: e.target.value })}
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor="edit-unittype-select">
              유형
            </label>
            <select
              id="edit-unittype-select"
              className="input"
              value={editForm.unitType}
              onChange={(e) => setEditForm({ ...editForm, unitType: e.target.value })}
            >
              <option value="">유형 선택</option>
              {UNIT_TYPE_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label className="field-label" htmlFor="edit-description-input">
              설명
            </label>
            <input
              id="edit-description-input"
              className="input"
              value={editForm.description}
              onChange={(e) => setEditForm({ ...editForm, description: e.target.value })}
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor="edit-latitude-input">
              위도
            </label>
            <input
              id="edit-latitude-input"
              className="input"
              type="number"
              step="any"
              value={editForm.latitude}
              onChange={(e) => setEditForm({ ...editForm, latitude: e.target.value })}
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor="edit-longitude-input">
              경도
            </label>
            <input
              id="edit-longitude-input"
              className="input"
              type="number"
              step="any"
              value={editForm.longitude}
              onChange={(e) => setEditForm({ ...editForm, longitude: e.target.value })}
            />
          </div>
          {editError && <p className="alert-error">장소 수정에 실패했습니다</p>}
          <div>
            <button
              className="btn btn-primary"
              onClick={handleEditSave}
              disabled={!formIsValid(editForm) || editSubmitting}
            >
              저장
            </button>
          </div>
        </Modal>
      )}
    </AdminLayout>
  );
}
```

- [ ] **Step 4: 라우트 추가**

`admin-web/src/App.tsx`에 import 추가 (`MemberManagementPage` import 바로 아래):

```tsx
import { PlaceManagementPage } from './features/place-management/PlaceManagementPage';
```

그리고 `/member-management` `<Route>` 블록 바로 다음에 추가:

```tsx
          <Route
            path="/place-management"
            element={
              <RequireAuth>
                <PlaceManagementPage />
              </RequireAuth>
            }
          />
```

- [ ] **Step 5: 사이드바 메뉴 추가**

`admin-web/src/components/AdminLayout.tsx`의 `NAV_ITEMS` 배열:

```tsx
const NAV_ITEMS = [
  { to: '/', label: '출석 현황' },
  { to: '/attend-management', label: '일정별 출석 관리' },
  { to: '/member-management', label: '회원 관리' },
  { to: '/place-management', label: '장소 관리' },
];
```

- [ ] **Step 6: 테스트 재실행**

```bash
cd admin-web
npx vitest run src/features/place-management/PlaceManagementPage.test.tsx
npx tsc --noEmit
```

Expected: 4개 테스트 PASS, 타입 에러 없음

- [ ] **Step 7: 커밋**

```bash
git add admin-web/src/features/place-management/PlaceManagementPage.tsx \
        admin-web/src/features/place-management/PlaceManagementPage.test.tsx \
        admin-web/src/App.tsx admin-web/src/components/AdminLayout.tsx
git commit -m "feat(place-management): add place management screen with create/edit/deactivate"
```

---

## Task 7: Playwright e2e

**Files:**
- Create: `admin-web/e2e/place-management.spec.ts`

**Interfaces:**
- Consumes: `login`(`admin-web/e2e/support.ts`), 실제 백엔드 + `e2e-seed` 데이터(`행복 노인 일자리센터`)

- [ ] **Step 1: e2e 스펙 작성**

`admin-web/e2e/place-management.spec.ts` (신규 파일):

```typescript
import { test, expect } from '@playwright/test';
import { login, SEED_PLACE_NAME } from './support';

test.describe('장소 관리', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.getByRole('link', { name: '장소 관리' }).click();
    await expect(page).toHaveURL('/place-management');
  });

  test('시드 장소를 보여준다', async ({ page }) => {
    await expect(page.getByRole('row', { name: new RegExp(SEED_PLACE_NAME) })).toBeVisible();
  });

  test('신규 장소를 등록하면 목록에 나타난다', async ({ page }) => {
    const uniqueName = `테스트장소${Date.now()}`;

    await page.fill('#place-name-input', uniqueName);
    await page.fill('#place-address-input', '서울시 테스트구');
    await page.selectOption('#place-unittype-select', 'MARKET');
    await page.fill('#place-latitude-input', '37.5');
    await page.fill('#place-longitude-input', '127.0');
    await page.click('button:has-text("등록")');

    await expect(page.getByRole('row', { name: new RegExp(uniqueName) })).toBeVisible();
  });

  test('필수값 미입력 시 등록 버튼이 비활성화된다', async ({ page }) => {
    await expect(page.getByRole('button', { name: '등록' })).toBeDisabled();

    await page.fill('#place-name-input', '이름만입력');
    await expect(page.getByRole('button', { name: '등록' })).toBeDisabled();

    await page.fill('#place-address-input', '주소');
    await page.selectOption('#place-unittype-select', 'MARKET');
    await page.fill('#place-latitude-input', '37.5');
    await page.fill('#place-longitude-input', '127.0');
    await expect(page.getByRole('button', { name: '등록' })).toBeEnabled();
  });

  test('수정 모달로 필드를 바꾸면 목록에 반영된다', async ({ page }) => {
    const uniqueName = `수정테스트${Date.now()}`;

    await page.fill('#place-name-input', uniqueName);
    await page.fill('#place-address-input', '수정전주소');
    await page.selectOption('#place-unittype-select', 'MARKET');
    await page.fill('#place-latitude-input', '37.5');
    await page.fill('#place-longitude-input', '127.0');
    await page.click('button:has-text("등록")');

    const row = page.getByRole('row', { name: new RegExp(uniqueName) });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: '수정' }).click();

    const dialog = page.getByRole('dialog', { name: '장소 수정' });
    const updatedName = `${uniqueName}-수정됨`;
    await dialog.locator('#edit-name-input').fill(updatedName);
    await dialog.getByRole('button', { name: '저장' }).click();

    await expect(page.getByRole('row', { name: new RegExp(updatedName) })).toBeVisible();
  });

  test('활성/비활성 토글이 새로고침 후에도 유지되고, 비활성 장소는 회원 등록 드롭다운에서 사라진다', async ({ page }) => {
    const uniqueName = `토글테스트${Date.now()}`;

    await page.fill('#place-name-input', uniqueName);
    await page.fill('#place-address-input', '주소');
    await page.selectOption('#place-unittype-select', 'MARKET');
    await page.fill('#place-latitude-input', '37.5');
    await page.fill('#place-longitude-input', '127.0');
    await page.click('button:has-text("등록")');

    const row = page.getByRole('row', { name: new RegExp(uniqueName) });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: new RegExp(`${uniqueName} 비활성화`) }).click();
    await expect(row.locator('.badge')).toHaveText('비활성');

    await page.reload();
    const rowAfterReload = page.getByRole('row', { name: new RegExp(uniqueName) });
    await expect(rowAfterReload.locator('.badge')).toHaveText('비활성');

    await page.getByRole('link', { name: '회원 관리' }).click();
    await expect(page).toHaveURL('/member-management');
    const placeSelect = page.locator('#member-place-select');
    await expect(placeSelect.locator('option', { hasText: uniqueName })).toHaveCount(0);
  });
});
```

- [ ] **Step 2: 실행**

```bash
cd admin-web
npx playwright test e2e/place-management.spec.ts --reporter=list
```

Expected: 5개 테스트 PASS

- [ ] **Step 3: 전체 admin-web e2e 회귀 확인 (다른 화면에 영향 없는지)**

```bash
cd admin-web
npx playwright test --reporter=list
```

Expected: 기존 13개 + 신규 5개 = 18개 전부 PASS

- [ ] **Step 4: 커밋**

```bash
git add admin-web/e2e/place-management.spec.ts
git commit -m "test(place-management): add e2e coverage for place CRUD screen"
```
