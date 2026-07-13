# 프로젝트 분할: 시니어 근태관리 / 번호표 큐 — 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 현재 한 저장소에 섞여 있는 두 시스템(작동 중인 시니어 근태관리, 백엔드 컨트롤러가 삭제되어 죽어있는 번호표 큐)을 `senior-attendance-app/`과 `queue-app/` 두 최상위 폴더로 물리적으로 분리한다.

**Architecture:** 별도 저장소로 쪼개지 않고 한 저장소 안에서 폴더 단위로 분리한다 (git 히스토리 유지, 나중에 필요하면 폴더 단위로 새 저장소 추출 가능). 시니어 근태관리 쪽은 정상 작동하는 코드를 그대로 옮기고 빌드/테스트가 여전히 통과하는지 검증한다. 번호표 쪽은 이미 죽어있는 코드(컨트롤러 없음)이므로 참고용으로만 이동하고, 이번 계획에서 실제 프로젝트로 재구축하지 않는다.

**Tech Stack:** Spring Boot 3.5.6 / Java 17 / Gradle (시니어 근태관리, MariaDB, 변경 없음), Flutter (시니어 근태관리 참여자 앱, 변경 없음). 번호표 쪽은 향후 PostgreSQL로 재구축 예정이나 이번 계획 범위 밖.

## Global Constraints

- 시니어 근태관리 백엔드(`senior-attendance-app/backend/`)는 각 이동 작업 후 `./gradlew test`가 항상 통과해야 한다 (이동 전 기준선: 36개 테스트 통과).
- 시니어 근태관리 Flutter 앱(`senior-attendance-app/mobile/`)은 이동 후 `flutter test`(8개 테스트 통과)와 `dart analyze lib/`(무오류)가 그대로 통과해야 한다.
- `queue-app/backend/`는 이번 계획에서 독립적으로 빌드 가능한 Gradle 프로젝트로 만들지 않는다 — 파일을 참고용으로 이동만 한다. 컴파일 검증 대상이 아니다.
- 파일 이동은 `git mv`로 수행해 히스토리를 보존한다. 삭제는 `git rm`으로 명시적으로 수행한다.
- 각 태스크는 독립된 커밋으로 마무리한다.

---

### Task 1: 최상위 폴더 스캐폴드 생성

**Files:**
- Create: `senior-attendance-app/.gitkeep` (임시, Task 2에서 실제 내용 이동 후 삭제)
- Create: `queue-app/README.md`
- Create: `queue-app/backend/README.md`
- Create: `queue-app/admin-mobile/README.md`
- Create: `queue-app/member-mobile/README.md`

**Interfaces:**
- Consumes: 없음
- Produces: 이후 모든 태스크가 옮겨 담을 폴더 구조

- [ ] **Step 1: 폴더 구조 생성**

```bash
mkdir -p senior-attendance-app
mkdir -p queue-app/backend
mkdir -p queue-app/admin-mobile
mkdir -p queue-app/member-mobile
```

- [ ] **Step 2: senior-attendance-app 임시 플레이스홀더 생성**

Task 2에서 실제 `backend/`, `mobile/`, `docs/`를 채워 넣을 때까지 빈 폴더가 git에 잡히도록 임시 파일을 만든다.

```bash
touch senior-attendance-app/.gitkeep
```

- [ ] **Step 3: queue-app 하위 폴더에 안내 README 작성**

`queue-app/README.md`:

```markdown
# 번호표 큐 앱

이 폴더는 번호표 발급/호출 시스템을 위한 자리다. 기존 백엔드 컨트롤러는
2025년 12월 커밋(`9cc1533`)에서 삭제되어, `backend/legacy-reference/`
아래의 코드는 참고용일 뿐 실제로 작동하지 않는다.

- `backend/` — 참고용 레거시 코드 + 신규 스키마 설계 문서. 실제 Gradle
  프로젝트로 재구축하는 작업은 별도 계획에서 진행한다.
- `admin-mobile/` — 아직 없음. 신규 개발 예정.
- `member-mobile/` — 아직 없음. 신규 개발 예정.

설계 배경: `docs/superpowers/specs/2026-07-13-project-split-senior-queue-design.md`
(저장소 루트 `docs/`, 이 문서 자체는 시니어 근태관리 쪽으로 옮기지 않고
루트에 남겨진 원본을 참조한다 — 두 시스템 모두에 관련된 설계이기 때문).
```

`queue-app/backend/README.md`:

```markdown
# 번호표 백엔드 (참고용, 미구현)

`legacy-reference/`에는 기존 Room/TicketIssuance 관련 코드가 그대로
들어있지만, 이를 노출하던 컨트롤러가 전부 삭제되어 있어 현재 빌드
가능한 프로젝트가 아니다. 신규 구축 시 `SCHEMA_DESIGN.md`의 스키마를
기준으로 새로 설계한다 (PostgreSQL 사용 예정).
```

