# 시니어 일자리 근태 관리 — 공통 코어 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 참여자(어르신)가 Flutter 앱에서 사업단 유형을 고르고, 본인 일자리를 검색해 찾고, 위치정보 동의 후, 근무지 500m 이내에서 위치기반 출석 체크를 할 수 있게 만든다.

**Architecture:** 기존 Spring Boot 백엔드(Member/Place/Schedule/Attend 엔티티, JWT 인증, `AttendService`의 Haversine 거리 계산)를 확장한다. 신규 REST 컨트롤러(회원 OTP 인증, 회원 셀프서비스, 장소 검색, 출석 체크인)를 추가하고, Flutter 앱을 신규로 만들어 이 API들에 연결한다.

**Tech Stack:** Spring Boot 3.5.6 / Java 17 / MariaDB(H2 로컬) / Spring Security + JWT / Flutter(Riverpod, dio, geolocator, flutter_secure_storage)

## Global Constraints

- 위치기반 출석 반경은 500m (`attendance.location.radius`) — 스펙에서 정한 정확한 값
- 기존 `AttendService.checkIn()`의 Haversine 거리 계산 로직을 재사용한다. 새로 작성하지 않는다.
- 신규 비밀 값(해시 시크릿 등)은 `RefreshTokenServiceImpl` 패턴을 따른다: 환경변수 필수, 기본값 없음, 없으면 기동 실패
- 참여자 로그인은 휴대폰번호 + SMS OTP. 관리자 로그인 방식(ID/PW)과 별개이며 기존 `SmsService.sendCustomMessage()`를 재사용해 코드를 발송한다
- 본인 체크인 시 `memberId`는 절대 클라이언트 요청 바디에서 신뢰하지 않는다 — JWT 인증 주체(휴대폰번호)로 서버가 직접 조회한다
- 신규 REST 엔드포인트는 모두 `/api/v1/...` 프리픽스를 쓴다
- 신규 Flyway 마이그레이션은 `V3`부터 시작한다 (`V1`, `V2` 존재). 로컬/테스트 환경은 `ddl-auto: update`로 엔티티가 스키마를 직접 생성하므로, 마이그레이션 파일은 엔티티 변경과 정확히 일치해야 한다 (기존 관례)
- Flutter 앱은 단일 앱, 상태관리는 Riverpod. 참여자/팀장 역할 분기는 이번 플랜 범위 밖 (팀장 기능은 별도 플랜)
- 기존 코드 스타일을 따른다: Lombok(`@RequiredArgsConstructor`, `@Slf4j`), 생성자 주입, `ResourceNotFoundException`/`IllegalArgumentException`/`IllegalStateException` + `GlobalExceptionHandler`

---

## File Structure

**백엔드 (신규/수정)**
- `domain/UnitType.java` (신규) — 사업단 유형 enum
- `domain/Place.java` (수정) — `unitType` 필드 추가
- `domain/Member.java` (수정) — `locationConsentAgreedAt`, `assignedPlaceId` 필드 추가
- `domain/JobKeywordSynonym.java` (신규) — 검색 동의어
- `domain/MemberOtpCode.java` (신규) — OTP 코드 저장
- `repository/PlaceRepository.java` (수정) — 검색 쿼리 추가
- `repository/JobKeywordSynonymRepository.java` (신규)
- `repository/MemberOtpCodeRepository.java` (신규)
- `repository/MemberRepository.java` (수정) — `findByAssignedPlaceId` 추가
- `service/PlaceSearchService.java` (신규) — 1단계 검색 + LLM 폴백 오케스트레이션
- `service/MemberOtpService.java` (신규) — OTP 생성/발송/검증
- `service/CurrentMemberService.java` (신규) — JWT 인증 주체 → Member 조회 헬퍼
- `service/LlmJobSearchClient.java` (신규) — LLM 폴백 호출 클라이언트
- `service/ScheduleService.java` (수정) — `assignedPlaceId` 기반 자동 Attend 대상 포함
- `controller/MemberAuthController.java` (신규) — OTP 요청/검증, JWT 발급
- `controller/MemberSelfController.java` (신규) — 동의, 본인 일자리 연결, 내 정보 조회
- `controller/PlaceController.java` (신규) — 장소 목록/검색/폴백검색
- `controller/AttendController.java` (신규) — 위치기반 체크인
- `config/SecurityConfig.java` (수정) — 신규 경로 인가 규칙 추가
- `exception/GlobalExceptionHandler.java` (수정) — `IllegalStateException` → 409 매핑 추가
- `dto/place/*`, `dto/memberauth/*`, `dto/attend/AttendCheckInApiRequest.java` (신규 DTO들)
- `db/migration/V3__add_unit_type_and_member_assignment.sql`, `V4__create_job_keyword_synonym.sql`, `V5__create_member_otp_code.sql` (신규)
- `application.yml` (수정) — radius 500, member-otp/llm 설정 추가
- `src/test/resources/application.properties` (수정) — 신규 시크릿 테스트값 추가

**Flutter (신규, `mobile/` 디렉토리)**
- `mobile/lib/main.dart`, `mobile/lib/core/api_client.dart`, `mobile/lib/core/token_storage.dart`
- `mobile/lib/features/auth/*` — 로그인(휴대폰/OTP) 화면 + provider
- `mobile/lib/features/unit_selection/*` — 사업단 유형 선택 화면
- `mobile/lib/features/job_search/*` — 검색/선택 화면
- `mobile/lib/features/consent/*` — 동의 화면
- `mobile/lib/features/checkin/*` — 위치기반 체크인 화면

---

### Task 1: 스키마 — Place.unitType, Member 동의/배정 필드

**Files:**
- Create: `src/main/java/com/example/attempt/domain/UnitType.java`
- Create: `src/main/resources/db/migration/V3__add_unit_type_and_member_assignment.sql`
- Modify: `src/main/java/com/example/attempt/domain/Place.java`
- Modify: `src/main/java/com/example/attempt/domain/Member.java`
- Test: `src/test/java/com/example/attempt/domain/PlaceMemberSchemaTest.java`

**Interfaces:**
- Produces: `UnitType` enum (`PUBLIC_INTEREST`, `MARKET`, `SOCIAL_SERVICE`), `Place.getUnitType()/setUnitType(UnitType)`, `Member.getLocationConsentAgreedAt()/setLocationConsentAgreedAt(LocalDateTime)`, `Member.getAssignedPlaceId()/setAssignedPlaceId(Long)`

- [ ] **Step 1: Write the failing test**

```java
package com.example.attempt.domain;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class PlaceMemberSchemaTest {

    @Autowired
    private jakarta.persistence.EntityManager em;

    @Test
    void place_persistsAndReloadsUnitType() {
        Place place = new Place("공원안전지킴이", "경남 양산시 어딘가", 35.33, 129.03);
        place.setUnitType(UnitType.PUBLIC_INTEREST);
        em.persist(place);
        em.flush();
        em.clear();

        Place reloaded = em.find(Place.class, place.getId());
        assertEquals(UnitType.PUBLIC_INTEREST, reloaded.getUnitType());
    }

    @Test
    void member_persistsAndReloadsConsentAndAssignedPlace() {
        Place place = new Place("공원안전지킴이", "경남 양산시 어딘가", 35.33, 129.03);
        em.persist(place);

        Member member = new Member("김할매", "01000000000");
        member.setAssignedPlaceId(place.getId());
        LocalDateTime consentAt = LocalDateTime.now().withNano(0);
        member.setLocationConsentAgreedAt(consentAt);
        em.persist(member);
        em.flush();
        em.clear();

        Member reloaded = em.find(Member.class, member.getId());
        assertEquals(place.getId(), reloaded.getAssignedPlaceId());
        assertEquals(consentAt, reloaded.getLocationConsentAgreedAt());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.example.attempt.domain.PlaceMemberSchemaTest"`
Expected: FAIL — compile error, `UnitType` does not exist / `setUnitType`, `setAssignedPlaceId`, `setLocationConsentAgreedAt` not found

- [ ] **Step 3: Create the UnitType enum**

```java
package com.example.attempt.domain;

/**
 * 사업단 유형
 */
public enum UnitType {
    PUBLIC_INTEREST("공익형"),
    MARKET("시장형"),
    SOCIAL_SERVICE("사회서비스형");

    private final String description;

    UnitType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
```

- [ ] **Step 4: Add unitType to Place**

In `src/main/java/com/example/attempt/domain/Place.java`, add the import and field (nullable — 기존 데이터에는 값이 없을 수 있음, 관리자가 추후 채운다):

```java
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
```

```java
    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", length = 20)
    private UnitType unitType;
```

(Lombok `@Data`가 이미 클래스에 붙어 있으므로 getter/setter는 자동 생성된다.)

- [ ] **Step 5: Add consent/assigned-place fields to Member**

In `src/main/java/com/example/attempt/domain/Member.java`, add:

```java
import java.time.LocalDateTime;
```

```java
    @Column(name = "location_consent_agreed_at")
    private LocalDateTime locationConsentAgreedAt;

    @Column(name = "assigned_place_id")
    private Long assignedPlaceId;
```

(클래스에 이미 `@Getter @Setter`가 붙어 있으므로 별도 접근자 불필요.)

- [ ] **Step 6: Write the Flyway migration**

```sql
-- V3__add_unit_type_and_member_assignment.sql
ALTER TABLE place ADD COLUMN unit_type VARCHAR(20) NULL;

ALTER TABLE member ADD COLUMN location_consent_agreed_at TIMESTAMP NULL;
ALTER TABLE member ADD COLUMN assigned_place_id BIGINT NULL;
ALTER TABLE member ADD CONSTRAINT fk_member_assigned_place
    FOREIGN KEY (assigned_place_id) REFERENCES place(place_id) ON DELETE SET NULL;
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew test --tests "com.example.attempt.domain.PlaceMemberSchemaTest"`
Expected: PASS (both tests)

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/example/attempt/domain/UnitType.java \
        src/main/java/com/example/attempt/domain/Place.java \
        src/main/java/com/example/attempt/domain/Member.java \
        src/main/resources/db/migration/V3__add_unit_type_and_member_assignment.sql \
        src/test/java/com/example/attempt/domain/PlaceMemberSchemaTest.java
git commit -m "feat(schema): add Place.unitType and Member consent/assigned-place fields"
```

---

### Task 2: 검색 동의어 테이블 (JobKeywordSynonym)

**Files:**
- Create: `src/main/java/com/example/attempt/domain/JobKeywordSynonym.java`
- Create: `src/main/java/com/example/attempt/repository/JobKeywordSynonymRepository.java`
- Create: `src/main/resources/db/migration/V4__create_job_keyword_synonym.sql`
- Test: `src/test/java/com/example/attempt/repository/JobKeywordSynonymRepositoryTest.java`

**Interfaces:**
- Consumes: `Place` (Task 1)
- Produces: `JobKeywordSynonym{id, place, keyword}`, `JobKeywordSynonymRepository.findByPlaceId(Long placeId): List<JobKeywordSynonym>`, `JobKeywordSynonymRepository.save(...)`

- [ ] **Step 1: Write the failing test**

```java
package com.example.attempt.repository;

