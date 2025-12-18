# React 번호표 시스템 가이드

본 문서는 React 프론트엔드에서 번호표 시스템을 사용하는 방법을 설명합니다.

**중요**: 이 시스템은 Flutter 가이드([FLUTTER_INTEGRATION_GUIDE.md](FLUTTER_INTEGRATION_GUIDE.md))와 **동일한 API**를 사용합니다.
- Flutter와 React 모두 `/api/queue/*` 엔드포인트 사용
- 동일한 WebSocket STOMP 프로토콜 사용
- 완전히 동일한 백엔드 API 구조

---

## 목차

1. [시스템 구조](#시스템-구조)
2. [서비스 계층](#서비스-계층)
3. [페이지 구성](#페이지-구성)
4. [사용 예제](#사용-예제)
5. [문제 해결](#문제-해결)

---

## 시스템 구조

### 아키텍처 개요

```
React App
  │
  ├─── Services (서비스 계층)
  │    ├─ queueApiService.js  → REST API 호출
  │    ├─ queueService.js      → 통합 서비스 (REST + WebSocket)
  │    └─ socketService.js     → WebSocket 메시지 처리
  │
  ├─── Context
  │    └─ SocketContext.jsx    → WebSocket 연결 관리
  │
  ├─── Pages (페이지)
  │    ├─ RoomPage.jsx         → 사용자용 번호표 발급 화면
  │    └─ AdminPage.jsx        → 관리자용 방 관리 화면
  │
  └─── Utils
       └─ userKey.js            → 디바이스 ID 생성/관리
```

### Flutter와의 비교

| 항목 | Flutter | React |
|------|---------|-------|
| API 서비스 | `QueueApiService` | `queueApiService.js` |
| 통합 서비스 | `QueueServiceProvider` | `QueueService` 클래스 |
| WebSocket | `StompClient` (stomp_dart_client) | `Client` (@stomp/stompjs) |
| 디바이스 ID | `DeviceUtils.getDeviceId()` | `getUserKey()` |
| 상태 관리 | Provider / Riverpod | Context + useState / Zustand |

---

## 서비스 계층

### 1. queueApiService.js

Flutter 가이드의 API 구조와 **완전히 동일**한 REST API 서비스입니다.

```javascript
import { queueApiService } from './services/queueApiService';

// 1. 활성화된 방 목록 조회
const rooms = await queueApiService.getActiveRooms();

// 2. 특정 방 정보 조회
const room = await queueApiService.getRoomInfo(roomId);

// 3. 번호표 발급 (REST API)
const ticket = await queueApiService.issueTicket(roomId, deviceId);

// 4. 방 현황 조회
const status = await queueApiService.getRoomStatus(roomId);

// 5. 방의 모든 번호표 조회
const tickets = await queueApiService.getRoomTickets(roomId);

// 6. 다음 번호 호출 (관리자)
const nextNumber = await queueApiService.callNextNumber(roomId);
```

#### API 엔드포인트 (Flutter와 동일)

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/queue/rooms` | 활성화된 방 목록 |
| GET | `/api/queue/room/{roomId}` | 특정 방 정보 |
| POST | `/api/queue/room/{roomId}/ticket` | 번호표 발급 |
| GET | `/api/queue/room/{roomId}/status` | 방 현황 조회 |
| GET | `/api/queue/room/{roomId}/tickets` | 번호표 목록 |
| POST | `/api/queue/room/{roomId}/call` | 다음 번호 호출 |

### 2. queueService.js (통합 서비스)

Flutter의 `QueueServiceProvider` 패턴을 React에 맞게 구현한 통합 서비스입니다.

```javascript
import { queueService } from './services/queueService';

// WebSocket 클라이언트 설정
queueService.setClient(stompClient);

// 콜백 함수 설정
queueService.onRoomStatusUpdate = (status) => {
  console.log('방 상태 업데이트:', status);
};

queueService.onTicketIssued = (ticket) => {
  console.log('번호표 발급:', ticket);
};

queueService.onNotification = (notification) => {
  console.log('알림:', notification);
};

// 방 입장 (WebSocket 구독 시작)
queueService.joinRoom(roomUid);

// 번호표 발급 (REST API 방식)
const ticket = await queueService.issueTicketViaRest(roomUid);

// 방 현황 새로고침
await queueService.refreshRoomStatus(roomUid);

// 방 나가기 (정리)
queueService.leaveRoom();
```

### 3. socketService.js

WebSocket 메시지 전송/구독을 위한 유틸리티 서비스입니다.

```javascript
import { socketService } from './services/socketService';

// 방 입장 및 상태 구독
const subscription = socketService.joinRoom(
  client,
  roomUid,
  userKey,
  (state) => {
    console.log('상태 업데이트:', state);
  }
);

// 번호표 발급 (WebSocket)
socketService.issueTicket(
  client,
  roomUid,
  userKey,
  (ticket) => console.log('티켓:', ticket),
  (state) => console.log('상태:', state)
);

// 알림 구독
socketService.subscribeToNotifications(
  client,
  roomUid,
  userKey,
  (notification) => {
    console.log('알림 받음:', notification);
  }
);

// 알림 전송 (관리자)
socketService.sendNotification(client, roomUid, number, message);
```

---

## 페이지 구성

### 1. RoomPage.jsx (사용자용)

Flutter의 `RoomDetailScreen`과 동일한 기능을 제공합니다.

#### 주요 기능
- 활성화된 방 목록 표시
- 방 입장 (WebSocket 자동 연결)
- 번호표 발급
- 실시간 방 현황 업데이트
- 알림 수신

#### 코드 예제

```javascript
import { useEffect, useState, useRef } from 'react';
import { useSocketContext } from '../contexts/SocketContext';
import { queueService } from '../services/queueService';

const RoomPage = () => {
  const { client, connected } = useSocketContext();
  const [currentRoomStatus, setCurrentRoomStatus] = useState(null);
  const [myTicket, setMyTicket] = useState(null);
  const serviceRef = useRef(queueService);

  useEffect(() => {
    if (!roomUid || !client || !connected) return;

    const service = serviceRef.current;
    service.setClient(client);

    // 콜백 설정
    service.onRoomStatusUpdate = (status) => {
      setCurrentRoomStatus(status);
    };

    service.onTicketIssued = (ticket) => {
      setMyTicket(ticket);
    };

    // 방 입장
    service.joinRoom(roomUid);
    service.refreshRoomStatus(roomUid);

    return () => {
      service.leaveRoom();
    };
  }, [roomUid, client, connected]);

  const handleIssueTicket = async () => {
    await serviceRef.current.issueTicketViaRest(roomUid);
  };

  return (
    <div>
      {/* 방 현황 표시 */}
      {currentRoomStatus && (
        <div>
          <p>현재 번호: {currentRoomStatus.currentNumber}</p>
          <p>대기 중: {currentRoomStatus.waitingCount}명</p>
        </div>
      )}

      {/* 내 번호표 */}
      {myTicket ? (
        <div>
          <h2>내 번호: {myTicket.number}</h2>
          <p>앞에 {myTicket.number - currentRoomStatus.currentNumber}명 대기 중</p>
        </div>
      ) : (
        <button onClick={handleIssueTicket}>번호표 발급받기</button>
      )}
    </div>
  );
};
```

### 2. AdminPage.jsx (관리자용)

관리자가 모든 방을 관리할 수 있는 페이지입니다.

#### 주요 기능
- 모든 방 목록 조회 (상세 정보 포함)
- 새 방 생성 / 일괄 생성
- 다음 번호 호출
- 특정 번호에 알림 전송
- 방 초기화
- 방 삭제

#### 코드 예제

```javascript
import { adminService, queueService } from '../services/queueService';
import { socketService } from '../services/socketService';

const AdminPage = () => {
  const [rooms, setRooms] = useState([]);

  const loadRooms = async () => {
    const data = await adminService.getAllRoomsWithDetails();
    setRooms(data);
  };

  const handleCreateRoom = async () => {
    const title = prompt('방 제목을 입력하세요:');
    const result = await adminService.createRoom(title);
    alert(`방 생성 완료: ${result.roomUid}`);
    loadRooms();
  };

  const handleCallNextNumber = async (roomUid) => {
    const nextNumber = await queueService.callNextNumber(roomUid);
    alert(`번호 ${nextNumber} 호출됨`);
    loadRooms();
  };

  const handleSendNotification = (roomUid) => {
    const number = parseInt(prompt('번호 입력:'));
    const message = prompt('메시지 입력:');
    socketService.sendNotification(client, roomUid, number, message);
  };

  return (
    <div>
      <button onClick={handleCreateRoom}>새 방 만들기</button>
      <table>
        <thead>
          <tr>
            <th>방 코드</th>
            <th>방 제목</th>
            <th>현재 번호</th>
            <th>관리</th>
          </tr>
        </thead>
        <tbody>
          {rooms.map((room) => (
            <tr key={room.roomUid}>
              <td>{room.roomUid}</td>
              <td>{room.title}</td>
              <td>{room.currentNumber}</td>
              <td>
                <button onClick={() => handleCallNextNumber(room.roomUid)}>
                  호출
                </button>
                <button onClick={() => handleSendNotification(room.roomUid)}>
                  알림
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};
```

---

## 사용 예제

### 전체 흐름

#### 1. 사용자: 방 목록 조회 및 입장

```javascript
// Step 1: 방 목록 가져오기
const rooms = await queueService.fetchActiveRooms();

// Step 2: 방 선택 후 입장
queueService.setClient(stompClient);
queueService.onRoomStatusUpdate = (status) => {
  console.log('현재 번호:', status.currentNumber);
};

queueService.joinRoom(roomUid);

// Step 3: 현재 상태 가져오기
await queueService.refreshRoomStatus(roomUid);
```

#### 2. 사용자: 번호표 발급

```javascript
// REST API 방식 (추천)
queueService.onTicketIssued = (ticket) => {
  console.log('내 번호:', ticket.number);
  console.log('중복 발급:', ticket.duplicated);
};

const ticket = await queueService.issueTicketViaRest(roomUid);

// WebSocket 방식 (실시간)
queueService.issueTicketViaWebSocket();
```

#### 3. 관리자: 방 생성 및 관리

```javascript
// 방 생성
const newRoom = await adminService.createRoom('물금청소년문화의집');
console.log('방 코드:', newRoom.roomUid);

// 다음 번호 호출
const nextNumber = await queueService.callNextNumber(roomUid);
console.log('호출 번호:', nextNumber);

// 방 초기화
await adminService.resetRoom(roomUid);

// 방 삭제
await adminService.deleteRoom(roomId);
```

---

## 문제 해결

### 1. WebSocket 연결이 안 됨

**증상**: `connected` 상태가 계속 `false`

**해결 방법**:
1. SocketContext가 App에 제대로 래핑되어 있는지 확인
2. 서버 주소가 올바른지 확인 (`.env` 파일)
3. 브라우저 콘솔에서 WebSocket 에러 확인
4. CORS 설정 확인 (이미 설정됨)

```javascript
// App.jsx
import { SocketProvider } from './contexts/SocketContext';

function App() {
  return (
    <SocketProvider>
      <RouterProvider router={router} />
    </SocketProvider>
  );
}
```

### 2. API 호출 실패

**증상**: `Failed to fetch` 또는 CORS 에러

**해결 방법**:
1. `.env.development` 파일 확인
   ```env
   VITE_API_URL=http://localhost:8080
   ```

2. 서버가 실행 중인지 확인
   ```bash
   curl http://localhost:8080/api/queue/rooms
   ```

3. 네트워크 탭에서 실제 요청 URL 확인

### 3. 번호표가 발급되지 않음

**증상**: `issueTicket` 호출해도 번호표가 안 나옴

**해결 방법**:
1. 콜백 함수가 제대로 설정되어 있는지 확인
   ```javascript
   queueService.onTicketIssued = (ticket) => {
     console.log('Ticket:', ticket);
     setMyTicket(ticket);
   };
   ```

2. 디바이스 ID가 제대로 생성되는지 확인
   ```javascript
   const deviceId = queueService.getDeviceId();
   console.log('Device ID:', deviceId);
   ```

3. 브라우저 개발자 도구의 Application > Local Storage 확인

### 4. 실시간 업데이트가 안 됨

**증상**: 다른 사용자가 번호표를 발급받아도 화면이 안 바뀜

**해결 방법**:
1. WebSocket 연결 상태 확인
2. 방에 제대로 입장했는지 확인 (`joinRoom` 호출)
3. 콜백 함수 설정 확인
4. 브라우저 콘솔에서 WebSocket 메시지 확인

```javascript
// 디버깅 로그 확인
queueService.onRoomStatusUpdate = (status) => {
  console.log('[DEBUG] Status update:', status);
  setCurrentRoomStatus(status);
};
```

### 5. 알림이 도착하지 않음

**증상**: 관리자가 알림을 보내도 사용자가 받지 못함

**해결 방법**:
1. 알림 구독이 제대로 되어 있는지 확인
   ```javascript
   queueService.onNotification = (notification) => {
     console.log('[DEBUG] Notification:', notification);
     setNotification(notification);
   };
   ```

2. WebSocket이 연결되어 있는지 확인
3. 올바른 번호를 입력했는지 확인
4. 브라우저 개발자 도구의 WS 탭 확인

---

## Flutter vs React 비교표

| 기능 | Flutter | React |
|------|---------|-------|
| **API 서비스** | `QueueApiService` | `queueApiService.js` |
| **통합 서비스** | `QueueServiceProvider` | `QueueService` 클래스 |
| **WebSocket 라이브러리** | `stomp_dart_client` | `@stomp/stompjs` |
| **디바이스 ID** | `uuid` + `device_info_plus` | `localStorage` + random |
| **방 목록 조회** | `getActiveRooms()` | `fetchActiveRooms()` |
| **번호표 발급 (REST)** | `issueTicket(roomId, deviceId)` | `issueTicketViaRest(roomId)` |
| **번호표 발급 (WS)** | `issueTicketViaWebSocket()` | `issueTicketViaWebSocket()` |
| **방 입장** | `joinRoom(roomUid)` | `joinRoom(roomUid)` |
| **상태 업데이트** | `onRoomStatusUpdate` 콜백 | `onRoomStatusUpdate` 콜백 |
| **알림 수신** | `onNotification` 콜백 | `onNotification` 콜백 |

**핵심**: Flutter와 React 모두 **동일한 백엔드 API**를 사용하므로, 로직과 흐름이 완전히 동일합니다!

---

## API 엔드포인트 요약

### REST API (Flutter & React 공통)

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/queue/rooms` | 활성화된 방 목록 |
| GET | `/api/queue/room/{roomId}` | 방 정보 조회 |
| POST | `/api/queue/room/{roomId}/ticket` | 번호표 발급 |
| GET | `/api/queue/room/{roomId}/status` | 방 현황 조회 |
| GET | `/api/queue/room/{roomId}/tickets` | 번호표 목록 |
| POST | `/api/queue/room/{roomId}/call` | 다음 번호 호출 |

### WebSocket (Flutter & React 공통)

| Type | Destination | 설명 |
|------|-------------|------|
| 연결 | `/ws` | WebSocket 엔드포인트 |
| 전송 | `/app/room/join` | 방 입장 |
| 전송 | `/app/room/issue` | 번호표 발급 |
| 전송 | `/app/room/notify` | 알림 전송 |
| 구독 | `/topic/room/{roomUid}/state` | 방 상태 업데이트 |
| 구독 | `/user/queue/ticket` | 개인 티켓 수신 |
| 구독 | `/topic/room/{roomUid}/notification/{userKey}` | 알림 수신 |

---

## 추가 참고 자료

- **Flutter 가이드**: [FLUTTER_INTEGRATION_GUIDE.md](FLUTTER_INTEGRATION_GUIDE.md)
- **Flutter 빠른 시작**: [FLUTTER_QUICKSTART.md](FLUTTER_QUICKSTART.md)
- **API 테스트 가이드**: [API_TESTING_GUIDE.md](API_TESTING_GUIDE.md)
- **비즈니스 로직**: [BUSINESS_LOGIC_FOR_PAPER.md](BUSINESS_LOGIC_FOR_PAPER.md)

---

## 요약

✅ **Flutter와 React 모두 동일한 API 사용**
✅ **서비스 계층 패턴이 유사함** (QueueService)
✅ **WebSocket 통신 방식 동일** (STOMP)
✅ **완전히 호환 가능** (Flutter 앱과 React 웹이 동시에 사용 가능)

React 번호표 시스템은 Flutter 가이드와 **100% 호환**되도록 설계되었습니다!