`queue-app/admin-mobile/README.md`:

```markdown
# 번호표 관리자 모바일 앱 (아직 없음)

신규 개발 예정. 방 생성/조회/호출/알림 기능을 모바일 앱으로 제공한다.
```

`queue-app/member-mobile/README.md`:

```markdown
# 번호표 회원(방문객) 모바일 앱 (아직 없음)

신규 개발 예정. 어르신이 일자리를 선택해 번호표를 뽑는 화면을 제공한다.
```

- [ ] **Step 4: Commit**

```bash
git add senior-attendance-app/.gitkeep queue-app/
git commit -m "chore: scaffold senior-attendance-app and queue-app top-level folders"
```

---

### Task 2: 시니어 근태관리 백엔드 전체를 senior-attendance-app/backend/로 이동

이 태스크는 `src/`, 빌드 파일, 리소스를 통째로 옮긴다. 아직 번호표 전용 파일을 골라내지 않은 상태이므로, 이동 후에도 지금과 동일하게 빌드/테스트가 통과해야 한다 (내용은 하나도 안 바뀌고 위치만 바뀌었기 때문).

**Files:**
- Move: `src/` → `senior-attendance-app/backend/src/`
- Move: `build.gradle` → `senior-attendance-app/backend/build.gradle`
- Move: `settings.gradle` → `senior-attendance-app/backend/settings.gradle`
- Move: `gradlew` → `senior-attendance-app/backend/gradlew`
- Move: `gradlew.bat` → `senior-attendance-app/backend/gradlew.bat`
- Move: `gradle/` → `senior-attendance-app/backend/gradle/`
- Delete: `senior-attendance-app/.gitkeep`

**Interfaces:**
- Consumes: Task 1이 만든 `senior-attendance-app/` 폴더
- Produces: `senior-attendance-app/backend/`에서 `./gradlew test`로 실행 가능한 전체 백엔드 (아직 번호표 코드 포함된 상태)

- [ ] **Step 1: 파일 이동**

```bash
git mv src senior-attendance-app/backend/src
git mv build.gradle senior-attendance-app/backend/build.gradle
git mv settings.gradle senior-attendance-app/backend/settings.gradle
git mv gradlew senior-attendance-app/backend/gradlew
git mv gradlew.bat senior-attendance-app/backend/gradlew.bat
git mv gradle senior-attendance-app/backend/gradle
git rm senior-attendance-app/.gitkeep
```

- [ ] **Step 2: 새 위치에서 빌드 및 테스트 검증**

```bash
cd senior-attendance-app/backend
./gradlew test
```

Expected: `BUILD SUCCESSFUL`, 36개 테스트 통과 (이동 전과 동일한 테스트 스위트, 파일 내용은 변경되지 않았으므로 결과도 동일해야 한다).

- [ ] **Step 3: Commit**

```bash
cd /path/to/repo/root
git add -A
git commit -m "refactor: relocate backend into senior-attendance-app/backend (queue code still mixed in)"
```

---

### Task 3: 번호표 전용 백엔드 파일을 queue-app/backend/legacy-reference/로 분리

Task 2에서 시니어 근태관리 쪽으로 통째로 옮긴 파일 중, 번호표 도메인에만 속하는 것들을 골라내 `queue-app/backend/legacy-reference/`로 다시 옮긴다. 아래 파일 감사를 통해 확정된 목록이다 — 예를 들어 `DeviceController`는 이름만 보면 회원 기기 등록처럼 보이지만 실제로는 `TicketIssuanceRepository`를 직접 참조하는 번호표 전용 코드이고, `WebSocketConfig`/`WebSocketAuthInterceptor`/`RedisConfig`는 애초에 설계 문서 인벤토리에 빠져있던 것을 이번에 발견해 추가했다.

