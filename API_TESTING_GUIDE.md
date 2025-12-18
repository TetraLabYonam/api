# API 테스트 가이드 - 번호표 시스템

Flutter 앱 개발 전에 백엔드 API가 정상적으로 작동하는지 테스트하는 방법입니다.

## 테스트 도구

### 1. Swagger UI (추천)
가장 쉬운 방법입니다.

```
http://localhost:8080/swagger-ui/index.html
```

브라우저에서 접속하면 모든 API를 GUI로 테스트할 수 있습니다.

### 2. curl (명령줄)
터미널에서 직접 테스트할 수 있습니다.

### 3. Postman
REST API 클라이언트 도구를 사용할 수 있습니다.

---

## REST API 테스트

### 1. 방 목록 조회

#### Request
```bash
curl -X GET "http://localhost:8080/api/queue/rooms" \
  -H "Content-Type: application/json"
```

#### Response (성공)
```json
[
  {
    "id": 1,
    "roomUid": "ABC123XY",
    "roomName": "물금청소년문화의집",
    "isActive": true,
    "currentNumber": 5,
    "lastIssuedNumber": 12,
    "waitingCount": 7,
    "createdAt": "2025-01-15T10:30:00",
    "updatedAt": "2025-01-15T14:25:00"
  }
]
```

### 2. 특정 방 정보 조회

#### Request
```bash
curl -X GET "http://localhost:8080/api/queue/room/ABC123XY" \
  -H "Content-Type: application/json"
```

#### Response
```json
{
  "id": 1,
  "roomUid": "ABC123XY",
  "roomName": "물금청소년문화의집",
  "isActive": true,
  "currentNumber": 5,
  "lastIssuedNumber": 12,
  "waitingCount": 7
}
```

### 3. 번호표 발급

#### Request
```bash
curl -X POST "http://localhost:8080/api/queue/room/ABC123XY/ticket" \
  -H "Content-Type: application/json" \
  -d '{
    "userDeviceId": "test-device-001"
  }'
```

#### Response (첫 발급)
```json
{
  "number": 13,
  "duplicated": false,
  "lastNumber": 5,
  "count": 8
}
```

#### Response (중복 발급)
```json
{
  "number": 13,
  "duplicated": true,
  "lastNumber": 5,
  "count": 8
}
```

### 4. 방 현황 조회

#### Request
```bash
curl -X GET "http://localhost:8080/api/queue/room/ABC123XY/status" \
  -H "Content-Type: application/json"
```

#### Response
```json
{
  "roomId": "ABC123XY",
  "roomName": "물금청소년문화의집",
  "currentNumber": 5,
  "lastIssuedNumber": 13,
  "waitingCount": 8,
  "isActive": true
}
```

### 5. 방의 모든 번호표 조회

#### Request
```bash
curl -X GET "http://localhost:8080/api/queue/room/ABC123XY/tickets" \
  -H "Content-Type: application/json"
```

#### Response
```json
[
  {
    "id": 1,
    "number": 1,
    "userDeviceId": "device-001",
    "status": "COMPLETED",
    "issuedAt": "2025-01-15T10:00:00",
    "calledAt": "2025-01-15T10:05:00"
  },
  {
    "id": 2,
    "number": 2,
    "userDeviceId": "device-002",
    "status": "WAITING",
    "issuedAt": "2025-01-15T10:10:00",
    "calledAt": null
  }
]
```

### 6. 다음 번호 호출 (관리자용)

#### Request
```bash
curl -X POST "http://localhost:8080/api/queue/room/ABC123XY/call" \
  -H "Content-Type: application/json"
```

#### Response
```json
6
```

### 7. 방 생성 (관리자용)

#### Request
```bash
curl -X POST "http://localhost:8080/api/v1/rooms" \
  -H "Content-Type: application/json" \
  -d '{
    "roomName": "테스트 방",
    "roomUid": ""
  }'
```

