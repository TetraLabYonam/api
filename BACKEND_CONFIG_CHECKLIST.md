# 백엔드 서버 설정 체크리스트

본 문서는 번호표 시스템 백엔드 서버의 설정 상태를 확인하는 체크리스트입니다.

## ✅ 설정 완료 항목

### 1. CORS 설정

**파일**: `src/main/java/com/example/attempt/config/CorsConfig.java`

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")  // ✅ 모든 출처 허용
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")  // ✅ 모든 메서드 허용
                .allowedHeaders("*")  // ✅ 모든 헤더 허용
                .allowCredentials(true)  // ✅ 자격 증명 허용
                .maxAge(3600);  // ✅ preflight 캐시 1시간
    }
}
```

**상태**: ✅ **완료**
- 모든 출처 허용 (Flutter, React 모두 지원)
- 모든 HTTP 메서드 허용
- 자격 증명(쿠키) 허용
- preflight 요청 캐싱

---

### 2. WebSocket CORS 설정

**파일**: `src/main/java/com/example/attempt/config/WebSocketConfig.java`

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");  // ✅ 메시지 브로커 설정
        config.setApplicationDestinationPrefixes("/app");  // ✅ 클라이언트 전송 prefix
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 메인 엔드포인트
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // ✅ 모든 출처 허용
                .withSockJS();  // ✅ SockJS fallback

        // 큐 전용 엔드포인트 (하위 호환)
        registry.addEndpoint("/ws/queue")
                .setAllowedOriginPatterns("*")  // ✅ 모든 출처 허용
                .withSockJS();  // ✅ SockJS fallback
    }
}
```

**상태**: ✅ **완료**
- WebSocket 엔드포인트: `/ws`, `/ws/queue`
- 모든 출처 허용
- SockJS fallback 지원 (브라우저 호환성)
- STOMP 프로토콜 사용

---

### 3. 서버 포트 및 데이터베이스 설정

**파일**: `src/main/resources/application.yml`

```yaml
server:
  port: 8080  # ✅ 기본 포트

spring:
  datasource:
    url: jdbc:mariadb://localhost:3306/queueapp  # ✅ MariaDB 설정
    username: queue
    password: queuepw
    driver-class-name: org.mariadb.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: update  # ✅ 자동 테이블 생성
    open-in-view: false

  flyway:
    enabled: false  # ✅ Flyway 비활성화 (필요시 활성화)
```

**상태**: ✅ **완료**
- 포트: 8080
- MariaDB 연결 설정
- JPA 자동 DDL

---

### 4. 로컬 개발 환경 설정

**파일**: `src/main/resources/application-local.yml`

```yaml
server:
  port: 8080

spring:
  # H2 인메모리 데이터베이스 (빠른 시연용)
  datasource:
    url: jdbc:h2:mem:queueapp  # ✅ H2 인메모리
    driver-class-name: org.h2.Driver
    username: sa
    password:

  h2:
    console:
      enabled: true  # ✅ H2 콘솔 활성화
      path: /h2-console

  jpa:
    hibernate:
      ddl-auto: update  # ✅ 자동 테이블 생성
    show-sql: true  # ✅ SQL 로그 출력

  flyway:
    enabled: false  # ✅ Flyway 비활성화

# 로깅 레벨
logging:
  level:
    com.example.attempt: DEBUG  # ✅ 애플리케이션 로그
    org.springframework.web: DEBUG  # ✅ Web 로그
    org.springframework.messaging: DEBUG  # ✅ WebSocket 로그
```

**상태**: ✅ **완료**
- H2 인메모리 DB 설정 (별도 DB 설치 불필요)
- H2 콘솔 활성화: `http://localhost:8080/h2-console`
- 상세한 로깅 설정

---

### 5. API 엔드포인트

#### REST API

| Method | Endpoint | 설명 | 상태 |
|--------|----------|------|------|
| GET | `/api/queue/rooms` | 활성화된 방 목록 | ✅ |
| GET | `/api/queue/room/{roomId}` | 방 정보 조회 | ✅ |
| POST | `/api/queue/room/{roomId}/ticket` | 번호표 발급 | ✅ |
| GET | `/api/queue/room/{roomId}/status` | 방 현황 조회 | ✅ |
| GET | `/api/queue/room/{roomId}/tickets` | 번호표 목록 | ✅ |
| POST | `/api/queue/room/{roomId}/call` | 다음 번호 호출 | ✅ |
| POST | `/api/v1/rooms` | 방 생성 (관리자) | ✅ |
| POST | `/api/v1/rooms/batch` | 방 일괄 생성 | ✅ |
| POST | `/api/v1/rooms/{uid}/reset` | 방 초기화 | ✅ |
| DELETE | `/api/v1/rooms/{id}` | 방 삭제 | ✅ |

#### WebSocket