**Files:**
- Move: `senior-attendance-app/backend/src/main/java/com/example/attempt/domain/Room.java` → `queue-app/backend/legacy-reference/domain/Room.java`
- Move: `senior-attendance-app/backend/src/main/java/com/example/attempt/domain/TicketIssuance.java` → `queue-app/backend/legacy-reference/domain/TicketIssuance.java`
- Move: `senior-attendance-app/backend/src/main/java/com/example/attempt/repository/RoomRepository.java` → `queue-app/backend/legacy-reference/repository/RoomRepository.java`
- Move: `senior-attendance-app/backend/src/main/java/com/example/attempt/repository/TicketIssuanceRepository.java` → `queue-app/backend/legacy-reference/repository/TicketIssuanceRepository.java`
- Move: `senior-attendance-app/backend/src/main/java/com/example/attempt/service/RoomService.java` → `queue-app/backend/legacy-reference/service/RoomService.java`
- Move: `senior-attendance-app/backend/src/main/java/com/example/attempt/service/QueueService.java` → `queue-app/backend/legacy-reference/service/QueueService.java`
- Move: `senior-attendance-app/backend/src/main/java/com/example/attempt/service/TicketService.java` → `queue-app/backend/legacy-reference/service/TicketService.java`
- Move: `senior-attendance-app/backend/src/main/java/com/example/attempt/service/WebSocketService.java` → `queue-app/backend/legacy-reference/service/WebSocketService.java`
- Move: `senior-attendance-app/backend/src/main/java/com/example/attempt/controller/DeviceController.java` → `queue-app/backend/legacy-reference/controller/DeviceController.java`
- Move: `senior-attendance-app/backend/src/main/java/com/example/attempt/config/RedisConfig.java` → `queue-app/backend/legacy-reference/config/RedisConfig.java`
- Move: `senior-attendance-app/backend/src/main/java/com/example/attempt/config/WebSocketConfig.java` → `queue-app/backend/legacy-reference/config/WebSocketConfig.java`
- Move: `senior-attendance-app/backend/src/main/java/com/example/attempt/config/WebSocketAuthInterceptor.java` → `queue-app/backend/legacy-reference/config/WebSocketAuthInterceptor.java`
- Move: `senior-attendance-app/backend/src/main/java/com/example/attempt/dto/QueueMessageDto.java` → `queue-app/backend/legacy-reference/dto/QueueMessageDto.java`
- Move: `senior-attendance-app/backend/src/main/java/com/example/attempt/dto/QueueRoomDto.java` → `queue-app/backend/legacy-reference/dto/QueueRoomDto.java`
- Move: `senior-attendance-app/backend/src/main/java/com/example/attempt/dto/QueueTicketDto.java` → `queue-app/backend/legacy-reference/dto/QueueTicketDto.java`
- Move: `senior-attendance-app/backend/src/main/java/com/example/attempt/dto/RoomCreateRequest.java` → `queue-app/backend/legacy-reference/dto/RoomCreateRequest.java`
- Move: `senior-attendance-app/backend/src/main/java/com/example/attempt/dto/RoomStatusResponse.java` → `queue-app/backend/legacy-reference/dto/RoomStatusResponse.java`
- Move: `senior-attendance-app/backend/src/main/java/com/example/attempt/dto/RoomUpdateRequest.java` → `queue-app/backend/legacy-reference/dto/RoomUpdateRequest.java`
- Move: `senior-attendance-app/backend/src/main/java/com/example/attempt/dto/TicketIssueRequest.java` → `queue-app/backend/legacy-reference/dto/TicketIssueRequest.java`
- Move: `senior-attendance-app/backend/src/main/java/com/example/attempt/dto/TicketIssueResponse.java` → `queue-app/backend/legacy-reference/dto/TicketIssueResponse.java`
- Modify: `senior-attendance-app/backend/src/main/java/com/example/attempt/config/SecurityConfig.java`

**Interfaces:**
- Consumes: Task 2가 만든 `senior-attendance-app/backend/`
- Produces: `queue-app/backend/legacy-reference/`에 번호표 전용 코드 전부, `senior-attendance-app/backend/`는 번호표 코드 없이 컴파일 가능

- [ ] **Step 1: 번호표 전용 파일 이동**

```bash
BACKEND=senior-attendance-app/backend/src/main/java/com/example/attempt
QUEUE=queue-app/backend/legacy-reference

mkdir -p $QUEUE/domain $QUEUE/repository $QUEUE/service $QUEUE/controller $QUEUE/config $QUEUE/dto

git mv $BACKEND/domain/Room.java $QUEUE/domain/Room.java
git mv $BACKEND/domain/TicketIssuance.java $QUEUE/domain/TicketIssuance.java
git mv $BACKEND/repository/RoomRepository.java $QUEUE/repository/RoomRepository.java
git mv $BACKEND/repository/TicketIssuanceRepository.java $QUEUE/repository/TicketIssuanceRepository.java
git mv $BACKEND/service/RoomService.java $QUEUE/service/RoomService.java
git mv $BACKEND/service/QueueService.java $QUEUE/service/QueueService.java
git mv $BACKEND/service/TicketService.java $QUEUE/service/TicketService.java
git mv $BACKEND/service/WebSocketService.java $QUEUE/service/WebSocketService.java
git mv $BACKEND/controller/DeviceController.java $QUEUE/controller/DeviceController.java
git mv $BACKEND/config/RedisConfig.java $QUEUE/config/RedisConfig.java
git mv $BACKEND/config/WebSocketConfig.java $QUEUE/config/WebSocketConfig.java
git mv $BACKEND/config/WebSocketAuthInterceptor.java $QUEUE/config/WebSocketAuthInterceptor.java
git mv $BACKEND/dto/QueueMessageDto.java $QUEUE/dto/QueueMessageDto.java
git mv $BACKEND/dto/QueueRoomDto.java $QUEUE/dto/QueueRoomDto.java
git mv $BACKEND/dto/QueueTicketDto.java $QUEUE/dto/QueueTicketDto.java
git mv $BACKEND/dto/RoomCreateRequest.java $QUEUE/dto/RoomCreateRequest.java
git mv $BACKEND/dto/RoomStatusResponse.java $QUEUE/dto/RoomStatusResponse.java
git mv $BACKEND/dto/RoomUpdateRequest.java $QUEUE/dto/RoomUpdateRequest.java
git mv $BACKEND/dto/TicketIssueRequest.java $QUEUE/dto/TicketIssueRequest.java
git mv $BACKEND/dto/TicketIssueResponse.java $QUEUE/dto/TicketIssueResponse.java
```