#### Response
```json
{
  "id": 2,
  "roomUid": "XYZ789AB",
  "roomName": "테스트 방",
  "isActive": true,
  "currentNumber": 0,
  "lastIssuedNumber": 0,
  "waitingCount": 0,
  "createdAt": "2025-01-15T15:00:00",
  "updatedAt": "2025-01-15T15:00:00"
}
```

### 8. 방 초기화 (관리자용)

#### Request
```bash
curl -X POST "http://localhost:8080/api/v1/rooms/ABC123XY/reset" \
  -H "Content-Type: application/json"
```

#### Response
```json
{
  "success": "true",
  "message": "방이 초기화되었습니다."
}
```

---

## WebSocket 테스트

### 브라우저 콘솔 테스트

브라우저 개발자 도구(F12)에서 다음 코드를 실행하세요.

```javascript
// SockJS 및 STOMP 라이브러리 로드 (CDN)
const script1 = document.createElement('script');
script1.src = 'https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js';
document.head.appendChild(script1);

const script2 = document.createElement('script');
script2.src = 'https://cdn.jsdelivr.net/npm/@stomp/stompjs@7/bundles/stomp.umd.min.js';
document.head.appendChild(script2);

// 라이브러리 로드 후 실행 (3초 대기)
setTimeout(() => {
  // WebSocket 연결
  const socket = new SockJS('http://localhost:8080/ws');
  const stompClient = new StompJs.Client({
    webSocketFactory: () => socket,
    debug: (str) => console.log('[STOMP]', str),
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
  });

  // 연결 성공 시
  stompClient.onConnect = (frame) => {
    console.log('✅ WebSocket Connected!', frame);

    const roomUid = 'ABC123XY'; // 실제 방 UID로 변경
    const userKey = 'test-user-' + Math.random().toString(36).substring(7);

    // 1. 방 상태 구독
    stompClient.subscribe(`/topic/room/${roomUid}/state`, (message) => {
      console.log('📊 Room State Update:', JSON.parse(message.body));
    });

    // 2. 개인 티켓 구독
    stompClient.subscribe('/user/queue/ticket', (message) => {
      console.log('🎫 Ticket Issued:', JSON.parse(message.body));
    });

    // 3. 알림 구독
    stompClient.subscribe(`/topic/room/${roomUid}/notification/${userKey}`, (message) => {
      console.log('🔔 Notification:', JSON.parse(message.body));
    });

    // 4. 방 입장
    stompClient.publish({
      destination: '/app/room/join',
      body: JSON.stringify({ roomUid, userKey }),
    });

    // 5. 번호표 발급 (3초 후)
    setTimeout(() => {
      console.log('🎫 Requesting ticket...');
      stompClient.publish({
        destination: '/app/room/issue',
        body: JSON.stringify({ roomUid, userKey }),
      });
    }, 3000);
  };

  // 연결 에러 시
  stompClient.onStompError = (frame) => {
    console.error('❌ STOMP Error:', frame);
  };

  // 연결 시작
  stompClient.activate();

  // 전역 변수로 저장 (나중에 사용 가능)
  window.stompClient = stompClient;
}, 3000);
```

### 테스트 결과 확인

콘솔에 다음과 같은 메시지가 출력되어야 합니다:

```
✅ WebSocket Connected!
📊 Room State Update: { lastNumber: 5, count: 8 }
🎫 Ticket Issued: { number: 14, duplicated: false, ... }
```

### WebSocket 연결 종료

```javascript
window.stompClient.deactivate();
```

---

## 통합 테스트 시나리오

### 시나리오 1: 번호표 발급 전체 흐름

