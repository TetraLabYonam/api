# Socket.IO → Spring WebSocket 마이그레이션 완료 보고서

## 📋 마이그레이션 개요

Socket.IO (netty-socketio)에서 Spring WebSocket (STOMP 프로토콜)으로 전환을 완료했습니다.

**마이그레이션 일시:** 2025년
**목적:** 표준 WebSocket 프로토콜 사용으로 인프라 단순화 및 유지보수성 향상

---

## 🔄 변경 사항 요약

### 백엔드 (Spring Boot)

#### 1. 의존성 변경
**이전 (Socket.IO):**
```gradle
implementation 'com.corundumstudio.socketio:netty-socketio:1.7.23'
```

**이후 (Spring WebSocket):**
```gradle
implementation 'org.springframework.boot:spring-boot-starter-websocket'
```

#### 2. 제거된 파일
- `src/main/java/com/example/attempt/config/SocketIoConfig.java` ❌
- `src/main/java/com/example/attempt/controller/socket/SocketHandlers.java` ❌

#### 3. 추가된 파일
- `src/main/java/com/example/attempt/config/WebSocketConfig.java` ✅
- `src/main/java/com/example/attempt/controller/websocket/WebSocketController.java` ✅

#### 4. 설정 변경 (application.yml)
**이전:**
```yaml
app:
  socket:
    host: 0.0.0.0
    port: 9092
    cors-origins: "..."
```

**이후:**
```yaml
app:
  socket:
    cors-origins: "..."
```

**변경 이유:** Spring WebSocket은 기본 HTTP 서버와 같은 포트(8080)를 사용하므로 별도 포트 설정 불필요

---

### 프론트엔드 (React)

#### 1. 패키지 변경
**제거:**
```bash
npm uninstall socket.io-client
```

**추가:**
```bash
npm install @stomp/stompjs sockjs-client
```

#### 2. SocketContext 변경
**이전 (Socket.IO):**
```javascript
import io from 'socket.io-client';

const socket = io(SOCKET_URL, {
  path: '/socket.io',
  transports: ['websocket'],
});
```

**이후 (STOMP):**
```javascript
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const stompClient = new Client({
  webSocketFactory: () => new SockJS(WS_URL),
  // ... STOMP 설정
});
```

#### 3. socketService 변경
**이전 (Socket.IO emit/on):**
```javascript
socket.emit('join', { roomUid, userKey }, (ack) => {
  // ...
});

socket.on('state', (data) => {
  // ...
});
```

**이후 (STOMP publish/subscribe):**
```javascript
client.publish({
  destination: '/app/room/join',
  body: JSON.stringify({ roomUid, userKey }),
});

client.subscribe('/topic/room/${roomUid}/state', (message) => {
  const data = JSON.parse(message.body);
  // ...
});
```

#### 4. 환경 변수 변경
**이전:**
```
VITE_API_URL=http://localhost:8080
VITE_SOCKET_URL=http://localhost:9092
```

**이후:**
```
VITE_API_URL=http://localhost:8080
```

**변경 이유:** WebSocket은 HTTP 서버와 같은 엔드포인트 사용 (`/ws`)

---

## 🔌 WebSocket 엔드포인트

### 연결 엔드포인트
```
ws://localhost:8080/ws
```

SockJS를 통해 fallback 지원 (IE 등 구형 브라우저)

### 메시지 목적지 (Destination)

#### 클라이언트 → 서버
| 목적지 | 설명 |
|--------|------|
| `/app/room/join` | 방 입장 |
| `/app/room/issue` | 번호표 발급/확인 |
| `/app/room/notify` | 알림 전송 (관리자) |

#### 서버 → 클라이언트
| 목적지 | 설명 |
|--------|------|
| `/topic/room/{roomUid}/state` | 방 상태 업데이트 (브로드캐스트) |
| `/topic/room/{roomUid}/notification/{userKey}` | 특정 사용자 알림 |
| `/user/queue/ticket` | 개인 티켓 응답 |
| `/user/queue/errors` | 에러 메시지 |

---

## 📡 통신 프로토콜 비교

### Socket.IO 방식 (이전)
```
클라이언트 → 서버: emit('join', data, callback)
서버 → 클라이언트: callback(ack), emit('state', data)
```

### STOMP 방식 (현재)
```
클라이언트 → 서버: publish('/app/room/join', data)
서버 → 클라이언트: send('/topic/room/{roomUid}/state', data)
```

---

## ✅ 기능 테스트 체크리스트

### 일반 사용자 (RoomPage)
- [x] 방 목록 조회 (REST API)
- [x] 방 선택 및 WebSocket 연결
- [x] 방 입장 메시지 전송
- [x] 방 상태 구독 및 수신
- [x] 번호표 발급/확인
- [x] 개인 티켓 응답 수신
- [x] 알림 구독 및 수신