- [ ] **Step 2: SecurityConfig에서 번호표 전용 매처 제거**

`senior-attendance-app/backend/src/main/java/com/example/attempt/config/SecurityConfig.java`의 `authorizeHttpRequests` 블록을 아래로 교체한다 (`/api/devices/register`, `/ws/**`, `/api/v1/rooms/**`는 번호표 전용 경로라 제거하고, `/api/place/**`/`/api/v1/member/**`/`/api/v1/admin/**`는 이미 삭제된 구버전 컨트롤러가 쓰던 죽은 매처라 함께 제거한다):

```java
            .authorizeHttpRequests(auth -> auth
                    // 인증 불필요 엔드포인트
                    .requestMatchers("/api/auth/**", "/api/v1/member-auth/**", "/actuator/health").permitAll()
                    // 회원 본인 서비스 및 장소 검색 API는 MEMBER 권한 필요
                    .requestMatchers("/api/v1/members/me/**", "/api/v1/places/**", "/api/v1/attend/**").hasRole("MEMBER")
                    // 그 외 모든 요청은 인증 필요
                    .anyRequest().authenticated()
            )
```

- [ ] **Step 3: 빌드 및 테스트 검증**

```bash
cd senior-attendance-app/backend
./gradlew test
```

Expected: `BUILD SUCCESSFUL`, 36개 테스트 통과. (SecurityConfig에서 지운 매처들은 어차피 대응하는 컨트롤러가 없었으므로 기존 테스트 중 이 매처를 검증하던 테스트는 없다 — 만약 컴파일 에러나 테스트 실패가 나면, 옮긴 파일 중 하나를 여전히 참조하는 코드가 남아있다는 뜻이므로 `grep -rn "RoomService\|QueueService\|TicketService\|WebSocketService\|RoomRepository\|TicketIssuanceRepository\|DeviceController\|RedisConfig\|WebSocketConfig\|WebSocketAuthInterceptor" senior-attendance-app/backend/src`로 잔여 참조를 찾아 제거한다.)

- [ ] **Step 4: Commit**

```bash
cd /path/to/repo/root
git add -A
git commit -m "refactor: extract queue-only backend files into queue-app/backend/legacy-reference"
```

---

### Task 4: 죽은 레거시 서비스와 프론트엔드 삭제

구버전(삭제된) 컨트롤러가 쓰던 서비스 계층(`MemberService`, `PlaceService`, `PlaceCrawlingService`, `ExcelService`)과 그 전용 DTO(`AddressDto`, `PlaceDto`), 이들에만 의존하던 `DataInitializer`(웹 크롤링으로 데모 Place 데이터를 시딩하던 코드 — 유일한 의존 대상인 `PlaceCrawlingService`가 삭제되므로 함께 삭제한다), 그리고 아무 컨트롤러도 렌더링하지 않는 `templates/` 전체, 마지막으로 저장소 루트의 `frontend/`(React, 삭제된 엔드포인트만 호출하는 죽은 코드)를 삭제한다.

**Files:**
- Delete: `senior-attendance-app/backend/src/main/java/com/example/attempt/service/MemberService.java`
- Delete: `senior-attendance-app/backend/src/main/java/com/example/attempt/service/PlaceService.java`
- Delete: `senior-attendance-app/backend/src/main/java/com/example/attempt/service/PlaceCrawlingService.java`
- Delete: `senior-attendance-app/backend/src/main/java/com/example/attempt/service/ExcelService.java`
- Delete: `senior-attendance-app/backend/src/main/java/com/example/attempt/dto/AddressDto.java`
- Delete: `senior-attendance-app/backend/src/main/java/com/example/attempt/dto/PlaceDto.java`
- Delete: `senior-attendance-app/backend/src/main/java/com/example/attempt/config/DataInitializer.java`
- Delete: `senior-attendance-app/backend/src/main/resources/templates/` (전체 디렉토리)
- Delete: `frontend/` (저장소 루트, 전체 디렉토리)
- Modify: `senior-attendance-app/backend/build.gradle`

**Interfaces:**
- Consumes: Task 3이 정리한 `senior-attendance-app/backend/`
- Produces: 죽은 코드 없이 정상 작동하는 코드만 남은 `senior-attendance-app/backend/`

- [ ] **Step 1: 죽은 서비스/DTO/초기화 코드 삭제**