```bash
# 1. 방 목록 조회
curl -X GET "http://localhost:8080/api/queue/rooms"

# 2. 첫 번째 방의 roomUid를 복사하여 사용
ROOM_UID="ABC123XY"

# 3. 방 상태 확인
curl -X GET "http://localhost:8080/api/queue/room/$ROOM_UID/status"

# 4. 번호표 발급 (디바이스 A)
curl -X POST "http://localhost:8080/api/queue/room/$ROOM_UID/ticket" \
  -H "Content-Type: application/json" \
  -d '{"userDeviceId": "device-A"}'

# 5. 번호표 발급 (디바이스 B)
curl -X POST "http://localhost:8080/api/queue/room/$ROOM_UID/ticket" \
  -H "Content-Type: application/json" \
  -d '{"userDeviceId": "device-B"}'

# 6. 중복 발급 테스트 (디바이스 A - 동일)
curl -X POST "http://localhost:8080/api/queue/room/$ROOM_UID/ticket" \
  -H "Content-Type: application/json" \
  -d '{"userDeviceId": "device-A"}'

# 7. 방 상태 재확인 (대기 인원 증가 확인)
curl -X GET "http://localhost:8080/api/queue/room/$ROOM_UID/status"

# 8. 다음 번호 호출 (관리자)
curl -X POST "http://localhost:8080/api/queue/room/$ROOM_UID/call"

# 9. 방 상태 재확인 (currentNumber 증가 확인)
curl -X GET "http://localhost:8080/api/queue/room/$ROOM_UID/status"
```

### 시나리오 2: 방 관리 (관리자)

```bash
# 1. 새 방 생성
curl -X POST "http://localhost:8080/api/v1/rooms" \
  -H "Content-Type: application/json" \
  -d '{
    "roomName": "신규 테스트 방"
  }'

# 응답에서 roomUid 확인
NEW_ROOM_UID="..."

# 2. 생성한 방 조회
curl -X GET "http://localhost:8080/api/v1/rooms/uid/$NEW_ROOM_UID"

# 3. 방 수정
ROOM_ID=1
curl -X PUT "http://localhost:8080/api/v1/rooms/$ROOM_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "roomName": "수정된 방 이름",
    "currentNumber": 10
  }'

# 4. 방 비활성화
curl -X PUT "http://localhost:8080/api/v1/rooms/$ROOM_ID/deactivate"

# 5. 방 활성화
curl -X PUT "http://localhost:8080/api/v1/rooms/$ROOM_ID/activate"

# 6. 방 초기화
curl -X POST "http://localhost:8080/api/v1/rooms/$NEW_ROOM_UID/reset"

# 7. 방 삭제
curl -X DELETE "http://localhost:8080/api/v1/rooms/$ROOM_ID"
```

---

## 예상 에러 및 해결

### 404 Not Found

```json
{
  "timestamp": "2025-01-15T15:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "방을 찾을 수 없습니다: ABC123XY",
  "path": "/api/queue/room/ABC123XY"
}
```

**원인**: 존재하지 않는 방 UID
**해결**: `GET /api/queue/rooms`로 실제 방 목록 확인

### 400 Bad Request

```json
{
  "timestamp": "2025-01-15T15:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "userDeviceId는 필수입니다."
}
```

**원인**: 필수 파라미터 누락
**해결**: 요청 body에 필수 필드 포함

### 500 Internal Server Error

```json
{
  "timestamp": "2025-01-15T15:30:00",
  "status": 500,
  "error": "Internal Server Error",
  "message": "서버 오류가 발생했습니다."
}
```

**원인**: 서버 내부 오류
**해결**: 서버 로그 확인, 데이터베이스 연결 확인

---

## Postman Collection

Postman을 사용하는 경우, 다음 설정을 Import 하세요:

```json
{
  "info": {
    "name": "Queue System API",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "variable": [
    {
      "key": "base_url",
      "value": "http://localhost:8080",
      "type": "string"
    },
    {
      "key": "room_uid",
      "value": "ABC123XY",
      "type": "string"
    },
    {
      "key": "device_id",
      "value": "test-device-001",
      "type": "string"
    }
  ],
  "item": [
    {
      "name": "Get Rooms",
      "request": {
        "method": "GET",
        "header": [],
        "url": {
          "raw": "{{base_url}}/api/queue/rooms",
          "host": ["{{base_url}}"],
          "path": ["api", "queue", "rooms"]
        }
      }
    },
    {
      "name": "Issue Ticket",
      "request": {
        "method": "POST",
        "header": [
          {
            "key": "Content-Type",
            "value": "application/json"
          }
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"userDeviceId\": \"{{device_id}}\"\n}"
        },
        "url": {
          "raw": "{{base_url}}/api/queue/room/{{room_uid}}/ticket",
          "host": ["{{base_url}}"],
          "path": ["api", "queue", "room", "{{room_uid}}", "ticket"]
        }
      }
    },
    {
      "name": "Get Room Status",
      "request": {
        "method": "GET",
        "header": [],
        "url": {
          "raw": "{{base_url}}/api/queue/room/{{room_uid}}/status",
          "host": ["{{base_url}}"],
          "path": ["api", "queue", "room", "{{room_uid}}", "status"]
        }
      }
    }
  ]
}
```

---

## 자동화 테스트 스크립트

### Bash 스크립트

```bash
#!/bin/bash
# test_queue_api.sh

BASE_URL="http://localhost:8080"
DEVICE_ID="test-device-$(date +%s)"

echo "🧪 Queue System API 테스트 시작"
echo "================================"

# 1. 방 목록 조회
echo -e "\n1️⃣ 방 목록 조회..."
ROOMS=$(curl -s "$BASE_URL/api/queue/rooms")
echo "$ROOMS" | jq .

# 첫 번째 방의 UID 추출
ROOM_UID=$(echo "$ROOMS" | jq -r '.[0].roomUid')
echo "📍 테스트 방: $ROOM_UID"

# 2. 방 상태 조회
echo -e "\n2️⃣ 방 상태 조회..."
curl -s "$BASE_URL/api/queue/room/$ROOM_UID/status" | jq .

# 3. 번호표 발급
echo -e "\n3️⃣ 번호표 발급..."
TICKET=$(curl -s -X POST "$BASE_URL/api/queue/room/$ROOM_UID/ticket" \
  -H "Content-Type: application/json" \
  -d "{\"userDeviceId\": \"$DEVICE_ID\"}")
echo "$TICKET" | jq .

TICKET_NUMBER=$(echo "$TICKET" | jq -r '.number')
echo "✅ 발급 번호: $TICKET_NUMBER"

# 4. 중복 발급 테스트
echo -e "\n4️⃣ 중복 발급 테스트..."
DUPLICATE=$(curl -s -X POST "$BASE_URL/api/queue/room/$ROOM_UID/ticket" \
  -H "Content-Type: application/json" \
  -d "{\"userDeviceId\": \"$DEVICE_ID\"}")
echo "$DUPLICATE" | jq .

IS_DUPLICATED=$(echo "$DUPLICATE" | jq -r '.duplicated')
if [ "$IS_DUPLICATED" = "true" ]; then
  echo "✅ 중복 발급 방지 정상 작동"
else
  echo "❌ 중복 발급 방지 실패!"
fi

# 5. 방 상태 재확인
echo -e "\n5️⃣ 방 상태 재확인..."
curl -s "$BASE_URL/api/queue/room/$ROOM_UID/status" | jq .

echo -e "\n✅ 테스트 완료!"
```

실행:
```bash
chmod +x test_queue_api.sh
./test_queue_api.sh
```

---

## 다음 단계

1. **Swagger UI**에서 모든 API를 직접 테스트해보세요
2. **WebSocket** 연결을 브라우저 콘솔에서 테스트해보세요
3. **Flutter 앱**을 실행하여 실제 통신을 테스트하세요

자세한 Flutter 통합 방법은 [FLUTTER_INTEGRATION_GUIDE.md](FLUTTER_INTEGRATION_GUIDE.md)를 참조하세요.