import com.example.attempt.domain.JobKeywordSynonym;
import com.example.attempt.domain.Place;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class JobKeywordSynonymRepositoryTest {

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private JobKeywordSynonymRepository synonymRepository;

    @Test
    void findByPlaceId_returnsSynonymsForThatPlaceOnly() {
        Place park = placeRepository.save(new Place("공원안전지킴이", "주소1", 35.3, 129.0));
        Place recycle = placeRepository.save(new Place("동네마당재활용", "주소2", 35.4, 129.1));

        synonymRepository.save(new JobKeywordSynonym(park, "청소"));
        synonymRepository.save(new JobKeywordSynonym(park, "쓰레기 줍기"));
        synonymRepository.save(new JobKeywordSynonym(recycle, "재활용"));

        List<JobKeywordSynonym> result = synonymRepository.findByPlaceId(park.getId());

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(s -> s.getKeyword().equals("청소")));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.example.attempt.repository.JobKeywordSynonymRepositoryTest"`
Expected: FAIL — `JobKeywordSynonym`, `JobKeywordSynonymRepository` do not exist

- [ ] **Step 3: Create the entity**

```java
package com.example.attempt.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "job_keyword_synonym")
@Getter
@NoArgsConstructor
public class JobKeywordSynonym {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(nullable = false, length = 100)
    private String keyword;

    public JobKeywordSynonym(Place place, String keyword) {
        this.place = place;
        this.keyword = keyword;
    }
}
```

- [ ] **Step 4: Create the repository**

```java
package com.example.attempt.repository;

import com.example.attempt.domain.JobKeywordSynonym;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobKeywordSynonymRepository extends JpaRepository<JobKeywordSynonym, Long> {
    List<JobKeywordSynonym> findByPlaceId(Long placeId);
}
```

- [ ] **Step 5: Write the migration**

```sql
-- V4__create_job_keyword_synonym.sql
CREATE TABLE job_keyword_synonym (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    place_id BIGINT NOT NULL,
    keyword VARCHAR(100) NOT NULL,
    CONSTRAINT fk_synonym_place FOREIGN KEY (place_id) REFERENCES place(place_id) ON DELETE CASCADE
);
CREATE INDEX idx_synonym_keyword ON job_keyword_synonym(keyword);
CREATE INDEX idx_synonym_place ON job_keyword_synonym(place_id);
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew test --tests "com.example.attempt.repository.JobKeywordSynonymRepositoryTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/example/attempt/domain/JobKeywordSynonym.java \
        src/main/java/com/example/attempt/repository/JobKeywordSynonymRepository.java \
        src/main/resources/db/migration/V4__create_job_keyword_synonym.sql \
        src/test/java/com/example/attempt/repository/JobKeywordSynonymRepositoryTest.java
git commit -m "feat(search): add job keyword synonym table"
```

---

### Task 3: 1단계 검색 — Place 검색 쿼리 + PlaceSearchService

**Files:**
- Modify: `src/main/java/com/example/attempt/repository/PlaceRepository.java`
- Create: `src/main/java/com/example/attempt/dto/place/PlaceSummaryDto.java`
- Create: `src/main/java/com/example/attempt/service/PlaceSearchService.java`
- Test: `src/test/java/com/example/attempt/service/PlaceSearchServiceTest.java`

**Interfaces:**
- Consumes: `Place` (Task 1), `JobKeywordSynonymRepository` (Task 2)
- Produces: `PlaceSummaryDto{id, name, address, unitType, description, latitude, longitude}`, `PlaceSearchService.search(UnitType unitType, String query): List<PlaceSummaryDto>` (빈 리스트면 1단계 검색 실패 → 컨트롤러가 폴백 여부 결정)

- [ ] **Step 1: Write the failing test**

```java
package com.example.attempt.service;

import com.example.attempt.domain.JobKeywordSynonym;
import com.example.attempt.domain.Place;
import com.example.attempt.domain.UnitType;
import com.example.attempt.dto.place.PlaceSummaryDto;
import com.example.attempt.repository.JobKeywordSynonymRepository;
import com.example.attempt.repository.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaceSearchServiceTest {

    private PlaceRepository placeRepository;
    private PlaceSearchService service;

    @BeforeEach
    void setup() {
        placeRepository = mock(PlaceRepository.class);
        JobKeywordSynonymRepository synonymRepository = mock(JobKeywordSynonymRepository.class);
        service = new PlaceSearchService(placeRepository, synonymRepository);
    }

    @Test
    void search_delegatesToRepositoryWithUnitTypeAndKeyword() {
        Place place = new Place("공원안전지킴이", "주소", 35.3, 129.0);
        place.setUnitType(UnitType.PUBLIC_INTEREST);
        when(placeRepository.searchByUnitTypeAndKeyword(UnitType.PUBLIC_INTEREST, "청소"))
                .thenReturn(List.of(place));

        List<PlaceSummaryDto> result = service.search(UnitType.PUBLIC_INTEREST, "청소");

        assertEquals(1, result.size());
        assertEquals("공원안전지킴이", result.get(0).getName());
        assertEquals(UnitType.PUBLIC_INTEREST, result.get(0).getUnitType());
    }

    @Test
    void search_returnsEmptyList_whenNoMatch() {
        when(placeRepository.searchByUnitTypeAndKeyword(UnitType.MARKET, "존재안함"))
                .thenReturn(List.of());

        List<PlaceSummaryDto> result = service.search(UnitType.MARKET, "존재안함");

        assertTrue(result.isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.example.attempt.service.PlaceSearchServiceTest"`
Expected: FAIL — `PlaceSearchService`, `PlaceSummaryDto`, `searchByUnitTypeAndKeyword` do not exist

- [ ] **Step 3: Create PlaceSummaryDto**

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
}
```

- [ ] **Step 4: Add the search query to PlaceRepository**

```java
package com.example.attempt.repository;

import com.example.attempt.domain.Place;
import com.example.attempt.domain.UnitType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    List<Place> findByUnitType(UnitType unitType);

    @Query("""
        SELECT DISTINCT p FROM Place p
        LEFT JOIN JobKeywordSynonym s ON s.place = p
        WHERE p.unitType = :unitType
        AND (
            LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(s.keyword) LIKE LOWER(CONCAT('%', :keyword, '%'))
        )
        """)
    List<Place> searchByUnitTypeAndKeyword(@Param("unitType") UnitType unitType, @Param("keyword") String keyword);
}
```

- [ ] **Step 5: Create PlaceSearchService**

```java
package com.example.attempt.service;