### 관리자 (AdminPage)
- [x] 방 생성 (REST API)
- [x] 방 초기화 (REST API)
- [x] WebSocket 연결
- [x] 특정 번호에 알림 전송
- [x] 알림 전송 확인

### 백엔드
- [x] WebSocket 핸드셰이크
- [x] STOMP 프레임 처리
- [x] 메시지 라우팅
- [x] 브로드캐스트 메시지
- [x] 개인 메시지 (point-to-point)
- [x] 에러 처리

---

## 🚀 실행 방법

### 1. 백엔드 실행
```bash
./gradlew bootRun
```

**WebSocket 엔드포인트:** `ws://localhost:8080/ws`

### 2. 프론트엔드 실행
```bash
cd frontend
npm run dev
```

**앱 URL:** `http://localhost:5173`

### 3. 연결 확인
브라우저 개발자 도구 콘솔에서 다음 메시지 확인:
```
[WebSocket] Connecting to http://localhost:8080/ws
[WebSocket] Connected: ...
```

---

## 🔍 디버깅

### 백엔드 로그
```java
// WebSocketController에서
log.info("User {} joined room {}", userKey, roomUid);
log.info("Ticket issued for user {} in room {}: {}", userKey, roomUid, number);
```

### 프론트엔드 로그
```javascript
// SocketContext에서
console.log('[WebSocket] Connected:', frame);
console.log('[STOMP Debug]', str); // STOMP 프로토콜 상세 로그
```

---

## 🎯 장점

### 1. 표준 프로토콜
- ✅ Spring 생태계와 자연스러운 통합
- ✅ STOMP는 WebSocket 위에서 동작하는 표준 프로토콜
- ✅ 많은 클라이언트 라이브러리 지원

### 2. 인프라 단순화
- ✅ 별도 포트 불필요 (8080 단일 포트)
- ✅ HTTP와 WebSocket 동일 서버
- ✅ CORS 설정 단순화

### 3. 유지보수성
- ✅ Spring의 @MessageMapping 어노테이션 사용
- ✅ 타입 안전한 메시지 처리 (DTO 사용)
- ✅ Spring의 DI/AOP 활용 가능

### 4. 성능
- ✅ Native WebSocket 사용
- ✅ SockJS fallback 지원
- ✅ 브라우저 네이티브 WebSocket API 활용

---

## ⚠️ 주의사항

### 1. 구독 관리
STOMP에서는 명시적으로 구독을 해제해야 메모리 누수 방지:
```javascript
useEffect(() => {
  const subscription = client.subscribe(...);
  return () => {
    subscription.unsubscribe();
  };
}, []);
```

### 2. 연결 상태 확인
메시지 전송 전 항상 연결 상태 확인:
```javascript
if (!client || !client.connected) {
  throw new Error('Client not connected');
}
```

### 3. JSON 파싱
STOMP 메시지는 항상 문자열이므로 명시적 파싱 필요:
```javascript
const data = JSON.parse(message.body);
```

---

## 🔧 트러블슈팅

### 문제: WebSocket 연결 실패
**증상:** `WebSocket connection to 'ws://localhost:8080/ws' failed`

**해결:**
1. 백엔드가 실행 중인지 확인
2. CORS 설정 확인 (application.yml)
3. 방화벽/프록시 설정 확인

### 문제: 메시지가 수신되지 않음
**증상:** 구독은 되었지만 메시지가 오지 않음

**해결:**
1. 목적지(destination) 경로 확인
2. 브라우저 콘솔에서 STOMP Debug 로그 확인
3. 백엔드 로그 확인

### 문제: 개인 메시지 수신 안 됨
**증상:** `/user/queue/...` 메시지가 오지 않음

**해결:**
1. 세션 ID 확인
2. `SimpMessagingTemplate.convertAndSendToUser()` 첫 번째 인자에 세션 ID 사용 확인

---

## 📚 참고 자료

### Spring WebSocket
- [Spring WebSocket Documentation](https://docs.spring.io/spring-framework/reference/web/websocket.html)
- [Spring STOMP Support](https://docs.spring.io/spring-framework/reference/web/websocket/stomp.html)

### STOMP 프로토콜
- [STOMP Protocol Specification](https://stomp.github.io/)
- [@stomp/stompjs Documentation](https://stomp-js.github.io/guide/stompjs/using-stompjs-v5.html)

### SockJS
- [SockJS Client](https://github.com/sockjs/sockjs-client)
- [Spring SockJS Support](https://docs.spring.io/spring-framework/reference/web/websocket/fallback.html)

---

## ✨ 결론

Socket.IO에서 Spring WebSocket (STOMP)로의 마이그레이션이 성공적으로 완료되었습니다.

**주요 성과:**
- ✅ 표준 프로토콜 사용
- ✅ 인프라 단순화 (단일 포트)
- ✅ Spring 생태계와 자연스러운 통합
- ✅ 모든 기능 정상 동작 확인

**다음 단계:**
- 프로덕션 환경 배포
- 부하 테스트
- 모니터링 설정