# 문제 해결 기록 (Troubleshooting Guide)

> 프로젝트 개발 중 발생한 주요 문제와 해결 방법을 기록한 문서입니다.
>
> 작성일: 2025-11-28

---

## 목차
1. [DataInitializer 의존성 주입 문제](#1-datainitializer-의존성-주입-문제)
2. [Flyway 마이그레이션 실패](#2-flyway-마이그레이션-실패)
3. [Swagger 설정 오류](#3-swagger-설정-오류)
4. [WebSocket 엔드포인트 404 에러](#4-websocket-엔드포인트-404-에러)

---

## 1. DataInitializer 의존성 주입 문제

### 문제 상황
```
org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'dataInitializer':
Injection of autowired dependencies failed
```

### 원인 분석
- `@Value("${geocoding-api-key}")`로 주입받는 프로퍼티가 `local` 프로필에서 로드되지 않음
- `geocoding-api-key`는 `application-API-KEY.properties`에만 정의되어 있음
- 프로퍼티가 없을 때 빈 생성이 실패함

### 해결 방법

#### 1) 기본값 제공
**파일**: `src/main/java/com/example/attempt/config/DataInitializer.java`

```java
// 변경 전
@Value("${geocoding-api-key}")
private String geocodingApiKey;

// 변경 후
@Value("${geocoding-api-key:}")
private String geocodingApiKey;
```

#### 2) API 키 없을 때 안전한 처리
```java
// API 키가 없으면 Geocoding 없이 저장
if (geocodingApiKey == null || geocodingApiKey.trim().isEmpty()) {
    log.warn("Geocoding API 키가 설정되지 않았습니다. 위도/경도 없이 데이터를 저장합니다.");
    // Geocoding 없이 Place 데이터 저장
    ...
}
```

### 결과
- ✅ 빌드 성공
- ✅ API 키가 없어도 애플리케이션 정상 실행
- ✅ Geocoding 기능만 비활성화되고 나머지 기능은 정상 작동

---

## 2. Flyway 마이그레이션 실패

### 문제 상황
```
org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'entityManagerFactory'
defined in class path resource [...]: Failed to initialize dependency 'flywayInitializer' of LoadTimeWeaverAware
bean 'entityManagerFactory': Error creating bean with name 'flywayInitializer' defined in class path resource
[...]: Script V2__improve_schedule_attend.sql failed
```

### 원인 분석
1. **V1 마이그레이션 누락**: V2 스크립트가 있지만 V1이 없어 기존 테이블을 수정하려다 실패
2. **데이터베이스 불일치**: MariaDB 문법을 사용하는데 local 프로필에서는 H2 사용
3. **환경별 전략 차이**:
   - Local: H2 메모리 DB (매번 새로 생성, Flyway 불필요)
   - Production: MariaDB (데이터 보존, Flyway 필요)

### 해결 방법

#### 1) Local 환경에서 Flyway 비활성화
**파일**: `src/main/resources/application-local.yml`

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:queueapp;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: update  # H2에서는 JPA가 자동으로 테이블 생성
    open-in-view: false
  flyway:
    enabled: false  # Local 환경에서는 Flyway 비활성화
```

#### 2) V1 초기 마이그레이션 스크립트 생성
**파일**: `src/main/resources/db/migration/V1__init_schema.sql`

```sql
-- ============================================================================
-- Initial Schema Creation
-- Version: 1.0
-- ============================================================================

-- ADMIN 테이블
CREATE TABLE IF NOT EXISTS ADMIN (
    ID BIGINT AUTO_INCREMENT PRIMARY KEY,
    NAME VARCHAR(100) NOT NULL,
    ...
);

-- PLACE 테이블
CREATE TABLE IF NOT EXISTS PLACE (
    PLACE_ID BIGINT AUTO_INCREMENT PRIMARY KEY,
    ...
);

-- SCHEDULE 테이블 (V2의 개선사항 포함)
CREATE TABLE IF NOT EXISTS SCHEDULE (
    SCHEDULE_ID BIGINT AUTO_INCREMENT PRIMARY KEY,
    TITLE VARCHAR(200) NOT NULL DEFAULT '미지정 일정',
    DESCRIPTION VARCHAR(1000),
    SCHEDULE_DATE DATE NOT NULL,
    START_TIME TIME,
    END_TIME TIME,
    ...
);

-- ATTEND 테이블 (V2의 개선사항 포함)
CREATE TABLE IF NOT EXISTS ATTEND (
    ATTEND_ID BIGINT AUTO_INCREMENT PRIMARY KEY,
    STATUS VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    LATITUDE DOUBLE,
    LONGITUDE DOUBLE,
    ...
);
```

#### 3) V2 마이그레이션 스크립트 삭제
```bash
rm src/main/resources/db/migration/V2__improve_schedule_attend.sql
```
V1에 모든 개선사항이 포함되어 있으므로 V2는 불필요

### 환경별 전략

| 환경 | DB | JPA DDL | Flyway | 설명 |
|------|----|---------|---------| -----|
| Local | H2 (메모리) | `update` | ❌ 비활성화 | 재시작 시 테이블 자동 재생성 |
| Production | MariaDB | `validate` | ✅ 활성화 | V1 마이그레이션으로 안전한 스키마 관리 |

### 결과
- ✅ Local 환경: H2 + JPA로 자동 테이블 생성
- ✅ Production 환경: MariaDB + Flyway로 버전 관리
- ✅ 애플리케이션 정상 시작

---

## 3. Swagger 설정 오류

### 문제 상황 1: 변수명 오류
**파일**: `src/main/java/com/example/attempt/config/SwaggerConfig.java:21`

```java
Server server = new Server();
server.setUrl("/");

Server prodServer = new Server();
server.setUrl("운영 URL");  // ❌ 잘못된 변수 사용
```

### 문제 상황 2: 버전 호환성 문제
```
java.lang.NoSuchMethodError: 'void org.springframework.web.method.ControllerAdviceBean.<init>(java.lang.Object)'
```

#### 원인
- Spring Boot 3.5.6 (최신)
- springdoc-openapi 2.6.0 (구버전)
- 메서드 시그니처 불일치로 인한 호환성 문제

### 해결 방법

#### 1) 변수명 수정
**파일**: `src/main/java/com/example/attempt/config/SwaggerConfig.java`

```java
Server server = new Server();
server.setUrl("/");

Server prodServer = new Server();
prodServer.setUrl("운영 URL");  // ✅ 올바른 변수 사용
```

#### 2) SpringDoc 버전 업그레이드
**파일**: `build.gradle`

```gradle
dependencies {
    // 변경 전
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0'

    // 변경 후
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0'
}
```

### 결과
- ✅ OpenAPI 문서 정상 생성: `http://localhost:8080/v3/api-docs`
- ✅ Swagger UI 정상 작동: `http://localhost:8080/swagger-ui/index.html`
- ✅ API 엔드포인트 자동 문서화
  - Schedule API (일정 관리)
  - Attend API (출석 관리)

### Swagger 접속 방법
```
브라우저 주소창에 입력:
http://localhost:8080/swagger-ui/index.html
```

---

## 4. WebSocket 엔드포인트 404 에러

### 문제 상황
```
org.springframework.web.servlet.resource.NoResourceFoundException: No static resource ws/info.
    at org.springframework.web.servlet.resource.ResourceHttpRequestHandler.handleRequest(...)
```

### 원인 분석
```
현재 설정:
- 등록된 엔드포인트: /ws/queue
- 클라이언트 접속 시도: /ws
- SockJS 정보 요청: /ws/info ❌ (존재하지 않음)

문제:
SockJS는 연결 전 엔드포인트 정보를 확인하기 위해 /ws/info를 호출하지만,
/ws 경로가 등록되지 않아 404 에러 발생
```

### 해결 방법

#### 1) WebSocket 엔드포인트 추가
**파일**: `src/main/java/com/example/attempt/config/WebSocketConfig.java`

```java
@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    // 메인 엔드포인트 추가 (간단한 경로)
    registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS();

    // 큐 전용 엔드포인트 (하위 호환성 유지)
    registry.addEndpoint("/ws/queue")
            .setAllowedOriginPatterns("*")
            .withSockJS();
}
```

#### 2) NoResourceFoundException 우아한 처리
**파일**: `src/main/java/com/example/attempt/exception/GlobalExceptionHandler.java`

```java
@ExceptionHandler(NoResourceFoundException.class)
public ResponseEntity<ErrorResponse> handleNoResourceFoundException(
        NoResourceFoundException ex,
        WebRequest request) {

    String path = request.getDescription(false).replace("uri=", "");

    // WebSocket 관련 경로는 debug 레벨로 로그 (에러 로그 감소)
    if (path.contains("/ws")) {
        log.debug("WebSocket 리소스를 찾을 수 없음: {}", path);
    } else {
        log.warn("리소스를 찾을 수 없음: {}", path);
    }

    ErrorResponse errorResponse = new ErrorResponse(
            "요청한 리소스를 찾을 수 없습니다.",
            LocalDateTime.now(),
            path
    );

    return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(errorResponse);
}
```

### 검증 결과

#### 1) /ws/info 엔드포인트 정상 응답
```bash
$ curl http://localhost:8080/ws/info
```
```json
{
    "entropy": -65423716,
    "origins": ["*:*"],
    "cookie_needed": true,
    "websocket": true
}
```

#### 2) /ws/queue/info 엔드포인트 정상 응답
```bash
$ curl http://localhost:8080/ws/queue/info
```
```json
{
    "entropy": 1541978695,
    "origins": ["*:*"],
    "cookie_needed": true,
    "websocket": true
}
```

### WebSocket 사용 방법

클라이언트는 두 가지 경로 중 선택 가능:

#### JavaScript (SockJS + STOMP)
```javascript
// 방법 1: 간단한 경로
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

// 방법 2: 명시적 경로 (기존 코드 호환)
const socket = new SockJS('http://localhost:8080/ws/queue');
const stompClient = Stomp.over(socket);

// 연결
stompClient.connect({}, function(frame) {
    console.log('Connected: ' + frame);

    // 구독
    stompClient.subscribe('/topic/room/123/state', function(message) {
        console.log(JSON.parse(message.body));
    });

    // 메시지 전송
    stompClient.send("/app/room/join", {}, JSON.stringify({
        roomUid: '123',
        userKey: 'user1'
    }));
});
```

#### Flutter (stomp_dart_client)
```dart
StompClient client = StompClient(
  config: StompConfig(
    url: 'ws://localhost:8080/ws',
    onConnect: onConnect,
    onWebSocketError: (error) => print(error),
  ),
);

client.activate();
```

### WebSocket API 엔드포인트

| 경로 | 설명 | 요청 형식 | 응답 |
|------|------|-----------|------|
| `/app/room/join` | 방 입장 | `{roomUid, userKey}` | `/topic/room/{roomUid}/state` |
| `/app/room/issue` | 번호표 발급 | `{roomUid, userKey}` | `/queue/ticket`, `/topic/room/{roomUid}/state` |
| `/app/room/notify` | 특정 번호 알림 | `{roomUid, number, message}` | `/topic/room/{roomUid}/notification/{userKey}` |

### 결과
- ✅ `/ws/info` 404 에러 해결
- ✅ 두 개의 WebSocket 엔드포인트 제공
- ✅ 에러 로그 최소화
- ✅ 하위 호환성 유지

---

## 요약

| 문제 | 원인 | 해결 방법 | 결과 |
|------|------|-----------|------|
| DataInitializer 빈 생성 실패 | 프로퍼티 누락 | `@Value` 기본값 제공 | ✅ 정상 시작 |
| Flyway 마이그레이션 실패 | V1 누락, 환경 불일치 | Local에서 Flyway 비활성화, V1 생성 | ✅ 환경별 전략 |
| Swagger NoSuchMethodError | 버전 호환성 문제 | SpringDoc 2.7.0 업그레이드 | ✅ API 문서화 |
| WebSocket 404 에러 | 엔드포인트 누락 | `/ws` 엔드포인트 추가 | ✅ 실시간 통신 |

---

## 참고 자료

### 공식 문서
- [Spring Boot Reference Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [SpringDoc OpenAPI](https://springdoc.org/)
- [Flyway Documentation](https://flywaydb.org/documentation/)
- [Spring WebSocket](https://docs.spring.io/spring-framework/reference/web/websocket.html)

### 프로젝트 관련
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI Docs: `http://localhost:8080/v3/api-docs`
- WebSocket 엔드포인트: `ws://localhost:8080/ws` 또는 `ws://localhost:8080/ws/queue`