import com.example.attempt.domain.Place;
import com.example.attempt.domain.UnitType;
import com.example.attempt.dto.place.PlaceSummaryDto;
import com.example.attempt.repository.JobKeywordSynonymRepository;
import com.example.attempt.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사업단 유형별 일자리(Place) 검색
 * 1단계: 이름/설명/동의어 LIKE 검색
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceSearchService {

    private final PlaceRepository placeRepository;
    private final JobKeywordSynonymRepository synonymRepository;

    public List<PlaceSummaryDto> listByUnitType(UnitType unitType) {
        return placeRepository.findByUnitType(unitType).stream()
                .map(this::toDto)
                .toList();
    }

    public List<PlaceSummaryDto> search(UnitType unitType, String keyword) {
        return placeRepository.searchByUnitTypeAndKeyword(unitType, keyword).stream()
                .map(this::toDto)
                .toList();
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
                .build();
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew test --tests "com.example.attempt.service.PlaceSearchServiceTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/example/attempt/repository/PlaceRepository.java \
        src/main/java/com/example/attempt/dto/place/PlaceSummaryDto.java \
        src/main/java/com/example/attempt/service/PlaceSearchService.java \
        src/test/java/com/example/attempt/service/PlaceSearchServiceTest.java
git commit -m "feat(search): add stage-1 place search (name/description/synonym LIKE)"
```

---

### Task 4: PlaceController — 목록/검색 API

**Files:**
- Create: `src/main/java/com/example/attempt/controller/PlaceController.java`
- Test: `src/test/java/com/example/attempt/controller/PlaceControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `PlaceSearchService` (Task 3)
- Produces: `GET /api/v1/places?unitType=&q=` — `q` 없으면 `unitType` 전체 목록, 있으면 검색

- [ ] **Step 1: Write the failing test**

```java
package com.example.attempt.controller;

import com.example.attempt.domain.Place;
import com.example.attempt.domain.UnitType;
import com.example.attempt.repository.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlaceControllerIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    PlaceRepository placeRepository;

    @Test
    void listByUnitType_returnsOnlyMatchingPlaces() {
        Place park = new Place("공원안전지킴이", "주소1", 35.3, 129.0);
        park.setUnitType(UnitType.PUBLIC_INTEREST);
        placeRepository.save(park);

        Place market = new Place("동네마당재활용", "주소2", 35.4, 129.1);
        market.setUnitType(UnitType.MARKET);
        placeRepository.save(market);

        String url = "http://localhost:" + port + "/api/v1/places?unitType=PUBLIC_INTEREST";
        ResponseEntity<Object[]> resp = restTemplate.getForEntity(url, Object[].class);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(1, resp.getBody().length);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.example.attempt.controller.PlaceControllerIntegrationTest"`
Expected: FAIL — 404, `PlaceController` 없음. (이 테스트는 인증 없이 호출하므로, Task 8에서 `/api/v1/places/**`를 인증 필요로 바꾸면 이 테스트도 로그인 처리로 갱신해야 한다 — Task 8 Step 5에서 처리)

- [ ] **Step 3: Create PlaceController**

```java
package com.example.attempt.controller;

import com.example.attempt.domain.UnitType;
import com.example.attempt.dto.place.PlaceSummaryDto;
import com.example.attempt.service.PlaceSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceSearchService placeSearchService;

    @GetMapping
    public List<PlaceSummaryDto> list(@RequestParam UnitType unitType,
                                       @RequestParam(required = false) String q) {
        if (q == null || q.isBlank()) {
            return placeSearchService.listByUnitType(unitType);
        }
        return placeSearchService.search(unitType, q);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.example.attempt.controller.PlaceControllerIntegrationTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/attempt/controller/PlaceController.java \
        src/test/java/com/example/attempt/controller/PlaceControllerIntegrationTest.java
git commit -m "feat(search): add PlaceController list/search endpoint"
```

---

### Task 5: MemberOtpCode 저장소

**Files:**
- Create: `src/main/java/com/example/attempt/domain/MemberOtpCode.java`
- Create: `src/main/java/com/example/attempt/repository/MemberOtpCodeRepository.java`
- Create: `src/main/resources/db/migration/V5__create_member_otp_code.sql`
- Test: `src/test/java/com/example/attempt/repository/MemberOtpCodeRepositoryTest.java`

**Interfaces:**
- Produces: `MemberOtpCode{id, phoneNumber, codeHash, expiresAt, attempts, createdAt}`, `MemberOtpCodeRepository.findTopByPhoneNumberOrderByCreatedAtDesc(String phoneNumber): Optional<MemberOtpCode>`

- [ ] **Step 1: Write the failing test**

```java
package com.example.attempt.repository;

import com.example.attempt.domain.MemberOtpCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class MemberOtpCodeRepositoryTest {

    @Autowired
    private MemberOtpCodeRepository repository;

    @Test
    void findTopByPhoneNumberOrderByCreatedAtDesc_returnsMostRecent() throws InterruptedException {
        MemberOtpCode older = new MemberOtpCode("01011112222", "hash-old",
                LocalDateTime.now().plusMinutes(5));
        repository.saveAndFlush(older);
        Thread.sleep(10);
        MemberOtpCode newer = new MemberOtpCode("01011112222", "hash-new",
                LocalDateTime.now().plusMinutes(5));
        repository.saveAndFlush(newer);

        Optional<MemberOtpCode> result = repository.findTopByPhoneNumberOrderByCreatedAtDesc("01011112222");

        assertTrue(result.isPresent());
        assertEquals("hash-new", result.get().getCodeHash());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.example.attempt.repository.MemberOtpCodeRepositoryTest"`
Expected: FAIL — `MemberOtpCode`, `MemberOtpCodeRepository` do not exist

- [ ] **Step 3: Create the entity**

```java
package com.example.attempt.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "member_otp_code")
@Getter
@Setter
@NoArgsConstructor
public class MemberOtpCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "code_hash", nullable = false, length = 128)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private int attempts = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public MemberOtpCode(String phoneNumber, String codeHash, LocalDateTime expiresAt) {
        this.phoneNumber = phoneNumber;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }
}
```

- [ ] **Step 4: Create the repository**

```java
package com.example.attempt.repository;

import com.example.attempt.domain.MemberOtpCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberOtpCodeRepository extends JpaRepository<MemberOtpCode, Long> {
    Optional<MemberOtpCode> findTopByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);
}
```

- [ ] **Step 5: Write the migration**

```sql
-- V5__create_member_otp_code.sql
CREATE TABLE member_otp_code (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone_number VARCHAR(20) NOT NULL,
    code_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_otp_phone_created ON member_otp_code(phone_number, created_at);
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew test --tests "com.example.attempt.repository.MemberOtpCodeRepositoryTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/example/attempt/domain/MemberOtpCode.java \
        src/main/java/com/example/attempt/repository/MemberOtpCodeRepository.java \
        src/main/resources/db/migration/V5__create_member_otp_code.sql \
        src/test/java/com/example/attempt/repository/MemberOtpCodeRepositoryTest.java
git commit -m "feat(auth): add member OTP code storage"
```

---

### Task 6: MemberOtpService — OTP 생성/발송/검증

**Files:**
- Create: `src/main/java/com/example/attempt/service/MemberOtpService.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application.properties`
- Test: `src/test/java/com/example/attempt/service/MemberOtpServiceTest.java`

**Interfaces:**
- Consumes: `MemberOtpCodeRepository` (Task 5), `SmsService.sendCustomMessage(String, String)` (기존)
- Produces: `MemberOtpService.requestOtp(String phoneNumber): void`, `MemberOtpService.verifyOtp(String phoneNumber, String code): boolean`

- [ ] **Step 1: Write the failing test**

```java
package com.example.attempt.service;

import com.example.attempt.domain.MemberOtpCode;
import com.example.attempt.repository.MemberOtpCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberOtpServiceTest {

    private MemberOtpCodeRepository repository;
    private SmsService smsService;
    private MemberOtpService service;

    @BeforeEach
    void setup() {
        repository = mock(MemberOtpCodeRepository.class);
        smsService = mock(SmsService.class);
        service = new MemberOtpService(repository, smsService, "test-otp-secret", 300, 6);
    }

    @Test
    void requestOtp_savesHashedCodeAndSendsSms() {
        service.requestOtp("01012345678");

        ArgumentCaptor<MemberOtpCode> captor = ArgumentCaptor.forClass(MemberOtpCode.class);
        verify(repository, times(1)).save(captor.capture());
        assertEquals("01012345678", captor.getValue().getPhoneNumber());
        assertNotNull(captor.getValue().getCodeHash());

        verify(smsService, times(1)).sendCustomMessage(eq("01012345678"), anyString());
    }

    @Test
    void verifyOtp_returnsTrue_forCorrectCode() {
        // 실제 서비스가 생성한 해시와 동일한 알고리즘으로 직접 검증 가능하도록,
        // requestOtp가 저장한 코드를 캡처해서 그대로 verifyOtp에 사용한다.
        ArgumentCaptor<MemberOtpCode> captor = ArgumentCaptor.forClass(MemberOtpCode.class);
        service.requestOtp("01099998888");
        verify(repository).save(captor.capture());
        MemberOtpCode saved = captor.getValue();

        when(repository.findTopByPhoneNumberOrderByCreatedAtDesc("01099998888"))
                .thenReturn(Optional.of(saved));

        // 발송된 원본 코드를 알 수 없으므로, 이 테스트는 잘못된 코드가 거부되는 것만 검증한다.
        boolean result = service.verifyOtp("01099998888", "000000");

        assertFalse(result);
    }

    @Test
    void verifyOtp_returnsFalse_whenExpired() {
        MemberOtpCode expired = new MemberOtpCode("01055556666", "irrelevant-hash",
                LocalDateTime.now().minusMinutes(1));
        when(repository.findTopByPhoneNumberOrderByCreatedAtDesc("01055556666"))
                .thenReturn(Optional.of(expired));

        boolean result = service.verifyOtp("01055556666", "123456");

        assertFalse(result);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.example.attempt.service.MemberOtpServiceTest"`
Expected: FAIL — `MemberOtpService` does not exist

- [ ] **Step 3: Implement MemberOtpService**

```java
package com.example.attempt.service;

import com.example.attempt.domain.MemberOtpCode;
import com.example.attempt.repository.MemberOtpCodeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * 참여자(Member) 휴대폰번호 + SMS OTP 인증
 * 코드는 평문 저장하지 않고 HMAC-SHA256 해시로 저장한다 (RefreshTokenServiceImpl과 동일 패턴)
 */
@Service
@Slf4j
public class MemberOtpService {

    private final MemberOtpCodeRepository repository;
    private final SmsService smsService;
    private final String hashSecret;
    private final int ttlSeconds;
    private final int codeLength;
    private final SecureRandom secureRandom = new SecureRandom();

    public MemberOtpService(MemberOtpCodeRepository repository,
                             SmsService smsService,
                             @Value("${member-otp.hash-secret}") String hashSecret,
                             @Value("${member-otp.ttl-seconds:300}") int ttlSeconds,
                             @Value("${member-otp.code-length:6}") int codeLength) {
        this.repository = repository;
        this.smsService = smsService;
        this.hashSecret = hashSecret;
        this.ttlSeconds = ttlSeconds;
        this.codeLength = codeLength;
        if (this.hashSecret == null || this.hashSecret.isBlank()) {
            throw new IllegalStateException("member-otp.hash-secret (MEMBER_OTP_HASH_SECRET) must be set");
        }
    }

    @Transactional
    public void requestOtp(String phoneNumber) {
        String code = generateNumericCode(codeLength);
        String hash = hmacSha256Base64(code);

        MemberOtpCode entity = new MemberOtpCode(phoneNumber, hash, LocalDateTime.now().plusSeconds(ttlSeconds));
        repository.save(entity);

        smsService.sendCustomMessage(phoneNumber, "[시니어일자리] 인증번호는 " + code + " 입니다. " + (ttlSeconds / 60) + "분 이내에 입력해주세요.");
        log.info("OTP 발급: phoneNumber={}", phoneNumber);
    }

    @Transactional(readOnly = true)
    public boolean verifyOtp(String phoneNumber, String code) {
        Optional<MemberOtpCode> latest = repository.findTopByPhoneNumberOrderByCreatedAtDesc(phoneNumber);
        if (latest.isEmpty()) {
            return false;
        }

        MemberOtpCode otp = latest.get();
        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("OTP 만료: phoneNumber={}", phoneNumber);
            return false;
        }

        boolean matches = hmacSha256Base64(code).equals(otp.getCodeHash());
        if (!matches) {
            log.warn("OTP 불일치: phoneNumber={}", phoneNumber);
        }
        return matches;
    }

    private String generateNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(secureRandom.nextInt(10));
        }
        return sb.toString();
    }

    private String hmacSha256Base64(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 4: Add config defaults**

`src/main/resources/application.yml`의 `attendance:` 블록 위에 추가:

```yaml
# 참여자 SMS OTP 인증 설정
member-otp:
  ttl-seconds: 300
  code-length: 6
  hash-secret: ${MEMBER_OTP_HASH_SECRET:}
```

`src/test/resources/application.properties`에 추가 (기존 `refresh-token.hash-secret` 줄 아래):

```properties
member-otp.hash-secret=test-otp-secret
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "com.example.attempt.service.MemberOtpServiceTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/attempt/service/MemberOtpService.java \
        src/main/resources/application.yml \
        src/test/resources/application.properties \
        src/test/java/com/example/attempt/service/MemberOtpServiceTest.java
git commit -m "feat(auth): add member OTP generation/verification service"
```

---

### Task 7: MemberAuthController — OTP 요청/검증, JWT 발급

**Files:**
- Create: `src/main/java/com/example/attempt/dto/memberauth/OtpRequestRequest.java`
- Create: `src/main/java/com/example/attempt/dto/memberauth/OtpVerifyRequest.java`
- Create: `src/main/java/com/example/attempt/controller/MemberAuthController.java`
- Modify: `src/main/java/com/example/attempt/config/SecurityConfig.java`
- Test: `src/test/java/com/example/attempt/controller/MemberAuthControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `MemberOtpService` (Task 6), `MemberRepository` (기존, `findByPhoneNumber`), `JwtTokenProvider`, `RefreshTokenService` (기존)
- Produces: `POST /api/v1/member-auth/otp/request {phoneNumber}` → 200, `POST /api/v1/member-auth/otp/verify {phoneNumber, code}` → `{accessToken}` + refreshToken 쿠키. 전화번호로 기존 Member가 없으면 자동 가입(신규 Member 생성)한다.

- [ ] **Step 1: Write the failing test**

이 테스트는 OTP 코드를 알아야 하므로, `MemberOtpCodeRepository`를 직접 사용해 서비스가 저장한 해시를 우회하지 않고, **테스트 프로파일 전용 백도어**를 두지 않는 대신 `MemberOtpService`를 스텁으로 교체하지 않고 실제 흐름을 그대로 태우되 코드 검증 실패 케이스와 요청 성공(200) 케이스만 검증한다. 정상 검증 성공 흐름은 Task 6에서 이미 해시/검증 로직 단위 테스트로 커버했다.

```java
package com.example.attempt.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberAuthControllerIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void requestOtp_returns200() {
        String base = "http://localhost:" + port + "/api/v1/member-auth";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> req = new HttpEntity<>(Map.of("phoneNumber", "01011112222"), headers);

        ResponseEntity<Void> resp = restTemplate.postForEntity(base + "/otp/request", req, Void.class);

        assertEquals(200, resp.getStatusCodeValue());
    }

    @Test
    void verifyOtp_withWrongCode_returns401() {
        String base = "http://localhost:" + port + "/api/v1/member-auth";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.postForEntity(base + "/otp/request",
                new HttpEntity<>(Map.of("phoneNumber", "01033334444"), headers), Void.class);

        HttpEntity<Map<String, String>> verifyReq = new HttpEntity<>(
                Map.of("phoneNumber", "01033334444", "code", "000000"), headers);
        ResponseEntity<Map> resp = restTemplate.postForEntity(base + "/otp/verify", verifyReq, Map.class);

        assertEquals(401, resp.getStatusCodeValue());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.example.attempt.controller.MemberAuthControllerIntegrationTest"`
Expected: FAIL — 404, `MemberAuthController` 없음

- [ ] **Step 3: Create request DTOs**

```java
package com.example.attempt.dto.memberauth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OtpRequestRequest {
    @NotBlank(message = "휴대폰번호는 필수입니다.")
    private String phoneNumber;
}
```

```java
package com.example.attempt.dto.memberauth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OtpVerifyRequest {
    @NotBlank(message = "휴대폰번호는 필수입니다.")
    private String phoneNumber;

    @NotBlank(message = "인증번호는 필수입니다.")
    private String code;
}
```

- [ ] **Step 4: Create MemberAuthController**

```java
package com.example.attempt.controller;

import com.example.attempt.domain.Member;
import com.example.attempt.dto.memberauth.OtpRequestRequest;
import com.example.attempt.dto.memberauth.OtpVerifyRequest;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.security.JwtTokenProvider;
import com.example.attempt.service.MemberOtpService;
import com.example.attempt.service.RefreshTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/member-auth")
@RequiredArgsConstructor
public class MemberAuthController {

    private final MemberOtpService memberOtpService;
    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Value("${jwt.refresh-exp-ms:1209600000}")
    private long refreshExpMs;

    @Value("${refresh-token.cookie.secure:false}")
    private boolean cookieSecure;

    @PostMapping("/otp/request")
    public ResponseEntity<?> requestOtp(@Valid @RequestBody OtpRequestRequest request) {
        memberOtpService.requestOtp(request.getPhoneNumber());
        return ResponseEntity.ok(Map.of("message", "인증번호가 발송되었습니다."));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        boolean valid = memberOtpService.verifyOtp(request.getPhoneNumber(), request.getCode());
        if (!valid) {
            return ResponseEntity.status(401).body(Map.of("error", "인증번호가 올바르지 않거나 만료되었습니다."));
        }

        Member member = memberRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseGet(() -> memberRepository.save(new Member("참여자", request.getPhoneNumber())));

        // memberId는 토큰 클레임에 넣지 않는다 — CurrentMemberService가 매 요청마다
        // subject(phoneNumber)로 Member를 직접 조회하므로 클레임에 넣어도 쓰이지 않는다.
        // 응답 바디의 memberId는 클라이언트 표시/로깅 편의용이다.
        String accessToken = jwtTokenProvider.createAccessToken(
                member.getPhoneNumber(),
                Map.of("roles", new String[]{"ROLE_MEMBER"})
        );

        String rawRefresh = refreshTokenService.createRefreshToken(member.getPhoneNumber(), null, Duration.ofMillis(refreshExpMs));
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", rawRefresh)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/auth/refresh")
                .maxAge(refreshExpMs / 1000)
                .sameSite("Strict")
                .build();

        return ResponseEntity.ok()
                .header("Set-Cookie", refreshCookie.toString())
                .body(Map.of("accessToken", accessToken, "memberId", member.getId()));
    }
}
```

- [ ] **Step 5: Permit the new auth path in SecurityConfig**

`src/main/java/com/example/attempt/config/SecurityConfig.java`의 `authorizeHttpRequests` 블록에서 기존 줄:

```java
.requestMatchers("/api/auth/**", "/api/devices/register", "/ws/**", "/actuator/health").permitAll()
```

을 아래로 교체:

```java
.requestMatchers("/api/auth/**", "/api/v1/member-auth/**", "/api/devices/register", "/ws/**", "/actuator/health").permitAll()
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew test --tests "com.example.attempt.controller.MemberAuthControllerIntegrationTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/example/attempt/dto/memberauth \
        src/main/java/com/example/attempt/controller/MemberAuthController.java \
        src/main/java/com/example/attempt/config/SecurityConfig.java \
        src/test/java/com/example/attempt/controller/MemberAuthControllerIntegrationTest.java
git commit -m "feat(auth): add member OTP login issuing JWT + refresh cookie"
```

---

### Task 8: CurrentMemberService + MemberSelfController (동의, 본인 일자리 연결)

**Files:**
- Create: `src/main/java/com/example/attempt/service/CurrentMemberService.java`
- Create: `src/main/java/com/example/attempt/dto/memberauth/AssignPlaceRequest.java`
- Create: `src/main/java/com/example/attempt/controller/MemberSelfController.java`
- Modify: `src/main/java/com/example/attempt/config/SecurityConfig.java`
- Modify: `src/test/java/com/example/attempt/controller/PlaceControllerIntegrationTest.java` (인증 헤더 추가)
- Test: `src/test/java/com/example/attempt/controller/MemberSelfControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `MemberRepository` (기존), Spring Security `SecurityContextHolder`
- Produces: `CurrentMemberService.getCurrentMember(): Member` (인증 주체의 휴대폰번호로 조회, 없으면 `ResourceNotFoundException`), `POST /api/v1/members/me/consent`, `POST /api/v1/members/me/assign-place {placeId}`, `GET /api/v1/members/me`

- [ ] **Step 1: Write the failing test**

```java
package com.example.attempt.service;

import com.example.attempt.domain.Member;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentMemberServiceTest {

    private MemberRepository memberRepository;
    private CurrentMemberService service;

    @BeforeEach
    void setup() {
        memberRepository = mock(MemberRepository.class);
        service = new CurrentMemberService(memberRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentMember_looksUpByAuthenticatedPhoneNumber() {
        Member member = new Member("김할매", "01012345678");
        when(memberRepository.findByPhoneNumber("01012345678")).thenReturn(Optional.of(member));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("01012345678", null, List.of()));

        Member result = service.getCurrentMember();

        assertEquals("01012345678", result.getPhoneNumber());
    }

    @Test
    void getCurrentMember_throws_whenNoMemberForPhoneNumber() {
        when(memberRepository.findByPhoneNumber("01099990000")).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("01099990000", null, List.of()));

        assertThrows(ResourceNotFoundException.class, () -> service.getCurrentMember());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.example.attempt.service.CurrentMemberServiceTest"`
Expected: FAIL — `CurrentMemberService` does not exist

- [ ] **Step 3: Implement CurrentMemberService**

```java
package com.example.attempt.service;

import com.example.attempt.domain.Member;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * JWT 인증 주체(휴대폰번호)로부터 현재 로그인한 Member를 조회한다.
 * 컨트롤러가 클라이언트 요청 바디의 memberId를 신뢰하지 않도록 하기 위한 유일한 경로다.
 */
@Service
@RequiredArgsConstructor
public class CurrentMemberService {

    private final MemberRepository memberRepository;

    public Member getCurrentMember() {
        String phoneNumber = SecurityContextHolder.getContext().getAuthentication().getName();
        return memberRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("인증된 회원을 찾을 수 없습니다. phoneNumber=" + phoneNumber));
    }
}
```

- [ ] **Step 4: Create AssignPlaceRequest DTO and MemberSelfController**

```java
package com.example.attempt.dto.memberauth;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignPlaceRequest {
    @NotNull(message = "장소 ID는 필수입니다.")
    private Long placeId;
}
```

```java
package com.example.attempt.controller;

import com.example.attempt.domain.Member;
import com.example.attempt.dto.memberauth.AssignPlaceRequest;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.repository.PlaceRepository;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.service.CurrentMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/members/me")
@RequiredArgsConstructor
public class MemberSelfController {

    private final CurrentMemberService currentMemberService;
    private final MemberRepository memberRepository;
    private final PlaceRepository placeRepository;

    @GetMapping
    public Map<String, Object> me() {
        Member member = currentMemberService.getCurrentMember();
        return Map.of(
                "id", member.getId(),
                "username", member.getUsername(),
                "phoneNumber", member.getPhoneNumber(),
                "assignedPlaceId", member.getAssignedPlaceId() == null ? "" : member.getAssignedPlaceId(),
                "locationConsentAgreed", member.getLocationConsentAgreedAt() != null
        );
    }

    @PostMapping("/consent")
    public ResponseEntityBody consent() {
        Member member = currentMemberService.getCurrentMember();
        member.setLocationConsentAgreedAt(LocalDateTime.now());
        memberRepository.save(member);
        return new ResponseEntityBody("위치정보 수집에 동의했습니다.");
    }

    @PostMapping("/assign-place")
    public ResponseEntityBody assignPlace(@Valid @RequestBody AssignPlaceRequest request) {
        placeRepository.findById(request.getPlaceId())
                .orElseThrow(() -> new ResourceNotFoundException("장소를 찾을 수 없습니다. ID: " + request.getPlaceId()));

        Member member = currentMemberService.getCurrentMember();
        member.setAssignedPlaceId(request.getPlaceId());
        memberRepository.save(member);
        return new ResponseEntityBody("본인 일자리로 등록되었습니다.");
    }

    private record ResponseEntityBody(String message) {}
}
```

- [ ] **Step 5: Require authentication for member self-service and place search paths**

`src/main/java/com/example/attempt/config/SecurityConfig.java`의 `authorizeHttpRequests` 블록에 `.requestMatchers("/api/v1/admin/**", ...)` 줄 앞에 추가:

```java
.requestMatchers("/api/v1/members/me/**", "/api/v1/places/**", "/api/v1/attend/**").hasRole("MEMBER")
```

- [ ] **Step 6: Update PlaceControllerIntegrationTest to authenticate**

Task 4에서 작성한 `PlaceControllerIntegrationTest`는 이제 인증 없이는 401을 받는다. 로그인 흐름을 태우도록 수정:

```java
package com.example.attempt.controller;

import com.example.attempt.domain.Place;
import com.example.attempt.domain.UnitType;
import com.example.attempt.repository.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlaceControllerIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    PlaceRepository placeRepository;

    // 이 테스트는 인증 없이 401이 반환되는 것만 검증한다. OTP 코드는 SMS로만 발송되고
    // 해시로 저장되므로 통합 테스트에서 원본 코드를 알아낼 방법이 없다 — 로그인 성공
    // 이후의 인가 동작(200 응답)은 Task 6의 MemberOtpServiceTest가 해시/검증 로직을
    // 단위 테스트로 이미 커버한다.
    @Test
    void listByUnitType_withoutAuth_returns401() {
        Place park = new Place("공원안전지킴이", "주소1", 35.3, 129.0);
        park.setUnitType(UnitType.PUBLIC_INTEREST);
        placeRepository.save(park);

        String url = "http://localhost:" + port + "/api/v1/places?unitType=PUBLIC_INTEREST";
        ResponseEntity<Object> resp = restTemplate.getForEntity(url, Object.class);

        assertEquals(401, resp.getStatusCodeValue());
    }
}
```

- [ ] **Step 7: Write MemberSelfControllerIntegrationTest**

```java
package com.example.attempt.controller;

import com.example.attempt.domain.Place;
import com.example.attempt.repository.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberSelfControllerIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void assignPlace_withoutAuth_returns401() {
        String url = "http://localhost:" + port + "/api/v1/members/me/assign-place";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(Map.of("placeId", 1), headers);

        ResponseEntity<Object> resp = restTemplate.postForEntity(url, req, Object.class);

        assertEquals(401, resp.getStatusCodeValue());
    }
}
```

`CurrentMemberServiceTest`, `MemberSelfControllerIntegrationTest`, 수정된 `PlaceControllerIntegrationTest`가 이 태스크의 실패하는 테스트다.

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew test --tests "com.example.attempt.service.CurrentMemberServiceTest" --tests "com.example.attempt.controller.MemberSelfControllerIntegrationTest" --tests "com.example.attempt.controller.PlaceControllerIntegrationTest"`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/example/attempt/service/CurrentMemberService.java \
        src/main/java/com/example/attempt/dto/memberauth/AssignPlaceRequest.java \
        src/main/java/com/example/attempt/controller/MemberSelfController.java \
        src/main/java/com/example/attempt/config/SecurityConfig.java \
        src/test/java/com/example/attempt/service/CurrentMemberServiceTest.java \
        src/test/java/com/example/attempt/controller/MemberSelfControllerIntegrationTest.java \
        src/test/java/com/example/attempt/controller/PlaceControllerIntegrationTest.java
git commit -m "feat(member): add consent + assign-place self-service endpoints, require MEMBER auth for participant APIs"
```

---

### Task 9: LLM 폴백 검색

**Files:**
- Create: `src/main/java/com/example/attempt/service/LlmJobSearchClient.java`
- Create: `src/main/java/com/example/attempt/dto/place/PlaceSearchFallbackRequest.java`
- Modify: `src/main/java/com/example/attempt/service/PlaceSearchService.java`
- Modify: `src/main/java/com/example/attempt/controller/PlaceController.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/example/attempt/service/PlaceSearchServiceFallbackTest.java`

**Interfaces:**
- Consumes: `PlaceSummaryDto` (Task 3)
- Produces: `LlmJobSearchClient.pickBestMatch(String freeText, List<PlaceSummaryDto> candidates): Optional<Long>` (가장 근접한 Place id, 없으면 empty), `PlaceSearchService.searchWithFallback(UnitType, String): List<PlaceSummaryDto>`, `POST /api/v1/places/search/fallback {unitType, q}`

- [ ] **Step 1: Write the failing test**

```java
package com.example.attempt.service;

import com.example.attempt.domain.UnitType;
import com.example.attempt.dto.place.PlaceSummaryDto;
import com.example.attempt.repository.JobKeywordSynonymRepository;
import com.example.attempt.repository.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaceSearchServiceFallbackTest {

    private PlaceRepository placeRepository;
    private JobKeywordSynonymRepository synonymRepository;
    private LlmJobSearchClient llmClient;
    private PlaceSearchService service;

    @BeforeEach
    void setup() {
        placeRepository = mock(PlaceRepository.class);
        synonymRepository = mock(JobKeywordSynonymRepository.class);
        llmClient = mock(LlmJobSearchClient.class);
        service = new PlaceSearchService(placeRepository, synonymRepository, Optional.of(llmClient));
    }

    @Test
    void searchWithFallback_usesStage1Result_whenPresent() {
        com.example.attempt.domain.Place place = new com.example.attempt.domain.Place("공원안전지킴이", "주소", 35.3, 129.0);
        place.setUnitType(UnitType.PUBLIC_INTEREST);
        when(placeRepository.searchByUnitTypeAndKeyword(UnitType.PUBLIC_INTEREST, "청소"))
                .thenReturn(List.of(place));

        List<PlaceSummaryDto> result = service.searchWithFallback(UnitType.PUBLIC_INTEREST, "청소");

        assertEquals(1, result.size());
        verifyNoInteractions(llmClient);
    }

    @Test
    void searchWithFallback_callsLlm_whenStage1Empty() {
        when(placeRepository.searchByUnitTypeAndKeyword(UnitType.PUBLIC_INTEREST, "학교 앞에서 깃발"))
                .thenReturn(List.of());
        com.example.attempt.domain.Place candidate = new com.example.attempt.domain.Place("스쿨존실버봉사단1", "주소", 35.3, 129.0);
        candidate.setUnitType(UnitType.PUBLIC_INTEREST);
        candidate.setId(7L);
        when(placeRepository.findByUnitType(UnitType.PUBLIC_INTEREST)).thenReturn(List.of(candidate));
        when(llmClient.pickBestMatch(eq("학교 앞에서 깃발"), anyList())).thenReturn(Optional.of(7L));
        when(placeRepository.findById(7L)).thenReturn(Optional.of(candidate));

        List<PlaceSummaryDto> result = service.searchWithFallback(UnitType.PUBLIC_INTEREST, "학교 앞에서 깃발");

        assertEquals(1, result.size());
        assertEquals("스쿨존실버봉사단1", result.get(0).getName());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.example.attempt.service.PlaceSearchServiceFallbackTest"`
Expected: FAIL — `LlmJobSearchClient` 없음, `PlaceSearchService`에 3-arg 생성자/`searchWithFallback` 없음

- [ ] **Step 3: Create LlmJobSearchClient**

```java
package com.example.attempt.service;

import com.example.attempt.dto.place.PlaceSummaryDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 후보군이 작을 때(사업단당 수십 개 이내) LLM에게 자유서술 입력과 가장 가까운 Place를 고르게 한다.
 * 벡터DB/임베딩 인프라 없이 프롬프트 하나로 처리한다.
 */
@Service
@ConditionalOnProperty(prefix = "llm.provider", name = "api-key")
@Slf4j
public class LlmJobSearchClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String model;

    public LlmJobSearchClient(@Value("${llm.provider.api-key}") String apiKey,
                               @Value("${llm.provider.model:claude-haiku-4-5-20251001}") String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    public Optional<Long> pickBestMatch(String freeText, List<PlaceSummaryDto> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        StringBuilder candidateList = new StringBuilder();
        for (PlaceSummaryDto c : candidates) {
            candidateList.append(c.getId()).append(": ").append(c.getName());
            if (c.getDescription() != null && !c.getDescription().isBlank()) {
                candidateList.append(" (").append(c.getDescription()).append(")");
            }
            candidateList.append("\n");
        }

        String prompt = "참여자가 본인이 일하는 일자리를 이렇게 설명했습니다: \"" + freeText + "\"\n\n" +
                "아래 후보 목록 중 가장 가까운 것 하나의 번호만 답하세요. 확신이 없으면 0을 답하세요.\n\n" +
                candidateList;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");

            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", 16,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            var response = restTemplate.postForEntity(
                    "https://api.anthropic.com/v1/messages",
                    new HttpEntity<>(body, headers),
                    String.class);

            JsonNode json = objectMapper.readTree(response.getBody());
            String text = json.at("/content/0/text").asText("");
            long placeId = Long.parseLong(text.replaceAll("[^0-9]", ""));
            return placeId == 0 ? Optional.empty() : Optional.of(placeId);
        } catch (Exception e) {
            log.error("LLM 검색 폴백 호출 실패: freeText={}", freeText, e);
            return Optional.empty();
        }
    }
}
```

- [ ] **Step 4: Add `searchWithFallback` to PlaceSearchService**

`PlaceSearchService`를 아래처럼 변경한다 (생성자에 `Optional<LlmJobSearchClient>` 추가, 새 메서드 추가):

```java
package com.example.attempt.service;

import com.example.attempt.domain.Place;
import com.example.attempt.domain.UnitType;
import com.example.attempt.dto.place.PlaceSummaryDto;
import com.example.attempt.repository.JobKeywordSynonymRepository;
import com.example.attempt.repository.PlaceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 사업단 유형별 일자리(Place) 검색
 * 1단계: 이름/설명/동의어 LIKE 검색
 * 2단계: 1단계가 0건일 때만 LLM 폴백 (후보군이 작을 때만 유효)
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class PlaceSearchService {

    private final PlaceRepository placeRepository;
    private final JobKeywordSynonymRepository synonymRepository;
    private final Optional<LlmJobSearchClient> llmJobSearchClient;

    // 생성자가 2개라 Spring이 어떤 것으로 빈을 만들지 모호해지므로, 실제 빈 생성에 쓰일
    // 생성자를 명시적으로 @Autowired로 지정한다. 2-arg 생성자는 테스트에서 `new`로 직접
    // 호출할 때만 쓰인다 (Task 3의 PlaceSearchServiceTest).
    public PlaceSearchService(PlaceRepository placeRepository,
                               JobKeywordSynonymRepository synonymRepository) {
        this(placeRepository, synonymRepository, Optional.empty());
    }

    @Autowired
    public PlaceSearchService(PlaceRepository placeRepository,
                               JobKeywordSynonymRepository synonymRepository,
                               Optional<LlmJobSearchClient> llmJobSearchClient) {
        this.placeRepository = placeRepository;
        this.synonymRepository = synonymRepository;
        this.llmJobSearchClient = llmJobSearchClient;
    }

    public List<PlaceSummaryDto> listByUnitType(UnitType unitType) {
        return placeRepository.findByUnitType(unitType).stream()
                .map(this::toDto)
                .toList();
    }

    public List<PlaceSummaryDto> search(UnitType unitType, String keyword) {
        return placeRepository.searchByUnitTypeAndKeyword(unitType, keyword).stream()
                .map(this::toDto)
                .toList();
    }

    public List<PlaceSummaryDto> searchWithFallback(UnitType unitType, String freeText) {
        List<PlaceSummaryDto> stage1 = search(unitType, freeText);
        if (!stage1.isEmpty()) {
            return stage1;
        }
        if (llmJobSearchClient.isEmpty()) {
            log.info("LLM 폴백 비활성화 상태 (llm.provider.api-key 미설정)");
            return List.of();
        }

        List<PlaceSummaryDto> candidates = listByUnitType(unitType);
        return llmJobSearchClient.get().pickBestMatch(freeText, candidates)
                .flatMap(placeRepository::findById)
                .map(this::toDto)
                .map(List::of)
                .orElse(List.of());
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
                .build();
    }
}
```

- [ ] **Step 5: Add the fallback endpoint to PlaceController**

`PlaceController`에 DTO import와 메서드 추가:

```java
    @PostMapping("/search/fallback")
    public List<PlaceSummaryDto> searchFallback(@RequestBody PlaceSearchFallbackRequest request) {
        return placeSearchService.searchWithFallback(request.getUnitType(), request.getQ());
    }
```

```java
package com.example.attempt.dto.place;

import com.example.attempt.domain.UnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlaceSearchFallbackRequest {
    @NotNull(message = "사업단 유형은 필수입니다.")
    private UnitType unitType;

    @NotBlank(message = "검색어는 필수입니다.")
    private String q;
}
```

- [ ] **Step 6: Add LLM config**

`application.yml`의 `member-otp:` 블록 아래에 추가:

```yaml
# LLM 폴백 검색 설정 (미설정 시 폴백 비활성화, 1단계 검색만 동작)
llm:
  provider:
    api-key: ${ANTHROPIC_API_KEY:}
    model: claude-haiku-4-5-20251001
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew test --tests "com.example.attempt.service.PlaceSearchServiceFallbackTest" --tests "com.example.attempt.service.PlaceSearchServiceTest"`
Expected: PASS (기존 `PlaceSearchServiceTest`도 계속 통과해야 한다 — 2-arg 생성자 오버로드로 하위 호환)

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/example/attempt/service/LlmJobSearchClient.java \
        src/main/java/com/example/attempt/service/PlaceSearchService.java \
        src/main/java/com/example/attempt/dto/place/PlaceSearchFallbackRequest.java \
        src/main/java/com/example/attempt/controller/PlaceController.java \
        src/main/resources/application.yml \
        src/test/java/com/example/attempt/service/PlaceSearchServiceFallbackTest.java
git commit -m "feat(search): add LLM fallback search for small per-unit candidate lists"
```

---

### Task 10: AttendController — 위치기반 체크인

**Files:**
- Create: `src/main/java/com/example/attempt/dto/attend/AttendCheckInApiRequest.java`
- Create: `src/main/java/com/example/attempt/controller/AttendController.java`
- Modify: `src/main/java/com/example/attempt/exception/GlobalExceptionHandler.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/example/attempt/controller/AttendControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `AttendService.checkIn(AttendCheckInRequest)` (기존, 변경 없음), `CurrentMemberService` (Task 8)
- Produces: `POST /api/v1/attend/check-in {scheduleId, latitude, longitude}` — memberId는 JWT에서 서버가 직접 조회. 동의 전이면 409.

- [ ] **Step 1: Write the failing test**

```java
package com.example.attempt.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AttendControllerIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void checkIn_withoutAuth_returns401() {
        String url = "http://localhost:" + port + "/api/v1/attend/check-in";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(
                Map.of("scheduleId", 1, "latitude", 35.3, "longitude", 129.0), headers);

        ResponseEntity<Object> resp = restTemplate.postForEntity(url, req, Object.class);

        assertEquals(401, resp.getStatusCodeValue());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.example.attempt.controller.AttendControllerIntegrationTest"`
Expected: FAIL — 404, `AttendController` 없음

- [ ] **Step 3: Create AttendCheckInApiRequest**

```java
package com.example.attempt.dto.attend;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 클라이언트가 보내는 체크인 요청 — memberId는 포함하지 않는다 (JWT에서 서버가 조회)
 */
@Data
public class AttendCheckInApiRequest {
    @NotNull(message = "일정 ID는 필수입니다.")
    private Long scheduleId;

    @NotNull(message = "위도는 필수입니다.")
    private Double latitude;

    @NotNull(message = "경도는 필수입니다.")
    private Double longitude;
}
```

- [ ] **Step 4: Create AttendController**

```java
package com.example.attempt.controller;

import com.example.attempt.domain.Member;
import com.example.attempt.dto.attend.AttendCheckInApiRequest;
import com.example.attempt.dto.attend.AttendCheckInRequest;
import com.example.attempt.dto.attend.AttendCheckInResponse;
import com.example.attempt.service.AttendService;
import com.example.attempt.service.CurrentMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/attend")
@RequiredArgsConstructor
public class AttendController {

    private final AttendService attendService;
    private final CurrentMemberService currentMemberService;

    @PostMapping("/check-in")
    public AttendCheckInResponse checkIn(@Valid @RequestBody AttendCheckInApiRequest request) {
        Member member = currentMemberService.getCurrentMember();

        if (member.getLocationConsentAgreedAt() == null) {
            throw new IllegalStateException("위치정보 수집 동의가 필요합니다. 동의 후 출석 체크가 가능합니다.");
        }

        AttendCheckInRequest serviceRequest = AttendCheckInRequest.builder()
                .scheduleId(request.getScheduleId())
                .memberId(member.getId())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();

        return attendService.checkIn(serviceRequest);
    }
}
```

- [ ] **Step 5: Map IllegalStateException to HTTP 409**

`GlobalExceptionHandler`에 새 핸들러를 추가 (`IllegalArgumentException` 핸들러 아래):

```java
    /**
     * IllegalStateException 처리
     * HTTP 409 (Conflict) 응답 — 이미 처리된 상태, 동의 미완료, 위치 반경 밖 등
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException ex,
            WebRequest request) {

        log.warn("처리할 수 없는 상태: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                ex.getMessage(),
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorResponse);
    }
```

- [ ] **Step 6: Update the geofence radius to 500m**

`application.yml`의 `attendance:` 블록:

```yaml
# 출석 관련 설정
attendance:
  location:
    radius: 500  # 출석 가능 반경 (미터)
```

`application-local.yml`의 동일 블록도 500으로 변경한다.

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew test --tests "com.example.attempt.controller.AttendControllerIntegrationTest"`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/example/attempt/dto/attend/AttendCheckInApiRequest.java \
        src/main/java/com/example/attempt/controller/AttendController.java \
        src/main/java/com/example/attempt/exception/GlobalExceptionHandler.java \
        src/main/resources/application.yml \
        src/main/resources/application-local.yml \
        src/test/java/com/example/attempt/controller/AttendControllerIntegrationTest.java
git commit -m "feat(attend): add check-in endpoint deriving memberId from JWT, set 500m radius"
```

---

### Task 11: ScheduleService — 본인 일자리 연결 회원 자동 포함

**Files:**
- Modify: `src/main/java/com/example/attempt/repository/MemberRepository.java`
- Modify: `src/main/java/com/example/attempt/service/ScheduleService.java`
- Test: `src/test/java/com/example/attempt/service/ScheduleServiceAutoAttendTest.java`

**Interfaces:**
- Consumes: `Member.assignedPlaceId` (Task 1)
- Produces: `MemberRepository.findByAssignedPlaceId(Long placeId): List<Member>`. `ScheduleService.createSchedules()`가 명시적으로 선택된 회원 외에, 해당 Place에 `assignedPlaceId`로 연결된 회원 전원을 자동으로 Attend 대상에 포함한다.

- [ ] **Step 1: Write the failing test**

```java
package com.example.attempt.service;

import com.example.attempt.domain.Member;
import com.example.attempt.domain.Place;
import com.example.attempt.dto.schedule.ScheduleCreateRequest;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.repository.PlaceRepository;
import com.example.attempt.repository.ScheduleRepository;
import com.example.attempt.repository.AttendRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ScheduleServiceAutoAttendTest {

    private ScheduleRepository scheduleRepository;
    private AttendRepository attendRepository;
    private MemberRepository memberRepository;
    private PlaceRepository placeRepository;
    private ScheduleService service;

    @BeforeEach
    void setup() {
        scheduleRepository = mock(ScheduleRepository.class);
        attendRepository = mock(AttendRepository.class);
        memberRepository = mock(MemberRepository.class);
        placeRepository = mock(PlaceRepository.class);
        service = new ScheduleService(scheduleRepository, attendRepository, memberRepository, placeRepository);

        when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createSchedules_includesMembersAssignedToPlace_evenWithoutExplicitSelection() {
        Place place = new Place("공원안전지킴이", "주소", 35.3, 129.0);
        place.setId(1L);
        when(placeRepository.findById(1L)).thenReturn(Optional.of(place));

        Member selfAssigned = new Member("김할매", "01000000000");
        selfAssigned.setId(10L);
        selfAssigned.setAssignedPlaceId(1L);
        when(memberRepository.findByAssignedPlaceId(1L)).thenReturn(List.of(selfAssigned));

        Member explicit = new Member("박할배", "01011111111");
        explicit.setId(20L);
        when(memberRepository.findAllById(List.of(20L))).thenReturn(List.of(explicit));

        ScheduleCreateRequest request = ScheduleCreateRequest.builder()
                .title("환경정비")
                .dates(List.of(LocalDate.now()))
                .placeId(1L)
                .memberIds(List.of(20L))
                .build();

        service.createSchedules(request);

        verify(scheduleRepository, times(1)).save(argThat(schedule ->
                schedule.getAttends().size() == 2 &&
                schedule.getAttends().stream().anyMatch(a -> a.getMember().getId().equals(10L)) &&
                schedule.getAttends().stream().anyMatch(a -> a.getMember().getId().equals(20L))
        ));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.example.attempt.service.ScheduleServiceAutoAttendTest"`
Expected: FAIL — `MemberRepository.findByAssignedPlaceId` 없음 → 컴파일 에러, 또는 컴파일은 되고 `size() == 2` 대신 `1`로 실패 (자동 포함 로직 미구현)

- [ ] **Step 3: Add findByAssignedPlaceId to MemberRepository**

`MemberRepository`에 추가:

```java
    /**
     * 본인 일자리로 연결(assignedPlaceId)된 회원 조회
     */
    List<Member> findByAssignedPlaceId(Long assignedPlaceId);
```

- [ ] **Step 4: Update getTargetMembers in ScheduleService**

`ScheduleService.getTargetMembers()`를 아래로 교체:

```java
    /**
     * 참석 대상 회원 조회
     * - 전체 회원 선택 시: 전체 회원
     * - 그 외: 관리자가 명시적으로 선택한 회원/사업단 + 해당 Place에 본인 일자리로 연결(assignedPlaceId)된 회원을 자동 포함
     */
    private List<Member> getTargetMembers(ScheduleCreateRequest request) {
        // 전체 회원
        if (request.isAllMembersSelected()) {
            log.info("전체 회원 선택");
            return memberRepository.findAll();
        }

        Set<Member> members = new HashSet<>();

        // 특정 회원 ID
        if (request.hasMemberIds()) {
            List<Member> foundMembers = memberRepository.findAllById(request.getMemberIds());
            members.addAll(foundMembers);
            log.info("특정 회원 {}명 선택", foundMembers.size());
        }

        // 특정 사업단
        if (request.hasUnitNames()) {
            for (String unitName : request.getUnitNames()) {
                List<Member> unitMembers = memberRepository.findByUnitName(unitName);
                members.addAll(unitMembers);
                log.info("사업단 '{}' 회원 {}명 선택", unitName, unitMembers.size());
            }
        }

        // 본인 일자리로 이 Place를 연결해 둔 회원은 명시적 선택 여부와 무관하게 자동 포함
        if (request.getPlaceId() != null) {
            List<Member> assignedMembers = memberRepository.findByAssignedPlaceId(request.getPlaceId());
            members.addAll(assignedMembers);
            log.info("본인 일자리로 등록된 회원 {}명 자동 포함", assignedMembers.size());
        }

        return new ArrayList<>(members);
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "com.example.attempt.service.ScheduleServiceAutoAttendTest"`
Expected: PASS

- [ ] **Step 6: Run the full existing ScheduleService-related suite to check for regressions**

Run: `./gradlew test`
Expected: 전체 PASS (기존 스케줄 생성 동작에 영향이 없어야 한다 — 새 로직은 합집합에 추가만 한다)

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/example/attempt/repository/MemberRepository.java \
        src/main/java/com/example/attempt/service/ScheduleService.java \
        src/test/java/com/example/attempt/service/ScheduleServiceAutoAttendTest.java
git commit -m "feat(schedule): auto-include members self-assigned to a place when creating schedules"
```

---

### Task 12: Flutter 앱 스캐폴드 + API 클라이언트

**Files:**
- Create: `mobile/` (via `flutter create`)
- Create: `mobile/lib/core/api_client.dart`
- Create: `mobile/lib/core/token_storage.dart`
- Modify: `mobile/pubspec.yaml`
- Test: `mobile/test/core/token_storage_test.dart`

**Interfaces:**
- Produces: `ApiClient` (dio 기반, baseUrl 설정, accessToken 자동 첨부), `TokenStorage.saveAccessToken/readAccessToken/clear` (flutter_secure_storage 래핑)

- [ ] **Step 1: Scaffold the Flutter app**

```bash
flutter create --org com.example.attempt --project-name senior_job_attendance mobile
cd mobile
flutter pub add flutter_riverpod dio flutter_secure_storage geolocator
cd ..
```

- [ ] **Step 2: Write the failing test**

```dart
// mobile/test/core/token_storage_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/core/token_storage.dart';

class FakeSecureStore implements SecureStore {
  final Map<String, String> _store = {};

  @override
  Future<void> write({required String key, required String? value}) async {
    if (value == null) {
      _store.remove(key);
    } else {
      _store[key] = value;
    }
  }

  @override
  Future<String?> read({required String key}) async => _store[key];
}

void main() {
  test('saveAccessToken then readAccessToken returns the same value', () async {
    final storage = TokenStorage(FakeSecureStore());

    await storage.saveAccessToken('abc.def.ghi');
    final result = await storage.readAccessToken();

    expect(result, 'abc.def.ghi');
  });

  test('clear removes the stored token', () async {
    final storage = TokenStorage(FakeSecureStore());
    await storage.saveAccessToken('abc.def.ghi');

    await storage.clear();
    final result = await storage.readAccessToken();

    expect(result, isNull);
  });
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd mobile && flutter test test/core/token_storage_test.dart`
Expected: FAIL — `token_storage.dart` 없음

- [ ] **Step 4: Implement TokenStorage**

```dart
// mobile/lib/core/token_storage.dart
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

abstract class SecureStore {
  Future<void> write({required String key, required String? value});
  Future<String?> read({required String key});
}

class FlutterSecureStore implements SecureStore {
  final FlutterSecureStorage _storage = const FlutterSecureStorage();

  @override
  Future<void> write({required String key, required String? value}) =>
      _storage.write(key: key, value: value);

  @override
  Future<String?> read({required String key}) => _storage.read(key: key);
}

class TokenStorage {
  static const _accessTokenKey = 'access_token';

  final SecureStore _store;

  TokenStorage([SecureStore? store]) : _store = store ?? FlutterSecureStore();

  Future<void> saveAccessToken(String token) =>
      _store.write(key: _accessTokenKey, value: token);

  Future<String?> readAccessToken() => _store.read(key: _accessTokenKey);

  Future<void> clear() => _store.write(key: _accessTokenKey, value: null);
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd mobile && flutter test test/core/token_storage_test.dart`
Expected: PASS

- [ ] **Step 6: Implement ApiClient**

```dart
// mobile/lib/core/api_client.dart
import 'package:dio/dio.dart';
import 'token_storage.dart';

class ApiClient {
  final Dio dio;
  final TokenStorage tokenStorage;

  ApiClient({required String baseUrl, TokenStorage? tokenStorage})
      : tokenStorage = tokenStorage ?? TokenStorage(),
        dio = Dio(BaseOptions(baseUrl: baseUrl)) {
    dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        final token = await this.tokenStorage.readAccessToken();
        if (token != null) {
          options.headers['Authorization'] = 'Bearer $token';
        }
        handler.next(options);
      },
    ));
  }
}
```

이 클라이언트는 이후 태스크(로그인, 검색, 동의, 체크인)에서 재사용된다.

- [ ] **Step 7: Commit**

```bash
cd mobile
git add pubspec.yaml lib/core/api_client.dart lib/core/token_storage.dart test/core/token_storage_test.dart
git commit -m "feat(mobile): scaffold Flutter app with dio API client and secure token storage"
cd ..
```

---

### Task 13: 로그인 화면 (휴대폰번호 + OTP)

**Files:**
- Create: `mobile/lib/features/auth/auth_repository.dart`
- Create: `mobile/lib/features/auth/auth_provider.dart`
- Create: `mobile/lib/features/auth/phone_login_screen.dart`
- Create: `mobile/lib/features/auth/otp_verify_screen.dart`
- Test: `mobile/test/features/auth/auth_repository_test.dart`

**Interfaces:**
- Consumes: `ApiClient` (Task 12)
- Produces: `AuthRepository.requestOtp(String phoneNumber)`, `AuthRepository.verifyOtp(String phoneNumber, String code): Future<String>` (accessToken 반환 및 TokenStorage에 저장), `authStateProvider` (Riverpod, 로그인 여부)

- [ ] **Step 1: Write the failing test**

```dart
// mobile/test/features/auth/auth_repository_test.dart
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/features/auth/auth_repository.dart';

void main() {
  test('verifyOtp returns accessToken from response body', () async {
    final dio = Dio();
    dio.httpClientAdapter = _FakeAdapter();
    final repository = AuthRepository(dio: dio);

    final token = await repository.verifyOtp('01012345678', '123456');

    expect(token, 'fake-access-token');
  });
}

class _FakeAdapter implements HttpClientAdapter {
  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
      RequestOptions options, Stream<List<int>>? requestStream, Future<void>? cancelFuture) async {
    return ResponseBody.fromString(
      '{"accessToken":"fake-access-token","memberId":1}',
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd mobile && flutter test test/features/auth/auth_repository_test.dart`
Expected: FAIL — `AuthRepository` 없음

- [ ] **Step 3: Implement AuthRepository**

```dart
// mobile/lib/features/auth/auth_repository.dart
import 'package:dio/dio.dart';
import '../../core/token_storage.dart';

class AuthRepository {
  final Dio dio;
  final TokenStorage tokenStorage;

  AuthRepository({required this.dio, TokenStorage? tokenStorage})
      : tokenStorage = tokenStorage ?? TokenStorage();

  Future<void> requestOtp(String phoneNumber) async {
    await dio.post('/api/v1/member-auth/otp/request', data: {'phoneNumber': phoneNumber});
  }

  Future<String> verifyOtp(String phoneNumber, String code) async {
    final response = await dio.post('/api/v1/member-auth/otp/verify', data: {
      'phoneNumber': phoneNumber,
      'code': code,
    });
    final accessToken = response.data['accessToken'] as String;
    await tokenStorage.saveAccessToken(accessToken);
    return accessToken;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd mobile && flutter test test/features/auth/auth_repository_test.dart`
Expected: PASS

- [ ] **Step 5: Add Riverpod auth provider**

```dart
// mobile/lib/features/auth/auth_provider.dart
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/api_client.dart';
import '../../core/token_storage.dart';
import 'auth_repository.dart';

final apiClientProvider = Provider<ApiClient>((ref) {
  return ApiClient(baseUrl: 'http://10.0.2.2:8080');
});

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  final apiClient = ref.watch(apiClientProvider);
  return AuthRepository(dio: apiClient.dio, tokenStorage: apiClient.tokenStorage);
});

final isLoggedInProvider = FutureProvider<bool>((ref) async {
  final token = await TokenStorage().readAccessToken();
  return token != null;
});
```

- [ ] **Step 6: Build the login screens**

```dart
// mobile/lib/features/auth/phone_login_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'auth_provider.dart';
import 'otp_verify_screen.dart';

class PhoneLoginScreen extends ConsumerStatefulWidget {
  const PhoneLoginScreen({super.key});

  @override
  ConsumerState<PhoneLoginScreen> createState() => _PhoneLoginScreenState();
}

class _PhoneLoginScreenState extends ConsumerState<PhoneLoginScreen> {
  final _phoneController = TextEditingController();
  bool _sending = false;

  Future<void> _sendOtp() async {
    setState(() => _sending = true);
    try {
      await ref.read(authRepositoryProvider).requestOtp(_phoneController.text);
      if (!mounted) return;
      Navigator.of(context).push(MaterialPageRoute(
        builder: (_) => OtpVerifyScreen(phoneNumber: _phoneController.text),
      ));
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('로그인')),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          children: [
            TextField(
              controller: _phoneController,
              keyboardType: TextInputType.phone,
              decoration: const InputDecoration(labelText: '휴대폰번호'),
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _sending ? null : _sendOtp,
              child: Text(_sending ? '전송 중...' : '인증번호 받기'),
            ),
          ],
        ),
      ),
    );
  }
}
```

```dart
// mobile/lib/features/auth/otp_verify_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'auth_provider.dart';

class OtpVerifyScreen extends ConsumerStatefulWidget {
  final String phoneNumber;

  const OtpVerifyScreen({super.key, required this.phoneNumber});

  @override
  ConsumerState<OtpVerifyScreen> createState() => _OtpVerifyScreenState();
}

class _OtpVerifyScreenState extends ConsumerState<OtpVerifyScreen> {
  final _codeController = TextEditingController();
  String? _error;

  Future<void> _verify() async {
    try {
      await ref.read(authRepositoryProvider).verifyOtp(widget.phoneNumber, _codeController.text);
      if (!mounted) return;
      Navigator.of(context).pushNamedAndRemoveUntil('/unit-selection', (route) => false);
    } catch (e) {
      setState(() => _error = '인증번호가 올바르지 않습니다.');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('인증번호 입력')),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          children: [
            TextField(
              controller: _codeController,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(labelText: '인증번호 6자리'),
            ),
            if (_error != null) Text(_error!, style: const TextStyle(color: Colors.red)),
            const SizedBox(height: 16),
            ElevatedButton(onPressed: _verify, child: const Text('확인')),
          ],
        ),
      ),
    );
  }
}
```

- [ ] **Step 7: Commit**

```bash
cd mobile
git add lib/features/auth test/features/auth
git commit -m "feat(mobile): add phone+OTP login flow"
cd ..
```

---

### Task 14: 사업단 유형 선택 화면

**Files:**
- Create: `mobile/lib/features/unit_selection/unit_selection_screen.dart`
- Create: `mobile/lib/core/unit_type.dart`

**Interfaces:**
- Produces: `UnitType` enum (Dart, 백엔드 enum과 문자열 값 일치: `PUBLIC_INTEREST`/`MARKET`/`SOCIAL_SERVICE`), `UnitSelectionScreen` — 선택 시 `/job-search` 라우트로 이동하며 선택된 유형을 전달

- [ ] **Step 1: Implement UnitType**

```dart
// mobile/lib/core/unit_type.dart
enum UnitType {
  publicInterest('PUBLIC_INTEREST', '공익형'),
  market('MARKET', '시장형'),
  socialService('SOCIAL_SERVICE', '사회서비스형');