```bash
BACKEND=senior-attendance-app/backend/src/main/java/com/example/attempt

git rm $BACKEND/service/MemberService.java
git rm $BACKEND/service/PlaceService.java
git rm $BACKEND/service/PlaceCrawlingService.java
git rm $BACKEND/service/ExcelService.java
git rm $BACKEND/dto/AddressDto.java
git rm $BACKEND/dto/PlaceDto.java
git rm $BACKEND/config/DataInitializer.java
```

- [ ] **Step 2: 렌더링되지 않는 템플릿 삭제**

```bash
git rm -r senior-attendance-app/backend/src/main/resources/templates
```

- [ ] **Step 3: 루트 frontend/ 삭제**

```bash
git rm -r frontend
```

- [ ] **Step 4: build.gradle에서 미사용 의존성 제거**

`senior-attendance-app/backend/build.gradle`에서 아래 6개 의존성 라인을 제거한다 (각각 templates/, RedisConfig, WebSocketConfig, DataInitializer/PlaceCrawlingService, ExcelService 전용이었고 전부 이번 태스크 또는 Task 3에서 삭제/이동됨):

```
implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
implementation 'org.springframework.boot:spring-boot-starter-websocket'
implementation 'com.google.maps:google-maps-services:2.1.2'
implementation 'org.jsoup:jsoup:1.17.2'
implementation 'org.apache.poi:poi:5.4.0'
implementation 'org.apache.poi:poi-ooxml:5.4.0'
```

수정 후 `dependencies` 블록:

```groovy
dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
	implementation 'org.springframework.boot:spring-boot-starter-validation'
	implementation 'org.springframework.boot:spring-boot-starter-web'

    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0'

    implementation 'org.springframework.boot:spring-boot-starter-security'

    // JWT
    implementation 'io.jsonwebtoken:jjwt-api:0.11.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.11.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.11.5'

    // Flyway Database Migration
    implementation 'org.flywaydb:flyway-core:10.15.0'
    implementation 'org.flywaydb:flyway-mysql:10.15.0'
    compileOnly 'org.projectlombok:lombok:1.18.32'
    annotationProcessor 'org.projectlombok:lombok:1.18.32'

	implementation 'com.github.gavlyukovskiy:p6spy-spring-boot-starter:1.9.0'
    implementation 'org.slf4j:slf4j-simple:1.7.25'

    runtimeOnly 'org.mariadb.jdbc:mariadb-java-client:3.4.1'

    runtimeOnly 'com.h2database:h2'
    compileOnly 'org.projectlombok:lombok:1.18.32'
    annotationProcessor 'org.projectlombok:lombok:1.18.32'
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'

	implementation 'net.nurigo:sdk:4.3.0'
}
```

- [ ] **Step 5: 빌드 및 테스트 검증**

```bash
cd senior-attendance-app/backend
./gradlew clean test
```

Expected: `BUILD SUCCESSFUL`, 36개 테스트 통과. (`clean`을 붙이는 이유: 제거한 의존성의 클래스가 캐시된 빌드 산출물에 남아 컴파일 에러를 가리는 것을 방지하기 위해서다.)

- [ ] **Step 6: Commit**

```bash
cd /path/to/repo/root
git add -A
git commit -m "chore: remove dead legacy services, unused templates, and broken frontend/"
```

---

### Task 5: application.yml의 번호표 전용 설정 정리

`application.yml`의 데이터소스가 여전히 `queueapp`/`queue`/`queuepw`라는 번호표 시절 이름을 쓰고 있다. 시니어 근태관리 전용 프로젝트가 됐으니 이름을 맞게 바꾸고, 번호표 전용이었던 `app.socket.cors-origins` 키를 제거한다.

**Files:**
- Modify: `senior-attendance-app/backend/src/main/resources/application.yml`

**Interfaces:**
- Consumes: Task 4까지 정리된 `senior-attendance-app/backend/`
- Produces: 시니어 근태관리 전용으로 이름이 정리된 datasource 설정

- [ ] **Step 1: datasource 이름 변경**

`senior-attendance-app/backend/src/main/resources/application.yml`에서:

```yaml
    url : jdbc:mariadb://localhost:3306/queueapp?useUnicode=true&characterEncoding=utf8
    username: queue
    password: queuepw
```

를 아래로 교체:

```yaml
    url : jdbc:mariadb://localhost:3306/senior_attendance?useUnicode=true&characterEncoding=utf8
    username: senior
    password: seniorpw
```

- [ ] **Step 2: 번호표 전용 소켓 CORS 키 제거**

`app:` 블록에서 `socket:` 하위 키를 제거한다. 기존:

```yaml
app:
  socket:
    cors-origins: "https://example.com,http://localhost:3000,http://localhost:5173"
  allowed-origins: ${ALLOWED_ORIGINS:http://localhost:5173,http://10.0.2.2:8080,http://192.168.0.100:8080}
```

변경 후:

```yaml
app:
  allowed-origins: ${ALLOWED_ORIGINS:http://localhost:5173,http://10.0.2.2:8080,http://192.168.0.100:8080}
```

- [ ] **Step 3: 로컬 개발용 MariaDB 데이터베이스가 있다면 이름 변경 안내**

이 설정은 로컬 개발자의 MariaDB 인스턴스가 `queueapp`이라는 이름의 DB를 갖고 있다는 가정을 깬다. 로컬에서 이 프로젝트를 실행할 사람은 아래를 수동으로 실행해야 한다:

```sql
CREATE DATABASE senior_attendance CHARACTER SET utf8mb4;
CREATE USER 'senior'@'localhost' IDENTIFIED BY 'seniorpw';
GRANT ALL PRIVILEGES ON senior_attendance.* TO 'senior'@'localhost';
```

(이 단계는 실행 코드가 아니라 안내이므로 커밋 대상 아님 — README나 팀 공지에 남길 내용)

- [ ] **Step 4: 빌드 검증**

```bash
cd senior-attendance-app/backend
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL` (application.yml은 컴파일 타임에 검증되지 않으므로, 테스트는 `application-local.yml`/`application.properties`가 우선 적용되는 `local`/`test` 프로필로 돌아 datasource 설정과 무관하게 계속 통과한다 — 여기서는 `application.yml` 자체가 유효한 YAML인지, 그리고 컴파일이 깨지지 않는지만 확인한다).

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`, 36개 테스트 통과.

- [ ] **Step 5: Commit**

```bash
cd /path/to/repo/root
git add -A
git commit -m "config: rename queue-era datasource to senior_attendance, drop queue-only socket CORS key"
```

---

### Task 6: Flutter 모바일 앱 이동

**Files:**
- Move: `mobile/` → `senior-attendance-app/mobile/`

**Interfaces:**
- Consumes: 없음 (Flutter 앱은 백엔드 이동과 독립적)
- Produces: `senior-attendance-app/mobile/`에서 그대로 빌드/테스트 가능한 Flutter 앱

- [ ] **Step 1: 이동**

```bash
git mv mobile senior-attendance-app/mobile
```

- [ ] **Step 2: 새 위치에서 검증**

```bash
cd senior-attendance-app/mobile
flutter test
dart analyze lib/
```

Expected: `flutter test` → 8개 테스트 통과. `dart analyze lib/` → `No issues found!`. (파일 내용은 변경되지 않았으므로 상대 경로 기반 import는 전부 그대로 유효하다 — Dart의 패키지 import(`package:senior_job_attendance/...`)는 `pubspec.yaml`의 패키지명 기준이라 디렉토리 위치와 무관하다.)

- [ ] **Step 3: Commit**

```bash
cd /path/to/repo/root
git add -A
git commit -m "refactor: relocate Flutter mobile app into senior-attendance-app/mobile"
```

---

### Task 7: 문서 이동

**Files:**
- Move: `docs/superpowers/` → `senior-attendance-app/docs/superpowers/`
- Move: `JWT_AUTH_IMPLEMENTATION.md` → `senior-attendance-app/docs/JWT_AUTH_IMPLEMENTATION.md`
- Move: `senior-attendance-app/backend/src/main/resources/markDown/` → `senior-attendance-app/docs/legacy/`

**Interfaces:**
- Consumes: 없음
- Produces: 시니어 근태관리 관련 문서가 `senior-attendance-app/docs/` 아래로 정리됨

- [ ] **Step 1: 지난 세션 스펙/플랜 문서 이동**

```bash
mkdir -p senior-attendance-app/docs
git mv docs/superpowers senior-attendance-app/docs/superpowers
```

이번에 새로 작성한 이 분할 설계/계획 문서(`2026-07-13-project-split-senior-queue-*.md`)는 두 시스템 모두를 다루므로 저장소 루트 `docs/`에 남겨둔다 (아래 Step 2에서 빈 `docs/` 디렉토리에 이 두 파일만 남는지 확인).

- [ ] **Step 2: 루트 docs/ 디렉토리에 분할 설계 문서만 남았는지 확인**

```bash
ls docs/superpowers/specs/ docs/superpowers/plans/
```

Expected: `specs/`에 `2026-07-13-project-split-senior-queue-design.md`만, `plans/`에 이 파일(`2026-07-13-project-split-senior-queue.md`)만 남아있어야 한다. (Step 1의 `git mv docs/superpowers senior-attendance-app/docs/superpowers`가 폴더 전체를 옮겼으므로, 이 두 파일은 그 안에 같이 옮겨져 있을 것이다 — 두 시스템에 걸친 문서이므로 다시 루트로 꺼내온다.)

```bash
git mv senior-attendance-app/docs/superpowers/specs/2026-07-13-project-split-senior-queue-design.md docs/superpowers/specs/2026-07-13-project-split-senior-queue-design.md
git mv senior-attendance-app/docs/superpowers/plans/2026-07-13-project-split-senior-queue.md docs/superpowers/plans/2026-07-13-project-split-senior-queue.md
```

- [ ] **Step 3: JWT 구현 문서 및 레거시 마크다운 이동**

```bash
git mv JWT_AUTH_IMPLEMENTATION.md senior-attendance-app/docs/JWT_AUTH_IMPLEMENTATION.md
mkdir -p senior-attendance-app/docs/legacy
git mv senior-attendance-app/backend/src/main/resources/markDown senior-attendance-app/docs/legacy
```

- [ ] **Step 4: markDown 참조하던 .gitignore 규칙 확인**

루트 `.gitignore`에 `src/main/resources/markDown/*` 규칙이 있다면(이동 전 경로 기준이므로) 더 이상 유효하지 않다. 확인 후 필요시 제거:

```bash
grep -n "markDown" .gitignore
```

만약 아래와 같은 줄이 있다면 삭제한다 (더 이상 해당 경로에 파일이 없으므로):

```
src/main/resources/markDown/*
!src/main/resources/markDown/FRONTEND_INTEGRATION_GUIDE.md
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "docs: relocate senior-attendance-app documentation, keep split spec/plan at repo root"
```

---

### Task 8: queue-app 신규 스키마 설계 문서 작성

**Files:**
- Create: `queue-app/backend/SCHEMA_DESIGN.md`

**Interfaces:**
- Consumes: `docs/superpowers/specs/2026-07-13-project-split-senior-queue-design.md`의 스키마 설계 내용
- Produces: `queue-app`을 실제로 재구축할 때 참조할 수 있는, 그 폴더 안에 자리한 스키마 문서

- [ ] **Step 1: 스키마 설계 문서 작성**

`queue-app/backend/SCHEMA_DESIGN.md`:

```markdown
# 번호표 큐 — 신규 데이터 모델 설계

`legacy-reference/`의 기존 `Room`/`TicketIssuance`는 "사업단을 고르고
번호표를 뽑는" 단순 대기열 모델이었지만, 실제 업무는 다르다: 3개
사업단에 각각 미배정 일자리가 여러 개 있고, 번호표 방은 그 일자리
하나하나에 대한 접수 창구다. 관리자가 방을 만들면 그 관리자가 방의
소유자가 되고, 어르신이 그 일자리(방)를 선택해 들어가는 순간 방
소유자가 그 어르신의 접수 기록에 그대로 남는다. 방은 일시적이라
언제든 닫히고 삭제될 수 있으므로, 접수 이력은 방을 FK로 참조하지
않고 그 시점의 값을 그대로 복사해서 영구 보존한다.

## 테이블

\`\`\`
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
\`\`\`

`TicketDrawRecord`가 `TicketRoom`을 FK로 참조하지 않는 것이 핵심이다
— 방이 삭제되어도 "누가 언제 몇 번을 받았고 어느 사업단/어느
관리자였는지"는 영구히 조회 가능해야 하기 때문이다.

## DB 엔진: PostgreSQL

시니어 근태관리 쪽은 MariaDB를 유지하지만, 이 프로젝트는 백엔드를
사실상 새로 만드는 것이므로 PostgreSQL을 채택한다. MariaDB는
`lower_case_table_names=0` 환경(리눅스 기본값)에서 테이블명이
대소문자를 구분해 파일시스템 파일명과 매핑되는데, 시니어
근태관리 쪽 개발 중 마이그레이션 파일에 소문자 테이블명을 썼다가
반복적으로 걸린 버그가 정확히 이 문제였다. PostgreSQL은 따옴표
없는 식별자를 전부 소문자로 정규화해 이 문제 자체가 발생하지
않는다.

## 관리자 인증

시니어 근태관리 쪽과 완전히 독립된 Admin/JWT/RefreshToken 체계를
새로 구축한다 (SSO 없음). 근태관리 선생님과 번호표 방을 만드는
관리자는 역할 자체가 다른 사람일 가능성이 높고, 두 시스템을
독립 배포하는 것이 목표이므로 인증 서버를 공유하지 않는다.

## 이번 분할 작업에서 하지 않은 것

- 컨트롤러 실제 구현 (`legacy-reference/controller/DeviceController.java`
  참고 가능하나 새 스키마 기준으로 다시 작성해야 함)
- 신규 Gradle 프로젝트 스캐폴드, PostgreSQL 연결 설정
- 관리자/회원 모바일 앱 (`../admin-mobile/`, `../member-mobile/`)
```

- [ ] **Step 2: Commit**

```bash
git add queue-app/backend/SCHEMA_DESIGN.md
git commit -m "docs: add queue-app schema design doc for future rebuild"
```

---

### Task 9: 최종 전체 검증

**Files:**
- 없음 (검증 전용 태스크)

**Interfaces:**
- Consumes: Task 1-8의 전체 결과물
- Produces: 분할 작업이 완전히 끝났다는 확인

- [ ] **Step 1: 저장소 루트 구조 확인**

```bash
ls /path/to/repo/root
```

Expected: `senior-attendance-app/`, `queue-app/`, `docs/`(분할 설계/계획 문서 2개만), 그리고 손대지 않은 루트 메타 파일들(`README.md`, `PORTFOLIO.md`, `docker-compose.yml`, `Dockerfile`, `.github/` 등)만 보이고, `src/`, `frontend/`, `mobile/`, `build.gradle` 등 옛 루트 경로는 더 이상 존재하지 않아야 한다.

- [ ] **Step 2: senior-attendance-app 전체 재검증**

```bash
cd senior-attendance-app/backend
./gradlew clean test
```

Expected: `BUILD SUCCESSFUL`, 36개 테스트 통과.

```bash
cd ../mobile
flutter test
dart analyze lib/
```

Expected: 8개 테스트 통과, `No issues found!`.

- [ ] **Step 3: queue-app이 의도대로 "미구현 참고 자료"인지 확인**

```bash
find queue-app -name "*.gradle" -o -name "pom.xml"
```

Expected: 아무 결과 없음 (queue-app에 빌드 파일이 없어야 한다 — 의도한 대로 아직 프로젝트가 아니라 참고 자료 상태).

- [ ] **Step 4: git 히스토리에 frontend/의 과거 커밋이 남아있는지 확인 (삭제됐지만 복구 가능해야 함)**

```bash
git log --oneline --all -- "frontend/*" | head -5
```

Expected: 과거 커밋들이 조회됨 (파일은 삭제됐지만 히스토리에는 남아있어 필요시 `git checkout <sha> -- frontend/`로 복구 가능).

이 태스크는 코드 변경이 없으므로 커밋하지 않는다.

---

## Self-Review 결과

- **스펙 커버리지**: 설계 문서의 "저장소 구조"(Task 1-2, 6-7), "이관 인벤토리"(Task 3-4, 감사 과정에서 DeviceController/WebSocketConfig/WebSocketAuthInterceptor/RedisConfig를 정확한 쪽으로 재배치하고 DataInitializer 삭제를 추가로 확정함 — 아래 "설계 문서와의 차이" 참고), "관리자 인증 분리"(Task 3에서 SecurityConfig 정리로 시니어 근태관리 쪽만 우선 반영, queue-app 쪽 독립 인증 구축은 신규 프로젝트 스캐폴드와 함께 별도 계획에서), "queue-app 신규 데이터 모델"(Task 8), "DB 엔진"(Task 8 문서에 기록) 전부 태스크로 커버됨.
- **플레이스홀더 스캔**: "TODO"/"TBD"/"나중에 구현" 없음. 모든 step이 실행 가능한 정확한 명령/코드를 담고 있음.
- **타입 일관성**: 파일 경로가 태스크 간에 일관되게 참조됨 (예: Task 2에서 만든 `senior-attendance-app/backend/`가 Task 3-5의 모든 경로의 기준점).

### 설계 문서와의 차이 (승인된 설계 대비 수정된 부분)

계획을 정밀하게 작성하는 과정에서 파일 단위 의존성을 전수조사한 결과, 승인된 설계 문서(`2026-07-13-project-split-senior-queue-design.md`)의 인벤토리 표에 없던 두 가지 발견이 있었다:

1. **`DeviceController`는 시니어 근태관리가 아니라 번호표 전용이다.** 설계 문서에는 시니어 근태관리 쪽으로 분류되어 있었으나, 실제로는 `TicketIssuanceRepository`를 직접 참조하고 `TicketIssuance.userKey` 마이그레이션을 지원하는 번호표 전용 코드였다. Task 3에서 queue-app 쪽으로 재배치했다.
2. **`WebSocketConfig`, `WebSocketAuthInterceptor`, `RedisConfig`는 애초에 설계 문서 인벤토리 표에서 누락되어 있었다.** 셋 다 번호표 실시간 업데이트 전용(RedisConfig는 정의만 있고 실제 사용처는 없는 죽은 코드)이라 Task 3에서 queue-app 쪽으로 이동했다.
3. **`DataInitializer`는 설계 문서에서 논의되지 않았으나, 폐기 대상인 `PlaceCrawlingService`의 유일한 소비자라 함께 폐기했다.** 웹 크롤링으로 관광지 데모 데이터("서울중앙우체국", "부산역" 등)를 시딩하는 코드로, 실제 일자리 데이터와 무관한 레거시 프로토타입 코드였다.

이 세 가지는 설계 문서에서 이미 합의된 원칙("죽은 레거시는 버린다", "번호표 전용은 queue-app으로")을 그대로 적용한 결과이지 새로운 결정이 아니지만, 설계 문서 자체의 인벤토리 표가 부정확했으므로 이 실행 계획을 승인하기 전에 알고 있어야 한다.

---

Plan complete and saved to `docs/superpowers/plans/2026-07-13-project-split-senior-queue.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