| Type | Destination | 설명 | 상태 |
|------|-------------|------|------|
| 연결 | `/ws` | WebSocket 엔드포인트 | ✅ |
| 전송 | `/app/room/join` | 방 입장 | ✅ |
| 전송 | `/app/room/issue` | 번호표 발급 | ✅ |
| 전송 | `/app/room/notify` | 알림 전송 (관리자) | ✅ |
| 구독 | `/topic/room/{roomUid}/state` | 방 상태 업데이트 | ✅ |
| 구독 | `/user/queue/ticket` | 개인 티켓 수신 | ✅ |
| 구독 | `/topic/room/{roomUid}/notification/{userKey}` | 알림 수신 | ✅ |

---

## 실행 방법

### 방법 1: H2 인메모리 DB (빠른 시연)

```bash
# 로컬 프로필로 실행
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 방법 2: MariaDB (프로덕션)

```bash
# 기본 프로필로 실행
./gradlew bootRun
```

### 방법 3: IDE (IntelliJ IDEA)

1. `AttemptApplication.java` 우클릭
2. `Modify Run Configuration...`
3. `VM options`에 `-Dspring.profiles.active=local` 추가
4. 실행

---

## 확인 방법

### 1. 서버 실행 확인

```bash
# 서버 상태 확인
curl http://localhost:8080/api/queue/rooms

# 예상 응답: []
```

### 2. Swagger UI 확인

```
http://localhost:8080/swagger-ui/index.html
```

### 3. H2 콘솔 확인 (로컬 프로필 사용 시)

```
URL: http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:queueapp
Username: sa
Password: (비어있음)
```

### 4. WebSocket 연결 확인

브라우저 개발자 도구 > Console:

```javascript
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);
stompClient.connect({}, (frame) => {
  console.log('Connected:', frame);
});
```

---

## 필수 체크 사항

### 시작 전
- [ ] Java 17 이상 설치
- [ ] Gradle 설치 또는 `./gradlew` 사용 가능
- [ ] 8080 포트가 사용 가능한 상태

### MariaDB 사용 시
- [ ] MariaDB 설치 및 실행 중
- [ ] `queueapp` 데이터베이스 생성
- [ ] `queue` 사용자 생성 (비밀번호: `queuepw`)

### H2 사용 시 (로컬)
- [ ] `--spring.profiles.active=local` 옵션으로 실행
- [ ] H2 콘솔 접속 가능 확인

### 실행 후
- [ ] `http://localhost:8080/swagger-ui/index.html` 접속 가능
- [ ] `http://localhost:8080/api/queue/rooms` 응답 확인
- [ ] CORS 에러 없음
- [ ] WebSocket 연결 가능

---

## 트러블슈팅

### 포트 8080이 이미 사용 중

**증상**:
```
Port 8080 was already in use
```

**해결**:
```bash
# 포트 사용 중인 프로세스 확인
lsof -i :8080

# 프로세스 종료
kill -9 <PID>

# 또는 다른 포트 사용
./gradlew bootRun --args='--server.port=8081 --spring.profiles.active=local'
```

### MariaDB 연결 실패

**증상**:
```
Communications link failure
```

**해결**:
1. MariaDB 서비스 실행 확인
   ```bash
   # macOS
   brew services start mariadb

   # Linux
   sudo systemctl start mariadb
   ```

2. 데이터베이스 및 사용자 확인
   ```sql
   SHOW DATABASES;
   SELECT User, Host FROM mysql.user;
   ```

3. 비밀번호 확인 (`application.yml`의 `queuepw`)

### CORS 에러

**증상**:
```
Access to XMLHttpRequest ... has been blocked by CORS policy
```

**확인 사항**:
- ✅ `CorsConfig.java`에 `allowedOriginPatterns("*")` 설정
- ✅ 서버가 실행 중인지 확인
- ✅ 브라우저 캐시 삭제 후 재시도

### WebSocket 연결 실패

**증상**:
```
WebSocket connection failed
```

**확인 사항**:
- ✅ `WebSocketConfig.java`에 `setAllowedOriginPatterns("*")` 설정
- ✅ SockJS 사용 (`withSockJS()`)
- ✅ 엔드포인트: `/ws` 또는 `/ws/queue`
- ✅ 클라이언트에서 SockJS 사용

---

## 요약

### ✅ 완료된 설정

| 항목 | 상태 | 파일/설정 |
|------|------|-----------|
| CORS | ✅ | `CorsConfig.java` |
| WebSocket CORS | ✅ | `WebSocketConfig.java` |
| REST API | ✅ | `/api/queue/*`, `/api/v1/rooms/*` |
| WebSocket | ✅ | `/ws`, `/ws/queue` |
| H2 DB (로컬) | ✅ | `application-local.yml` |
| MariaDB (프로덕션) | ✅ | `application.yml` |
| Swagger UI | ✅ | `http://localhost:8080/swagger-ui/index.html` |

### 🚀 실행 명령어

```bash
# 로컬 환경 (H2)
./gradlew bootRun --args='--spring.profiles.active=local'

# 프로덕션 환경 (MariaDB)
./gradlew bootRun
```

### 📝 확인 URL

```
Swagger UI: http://localhost:8080/swagger-ui/index.html
API Test: http://localhost:8080/api/queue/rooms
H2 Console: http://localhost:8080/h2-console (로컬 프로필만)
```

모든 설정이 완료되었으며, 로컬 환경에서 바로 시연 가능합니다! ✨