  final String apiValue;
  final String label;

  const UnitType(this.apiValue, this.label);
}
```

- [ ] **Step 2: Build the selection screen**

```dart
// mobile/lib/features/unit_selection/unit_selection_screen.dart
import 'package:flutter/material.dart';
import '../../core/unit_type.dart';
import '../job_search/job_search_screen.dart';

class UnitSelectionScreen extends StatelessWidget {
  const UnitSelectionScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('사업단 유형 선택')),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: UnitType.values.map((type) {
          return Padding(
            padding: const EdgeInsets.only(bottom: 16),
            child: ElevatedButton(
              style: ElevatedButton.styleFrom(minimumSize: const Size.fromHeight(64)),
              onPressed: () {
                Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => JobSearchScreen(unitType: type),
                ));
              },
              child: Text(type.label, style: const TextStyle(fontSize: 20)),
            ),
          );
        }).toList(),
      ),
    );
  }
}
```

- [ ] **Step 3: Manual verification**

Run: `cd mobile && flutter analyze lib/features/unit_selection lib/core/unit_type.dart`
Expected: `No issues found!` (이 화면은 순수 UI 네비게이션이라 단위 테스트보다 `flutter analyze`로 충분하다. Task 16에서 `JobSearchScreen`이 만들어진 뒤 `flutter run`으로 전체 흐름을 수동 검증한다 — Task 17 Step 마지막에서 처리)

- [ ] **Step 4: Commit**

```bash
cd mobile
git add lib/features/unit_selection lib/core/unit_type.dart
git commit -m "feat(mobile): add business-unit-type selection screen"
cd ..
```

---

### Task 15: 일자리 검색 & 선택 화면

**Files:**
- Create: `mobile/lib/features/job_search/job_repository.dart`
- Create: `mobile/lib/features/job_search/job_search_screen.dart`
- Test: `mobile/test/features/job_search/job_repository_test.dart`

**Interfaces:**
- Consumes: `ApiClient`/`Dio` (Task 12), `UnitType` (Task 14)
- Produces: `JobRepository.list(UnitType): Future<List<PlaceSummary>>`, `JobRepository.search(UnitType, String q): Future<List<PlaceSummary>>`, `JobRepository.searchFallback(UnitType, String q): Future<List<PlaceSummary>>`, `JobRepository.assignPlace(int placeId): Future<void>`

- [ ] **Step 1: Write the failing test**

```dart
// mobile/test/features/job_search/job_repository_test.dart
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/core/unit_type.dart';
import 'package:senior_job_attendance/features/job_search/job_repository.dart';

void main() {
  test('search parses PlaceSummary list from response body', () async {
    final dio = Dio();
    dio.httpClientAdapter = _FakeAdapter();
    final repository = JobRepository(dio: dio);

    final result = await repository.search(UnitType.publicInterest, '청소');

    expect(result.length, 1);
    expect(result.first.name, '공원안전지킴이');
  });
}

class _FakeAdapter implements HttpClientAdapter {
  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
      RequestOptions options, Stream<List<int>>? requestStream, Future<void>? cancelFuture) async {
    return ResponseBody.fromString(
      '[{"id":1,"name":"공원안전지킴이","address":"주소","unitType":"PUBLIC_INTEREST","description":null,"latitude":35.3,"longitude":129.0}]',
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd mobile && flutter test test/features/job_search/job_repository_test.dart`
Expected: FAIL — `JobRepository`, `PlaceSummary` 없음

- [ ] **Step 3: Implement JobRepository**

```dart
// mobile/lib/features/job_search/job_repository.dart
import 'package:dio/dio.dart';
import '../../core/unit_type.dart';

class PlaceSummary {
  final int id;
  final String name;
  final String address;
  final String? description;

  PlaceSummary({required this.id, required this.name, required this.address, this.description});

  factory PlaceSummary.fromJson(Map<String, dynamic> json) {
    return PlaceSummary(
      id: json['id'] as int,
      name: json['name'] as String,
      address: json['address'] as String,
      description: json['description'] as String?,
    );
  }
}

class JobRepository {
  final Dio dio;

  JobRepository({required this.dio});

  Future<List<PlaceSummary>> list(UnitType unitType) async {
    final response = await dio.get('/api/v1/places', queryParameters: {'unitType': unitType.apiValue});
    return _parseList(response.data);
  }

  Future<List<PlaceSummary>> search(UnitType unitType, String q) async {
    final response = await dio.get('/api/v1/places', queryParameters: {
      'unitType': unitType.apiValue,
      'q': q,
    });
    return _parseList(response.data);
  }

  Future<List<PlaceSummary>> searchFallback(UnitType unitType, String q) async {
    final response = await dio.post('/api/v1/places/search/fallback', data: {
      'unitType': unitType.apiValue,
      'q': q,
    });
    return _parseList(response.data);
  }

  Future<void> assignPlace(int placeId) async {
    await dio.post('/api/v1/members/me/assign-place', data: {'placeId': placeId});
  }

  List<PlaceSummary> _parseList(dynamic data) {
    return (data as List).map((e) => PlaceSummary.fromJson(e as Map<String, dynamic>)).toList();
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd mobile && flutter test test/features/job_search/job_repository_test.dart`
Expected: PASS

- [ ] **Step 5: Build the search screen**

```dart
// mobile/lib/features/job_search/job_search_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/unit_type.dart';
import '../auth/auth_provider.dart';
import '../consent/consent_screen.dart';
import 'job_repository.dart';

class JobSearchScreen extends ConsumerStatefulWidget {
  final UnitType unitType;

  const JobSearchScreen({super.key, required this.unitType});

  @override
  ConsumerState<JobSearchScreen> createState() => _JobSearchScreenState();
}

class _JobSearchScreenState extends ConsumerState<JobSearchScreen> {
  final _queryController = TextEditingController();
  List<PlaceSummary> _results = [];
  bool _searchedOnce = false;

  Future<void> _loadAll() async {
    final repo = JobRepository(dio: ref.read(apiClientProvider).dio);
    final results = await repo.list(widget.unitType);
    setState(() => _results = results);
  }

  Future<void> _search() async {
    final repo = JobRepository(dio: ref.read(apiClientProvider).dio);
    final results = await repo.search(widget.unitType, _queryController.text);
    setState(() {
      _results = results;
      _searchedOnce = true;
    });
  }

  Future<void> _searchWithAi() async {
    final repo = JobRepository(dio: ref.read(apiClientProvider).dio);
    final results = await repo.searchFallback(widget.unitType, _queryController.text);
    setState(() => _results = results);
  }

  Future<void> _select(PlaceSummary place) async {
    final repo = JobRepository(dio: ref.read(apiClientProvider).dio);
    await repo.assignPlace(place.id);
    if (!mounted) return;
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => const ConsentScreen()));
  }

  @override
  void initState() {
    super.initState();
    _loadAll();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('${widget.unitType.label} 일자리 찾기')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _queryController,
                    decoration: const InputDecoration(labelText: '청소, 화단, 쓰레기 줍기 등'),
                  ),
                ),
                IconButton(icon: const Icon(Icons.search), onPressed: _search),
              ],
            ),
          ),
          if (_searchedOnce && _results.isEmpty)
            Padding(
              padding: const EdgeInsets.all(16),
              child: ElevatedButton(
                onPressed: _searchWithAi,
                child: const Text('AI로 더 찾아보기'),
              ),
            ),
          Expanded(
            child: ListView.builder(
              itemCount: _results.length,
              itemBuilder: (context, index) {
                final place = _results[index];
                return ListTile(
                  title: Text(place.name),
                  subtitle: Text(place.address),
                  onTap: () => _select(place),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
```

- [ ] **Step 6: Commit**

```bash
cd mobile
git add lib/features/job_search test/features/job_search
git commit -m "feat(mobile): add job search/selection screen with synonym+LLM fallback"
cd ..
```

---

### Task 16: 위치정보 동의 화면

**Files:**
- Create: `mobile/lib/features/consent/consent_repository.dart`
- Create: `mobile/lib/features/consent/consent_screen.dart`
- Test: `mobile/test/features/consent/consent_repository_test.dart`

**Interfaces:**
- Consumes: `ApiClient`/`Dio` (Task 12)
- Produces: `ConsentRepository.agree(): Future<void>` (`POST /api/v1/members/me/consent` 호출)

- [ ] **Step 1: Write the failing test**

```dart
// mobile/test/features/consent/consent_repository_test.dart
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/features/consent/consent_repository.dart';

void main() {
  test('agree posts to the consent endpoint', () async {
    final dio = Dio();
    late String calledPath;
    dio.httpClientAdapter = _RecordingAdapter(onFetch: (path) => calledPath = path);
    final repository = ConsentRepository(dio: dio);

    await repository.agree();

    expect(calledPath, '/api/v1/members/me/consent');
  });
}

class _RecordingAdapter implements HttpClientAdapter {
  final void Function(String path) onFetch;
  _RecordingAdapter({required this.onFetch});

  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
      RequestOptions options, Stream<List<int>>? requestStream, Future<void>? cancelFuture) async {
    onFetch(options.path);
    return ResponseBody.fromString('{"message":"ok"}', 200, headers: {
      Headers.contentTypeHeader: [Headers.jsonContentType],
    });
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd mobile && flutter test test/features/consent/consent_repository_test.dart`
Expected: FAIL — `ConsentRepository` 없음

- [ ] **Step 3: Implement ConsentRepository**

```dart
// mobile/lib/features/consent/consent_repository.dart
import 'package:dio/dio.dart';

class ConsentRepository {
  final Dio dio;

  ConsentRepository({required this.dio});

  Future<void> agree() async {
    await dio.post('/api/v1/members/me/consent');
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd mobile && flutter test test/features/consent/consent_repository_test.dart`
Expected: PASS

- [ ] **Step 5: Build the consent screen**

```dart
// mobile/lib/features/consent/consent_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../auth/auth_provider.dart';
import '../checkin/checkin_screen.dart';
import 'consent_repository.dart';

class ConsentScreen extends ConsumerStatefulWidget {
  const ConsentScreen({super.key});

  @override
  ConsumerState<ConsentScreen> createState() => _ConsentScreenState();
}

class _ConsentScreenState extends ConsumerState<ConsentScreen> {
  bool _agreed = false;

  Future<void> _continue() async {
    final repo = ConsentRepository(dio: ref.read(apiClientProvider).dio);
    await repo.agree();
    if (!mounted) return;
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => const CheckinScreen()));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('위치정보 수집 동의')),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              '출석 체크를 위해 체크인 시점의 위치정보(GPS 좌표)를 수집합니다. '
              '수집된 위치정보는 출석 확인 목적으로만 사용되며, 최초 1회 동의로 이후 출석 체크에 계속 적용됩니다.',
            ),
            const SizedBox(height: 16),
            CheckboxListTile(
              value: _agreed,
              onChanged: (value) => setState(() => _agreed = value ?? false),
              title: const Text('위치정보 수집 및 이용에 동의합니다.'),
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _agreed ? _continue : null,
              child: const Text('동의하고 계속하기'),
            ),
          ],
        ),
      ),
    );
  }
}
```

- [ ] **Step 6: Commit**

```bash
cd mobile
git add lib/features/consent test/features/consent
git commit -m "feat(mobile): add one-time location consent screen gating check-in"
cd ..
```

---

### Task 17: 위치기반 출석 체크인 화면

**Files:**
- Create: `mobile/lib/features/checkin/checkin_repository.dart`
- Create: `mobile/lib/features/checkin/checkin_screen.dart`
- Test: `mobile/test/features/checkin/checkin_repository_test.dart`

**Interfaces:**
- Consumes: `ApiClient`/`Dio` (Task 12), `geolocator` 패키지
- Produces: `CheckinRepository.checkIn({required int scheduleId, required double latitude, required double longitude}): Future<CheckinResult>`

- [ ] **Step 1: Write the failing test**

```dart
// mobile/test/features/checkin/checkin_repository_test.dart
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/features/checkin/checkin_repository.dart';

void main() {
  test('checkIn parses success result from response body', () async {
    final dio = Dio();
    dio.httpClientAdapter = _FakeAdapter(
      '{"success":true,"message":"출석 처리되었습니다.","distance":42.5}',
    );
    final repository = CheckinRepository(dio: dio);

    final result = await repository.checkIn(scheduleId: 1, latitude: 35.3, longitude: 129.0);

    expect(result.success, isTrue);
    expect(result.message, '출석 처리되었습니다.');
  });

  test('checkIn surfaces the server error message on 409', () async {
    final dio = Dio();
    dio.httpClientAdapter = _FakeAdapter(
      '{"message":"출석 가능한 위치가 아닙니다. (거리: 800.0m, 허용 반경: 500m)"}',
      statusCode: 409,
    );
    final repository = CheckinRepository(dio: dio);

    final result = await repository.checkIn(scheduleId: 1, latitude: 0, longitude: 0);

    expect(result.success, isFalse);
    expect(result.message, contains('허용 반경'));
  });
}

class _FakeAdapter implements HttpClientAdapter {
  final String body;
  final int statusCode;
  _FakeAdapter(this.body, {this.statusCode = 200});

  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
      RequestOptions options, Stream<List<int>>? requestStream, Future<void>? cancelFuture) async {
    return ResponseBody.fromString(body, statusCode, headers: {
      Headers.contentTypeHeader: [Headers.jsonContentType],
    });
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd mobile && flutter test test/features/checkin/checkin_repository_test.dart`
Expected: FAIL — `CheckinRepository`, `CheckinResult` 없음

- [ ] **Step 3: Implement CheckinRepository**

```dart
// mobile/lib/features/checkin/checkin_repository.dart
import 'package:dio/dio.dart';

class CheckinResult {
  final bool success;
  final String message;

  CheckinResult({required this.success, required this.message});
}

class CheckinRepository {
  final Dio dio;

  CheckinRepository({required this.dio});

  Future<CheckinResult> checkIn({
    required int scheduleId,
    required double latitude,
    required double longitude,
  }) async {
    try {
      final response = await dio.post('/api/v1/attend/check-in', data: {
        'scheduleId': scheduleId,
        'latitude': latitude,
        'longitude': longitude,
      });
      return CheckinResult(
        success: response.data['success'] as bool? ?? true,
        message: response.data['message'] as String? ?? '출석 처리되었습니다.',
      );
    } on DioException catch (e) {
      final message = e.response?.data is Map
          ? (e.response?.data['message'] as String? ?? '출석 처리에 실패했습니다.')
          : '출석 처리에 실패했습니다.';
      return CheckinResult(success: false, message: message);
    }
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd mobile && flutter test test/features/checkin/checkin_repository_test.dart`
Expected: PASS

- [ ] **Step 5: Build the check-in screen**

```dart
// mobile/lib/features/checkin/checkin_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import '../auth/auth_provider.dart';
import 'checkin_repository.dart';

class CheckinScreen extends ConsumerStatefulWidget {
  const CheckinScreen({super.key});

  @override
  ConsumerState<CheckinScreen> createState() => _CheckinScreenState();
}

class _CheckinScreenState extends ConsumerState<CheckinScreen> {
  String? _resultMessage;
  bool _loading = false;

  Future<void> _checkIn(int scheduleId) async {
    setState(() => _loading = true);
    try {
      final permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) {
        setState(() => _resultMessage = '위치 권한이 필요합니다. 설정에서 위치 권한을 허용해주세요.');
        return;
      }

      final position = await Geolocator.getCurrentPosition();
      final repo = CheckinRepository(dio: ref.read(apiClientProvider).dio);
      final result = await repo.checkIn(
        scheduleId: scheduleId,
        latitude: position.latitude,
        longitude: position.longitude,
      );
      setState(() => _resultMessage = result.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('출석 체크')),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            if (_resultMessage != null)
              Padding(
                padding: const EdgeInsets.all(16),
                child: Text(_resultMessage!, textAlign: TextAlign.center),
              ),
            ElevatedButton(
              onPressed: _loading ? null : () => _checkIn(1),
              child: Text(_loading ? '확인 중...' : '출석 체크'),
            ),
          ],
        ),
      ),
    );
  }
}
```

(체크인 대상 `scheduleId`를 오늘 일정 목록에서 고르는 화면은 이 플랜 범위 밖이다 — 현재는 참여자당 하나의 활성 일정만 있다고 가정하고 고정값을 쓴다. 여러 일정 지원은 후속 개선 사항.)

- [ ] **Step 6: Manual verification — run the full app end to end**

```bash
cd mobile
flutter run
```

로그인(휴대폰번호+OTP, 백엔드에서 발급된 코드는 로컬 로그 또는 CoolSMS 콘솔에서 확인) → 사업단 유형 선택 → 검색/선택 → 동의 → 체크인까지 수동으로 눌러보고, 각 단계에서 에러 없이 다음 화면으로 넘어가는지 확인한다. 500m 밖에서 체크인 시도 시 반경 초과 메시지가 뜨는지도 확인한다.

- [ ] **Step 7: Commit**

```bash
cd mobile
git add lib/features/checkin test/features/checkin
git commit -m "feat(mobile): add geofenced check-in screen"
cd ..
```

---

## Self-Review Notes

- **스펙 커버리지**: 배경/범위의 "공통 코어" 항목(사업단 선택→검색→동의→체크인) 전부 Task 1~17에서 구현됨. "스키마 보강"(Place.unitType) → Task 1. "일자리 검색 2단계"(동의어+LLM) → Task 2·3·9. "Attend 레코드 생성 시점" → Task 11. "보안 보강 memberId 위조 방지" → Task 8·10. "Flutter 앱 아키텍처"(Riverpod) → Task 12~17. "attendance.location.radius 500" → Task 10. 공익형/시장형/관리자확장/개발자대시보드는 이 플랜 범위 밖(스펙에서 별도 플랜으로 분리하기로 합의됨).
- **플레이스홀더 스캔**: "TODO"/"TBD" 없음. Task 17의 고정 `scheduleId=1`은 플레이스홀더가 아니라 명시적으로 범위를 좁힌 알려진 제약으로 문서화함 (다중 일정 선택은 후속 플랜).
- **타입 일관성 확인**: `PlaceSearchService` 생성자가 Task 3(2-arg)에서 Task 9(3-arg, `Optional<LlmJobSearchClient>`)로 확장되며 기존 2-arg를 오버로드로 유지해 Task 3 테스트가 깨지지 않도록 함. `AttendCheckInApiRequest`(클라이언트용, memberId 없음)와 기존 `AttendCheckInRequest`(서비스 내부용, memberId 있음)를 분리해 명명 충돌 없음. Dart `UnitType.apiValue`가 Java `UnitType` enum 상수명과 정확히 일치(`PUBLIC_INTEREST`/`MARKET`/`SOCIAL_SERVICE`).

---

Plan complete and saved to `docs/superpowers/plans/2026-07-10-senior-job-common-core.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
