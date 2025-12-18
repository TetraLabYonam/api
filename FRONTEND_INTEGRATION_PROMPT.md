# 프론트엔드 통합 프롬프트

본 문서는 번호표 시스템의 React 프론트엔드 통합을 위한 프롬프트입니다. Flutter 앱과 100% API 호환성을 유지하며, 동일한 백엔드를 공유합니다.

---

## 시스템 개요

**프로젝트**: 실시간 번호표 발급 및 관리 시스템
**백엔드**: Spring Boot 3.5.6 (Java 17) + WebSocket (STOMP)
**프론트엔드**: React 19.1.1 + Vite + Zustand
**실시간 통신**: WebSocket (SockJS + STOMP)
**데이터베이스**: MariaDB (프로덕션) / H2 (로컬)

---

## 핵심 아키텍처

### 1. 서비스 레이어 구조

프론트엔드는 Flutter와 동일한 서비스 패턴을 사용합니다:

```
frontend/src/services/
├── api.js              # Axios 인스턴스 및 기본 설정
├── queueApiService.js  # REST API 전용 서비스 (Flutter QueueApiService와 동일)
├── queueService.js     # 통합 서비스 (REST + WebSocket, Flutter QueueServiceProvider와 동일)
└── socketService.js    # WebSocket STOMP 클라이언트 관리
```

### 2. API 엔드포인트 (Flutter와 100% 동일)

#### REST API
- `GET /api/queue/rooms` - 활성화된 방 목록
- `GET /api/queue/room/{roomUid}` - 방 정보 조회
- `POST /api/queue/room/{roomUid}/ticket` - 번호표 발급
- `GET /api/queue/room/{roomUid}/status` - 방 현황 조회
- `GET /api/queue/room/{roomUid}/tickets` - 번호표 목록
- `POST /api/queue/room/{roomUid}/call` - 다음 번호 호출 (관리자)

#### WebSocket STOMP
- **연결**: `/ws` (SockJS 엔드포인트)
- **구독**:
  - `/topic/room/{roomUid}/state` - 방 상태 실시간 업데이트
  - `/user/queue/ticket` - 개인 티켓 수신
  - `/topic/room/{roomUid}/notification/{userKey}` - 알림 수신
- **전송**:
  - `/app/room/join` - 방 입장
  - `/app/room/issue` - 번호표 발급 (WebSocket)
  - `/app/room/notify` - 알림 전송 (관리자)

---

## 구현 가이드

### STEP 1: 환경 변수 설정

**파일**: `frontend/.env.local`

```env
# 백엔드 API URL
VITE_API_URL=http://localhost:8080

# Google Maps API 키 (선택 - 장소 관리 기능 사용 시)
VITE_GOOGLE_MAPS_API_KEY=YOUR_API_KEY_HERE
```

**중요**:
- Vite는 `VITE_` 접두사 필수
- `.env.local` 파일 생성 후 개발 서버 재시작 필수
- 프로덕션 배포 시 `.env.production` 사용

---

### STEP 2: REST API 서비스 (queueApiService.js)

**역할**: HTTP 요청만 담당 (Flutter의 QueueApiService와 동일)

```javascript
import apiClient from './api';

export const queueApiService = {
  // 1. 활성화된 방 목록 조회
  async getActiveRooms() {
    const response = await apiClient.get('/api/queue/rooms');
    return response.data;
  },

  // 2. 번호표 발급 (REST)
  async issueTicket(roomId, deviceId) {
    const response = await apiClient.post(
      `/api/queue/room/${roomId}/ticket`,
      { userDeviceId: deviceId }
    );
    return response.data;
  },

  // 3. 방 현황 조회
  async getRoomStatus(roomId) {
    const response = await apiClient.get(`/api/queue/room/${roomId}/status`);
    return response.data;
  },

  // 4. 방 정보 조회
  async getRoom(roomId) {
    const response = await apiClient.get(`/api/queue/room/${roomId}`);
    return response.data;
  },

  // 5. 번호표 목록 조회
  async getTickets(roomId) {
    const response = await apiClient.get(`/api/queue/room/${roomId}/tickets`);
    return response.data;
  }
};
```

---

### STEP 3: 통합 서비스 (queueService.js)

**역할**: REST API + WebSocket 통합 관리 (Flutter의 QueueServiceProvider와 동일)

```javascript
import { queueApiService } from './queueApiService';
import { socketService } from './socketService';
import { getUserKey } from './userKeyService';

export class QueueService {
  constructor() {
    this.client = null;
    this.deviceId = getUserKey();
    this.subscriptions = [];

    // 콜백 함수들 (Flutter의 notifyListeners 패턴과 동일)
    this.onRoomStatusUpdate = null;
    this.onTicketIssued = null;
    this.onNotification = null;
  }

  // WebSocket 클라이언트 설정
  setClient(client) {
    this.client = client;
  }

  // 방 입장 (WebSocket 구독 시작)
  joinRoom(roomUid) {
    if (!this.client) return;

    // 1. 방 상태 구독
    const stateSub = socketService.joinRoom(
      this.client,
      roomUid,
      this.deviceId,
      (data) => {
        console.log('방 상태 업데이트:', data);
        this.onRoomStatusUpdate?.(data);
      }
    );
    this.subscriptions.push(stateSub);

    // 2. 개인 티켓 수신 구독
    const ticketSub = socketService.subscribeToTicket(
      this.client,
      (ticket) => {
        console.log('티켓 발급됨:', ticket);
        this.onTicketIssued?.(ticket);
      }
    );
    this.subscriptions.push(ticketSub);

    // 3. 알림 수신 구독
    const notifSub = socketService.subscribeToNotification(
      this.client,
      roomUid,
      this.deviceId,
      (notification) => {
        console.log('알림 수신:', notification);
        this.onNotification?.(notification);
      }
    );
    this.subscriptions.push(notifSub);
  }

  // 방 나가기 (구독 해제)
  leaveRoom() {
    this.subscriptions.forEach(sub => sub.unsubscribe());
    this.subscriptions = [];
  }

  // 번호표 발급 (REST API 사용)
  async issueTicketViaRest(roomUid) {
    const ticket = await queueApiService.issueTicket(roomUid, this.deviceId);
    this.onTicketIssued?.(ticket);
    return ticket;
  }

  // 방 현황 새로고침 (REST API)
  async refreshRoomStatus(roomUid) {
    const status = await queueApiService.getRoomStatus(roomUid);
    this.onRoomStatusUpdate?.(status);
    return status;
  }

  // 다음 번호 호출 (관리자)
  async callNextNumber(roomUid) {
    const response = await apiClient.post(`/api/queue/room/${roomUid}/call`);
    return response.data;
  }
}

// 싱글톤 인스턴스
export const queueService = new QueueService();
```

---

### STEP 4: WebSocket 서비스 (socketService.js)

**역할**: STOMP 클라이언트 연결 및 구독 관리

```javascript
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export const socketService = {
  // STOMP 클라이언트 생성
  createClient(onConnect, onError) {
    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_URL}/ws`),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        console.log('WebSocket 연결됨');
        onConnect?.();
      },
      onStompError: (frame) => {
        console.error('STOMP 에러:', frame);
        onError?.(frame);
      },
      onWebSocketError: (error) => {
        console.error('WebSocket 에러:', error);
        onError?.(error);
      },
    });
    return client;
  },

  // 방 입장 (상태 구독)
  joinRoom(client, roomUid, userKey, callback) {
    const destination = `/topic/room/${roomUid}/state`;

    const subscription = client.subscribe(destination, (message) => {
      const data = JSON.parse(message.body);
      callback(data);
    });

    // 입장 메시지 전송
    client.publish({
      destination: '/app/room/join',
      body: JSON.stringify({ roomUid, userKey })
    });

    return subscription;
  },

  // 개인 티켓 수신 구독
  subscribeToTicket(client, callback) {
    return client.subscribe('/user/queue/ticket', (message) => {
      const ticket = JSON.parse(message.body);
      callback(ticket);
    });
  },

  // 알림 수신 구독
  subscribeToNotification(client, roomUid, userKey, callback) {
    const destination = `/topic/room/${roomUid}/notification/${userKey}`;
    return client.subscribe(destination, (message) => {
      const notification = JSON.parse(message.body);
      callback(notification);
    });
  },

  // 번호표 발급 (WebSocket)
  issueTicket(client, roomUid, userKey) {
    client.publish({
      destination: '/app/room/issue',
      body: JSON.stringify({ roomUid, userKey })
    });
  }
};
```

---

### STEP 5: 사용자 페이지 구현 (RoomPage.jsx)

**기능**: 번호표 발급 및 실시간 상태 확인

```javascript
import { useEffect, useState, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { socketService } from '../services/socketService';
import { queueService } from '../services/queueService';
import { useSocketStore } from '../store/socketStore';

export default function RoomPage() {
  const { roomUid } = useParams();
  const { client, connected } = useSocketStore();

  const [currentRoomStatus, setCurrentRoomStatus] = useState(null);
  const [myTicket, setMyTicket] = useState(null);
  const [notification, setNotification] = useState(null);

  const serviceRef = useRef(queueService);

  // WebSocket 연결 및 구독 설정
  useEffect(() => {
    if (!connected || !client) return;

    const service = serviceRef.current;
    service.setClient(client);

    // 콜백 등록
    service.onRoomStatusUpdate = (status) => {
      console.log('방 상태 업데이트:', status);
      setCurrentRoomStatus(status);
    };

    service.onTicketIssued = (ticket) => {
      console.log('티켓 발급:', ticket);
      setMyTicket(ticket);
    };

    service.onNotification = (notif) => {
      console.log('알림:', notif);
      setNotification(notif);
      setTimeout(() => setNotification(null), 5000);
    };

    // 방 입장 및 초기 데이터 로드
    service.joinRoom(roomUid);
    service.refreshRoomStatus(roomUid);

    // 정리
    return () => {
      service.leaveRoom();
    };
  }, [roomUid, client, connected]);

  // 번호표 발급 핸들러
  const handleIssueTicket = async () => {
    try {
      await serviceRef.current.issueTicketViaRest(roomUid);
    } catch (error) {
      console.error('번호표 발급 실패:', error);
      alert('번호표 발급에 실패했습니다.');
    }
  };

  // 대기 인원 계산
  const waitingCount = myTicket && currentRoomStatus
    ? Math.max(0, myTicket.number - currentRoomStatus.currentNumber)
    : 0;

  return (
    <div className="room-page">
      <h1>{currentRoomStatus?.roomName || '로딩 중...'}</h1>

      {/* 연결 상태 */}
      <div className="connection-status">
        {connected ? '✓ 실시간 연결됨' : '✗ 연결 끊김'}
      </div>

      {/* 현재 번호 */}
      <div className="current-number">
        <h2>현재 번호</h2>
        <div className="number-display">
          {currentRoomStatus?.currentNumber || 0}
        </div>
      </div>

      {/* 내 번호표 */}
      {myTicket ? (
        <div className="my-ticket">
          <h3>내 번호</h3>
          <div className="ticket-number">{myTicket.number}</div>
          <p>대기: {waitingCount}명</p>
        </div>
      ) : (
        <button onClick={handleIssueTicket}>
          번호표 발급받기
        </button>
      )}

      {/* 알림 */}
      {notification && (
        <div className="notification-popup">
          <p>{notification.message}</p>
        </div>
      )}
    </div>
  );
}
```

---

### STEP 6: 관리자 페이지 구현 (AdminPage.jsx)

**기능**: 방 생성, 번호 호출, 알림 전송, 방 관리

```javascript
import { useEffect, useState } from 'react';
import { queueApiService } from '../services/queueApiService';
import { queueService } from '../services/queueService';
import apiClient from '../services/api';

export default function AdminPage() {
  const [rooms, setRooms] = useState([]);
  const [loading, setLoading] = useState(false);

  // 방 목록 로드
  const loadRooms = async () => {
    setLoading(true);
    try {
      const data = await queueApiService.getActiveRooms();
      setRooms(data);
    } catch (error) {
      console.error('방 목록 로드 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadRooms();
  }, []);

  // 새 방 생성
  const handleCreateRoom = async () => {
    const roomName = prompt('방 이름을 입력하세요:');
    if (!roomName) return;

    try {
      await apiClient.post('/api/v1/rooms', { roomName });
      alert('방이 생성되었습니다!');
      loadRooms();
    } catch (error) {
      console.error('방 생성 실패:', error);
      alert('방 생성에 실패했습니다.');
    }
  };

  // 일괄 생성
  const handleBatchCreateRooms = async () => {
    const input = prompt('방 이름들을 쉼표(,)로 구분하여 입력하세요:');
    if (!input) return;

    const roomNames = input.split(',').map(name => name.trim()).filter(Boolean);

    try {
      await apiClient.post('/api/v1/rooms/batch', { roomNames });
      alert(`${roomNames.length}개의 방이 생성되었습니다!`);
      loadRooms();
    } catch (error) {
      console.error('일괄 생성 실패:', error);
      alert('일괄 생성에 실패했습니다.');
    }
  };

  // 다음 번호 호출
  const handleCallNextNumber = async (roomUid, roomName) => {
    try {
      const response = await queueService.callNextNumber(roomUid);
      alert(`[${roomName}] 번호 ${response.currentNumber}이(가) 호출되었습니다!`);
      loadRooms();
    } catch (error) {
      console.error('번호 호출 실패:', error);
      alert('번호 호출에 실패했습니다.');
    }
  };

  // 알림 전송
  const handleSendNotification = async (roomUid) => {
    const ticketNumber = parseInt(prompt('알림을 보낼 번호를 입력하세요:'));
    const message = prompt('메시지를 입력하세요:');

    if (!ticketNumber || !message) return;

    try {
      await apiClient.post(`/api/v1/rooms/${roomUid}/notify`, {
        ticketNumber,
        message
      });
      alert('알림이 전송되었습니다!');
    } catch (error) {
      console.error('알림 전송 실패:', error);
      alert('알림 전송에 실패했습니다.');
    }
  };

  // 방 초기화
  const handleResetRoom = async (roomUid, roomName) => {
    if (!confirm(`[${roomName}] 방을 초기화하시겠습니까?`)) return;

    try {
      await apiClient.post(`/api/v1/rooms/${roomUid}/reset`);
      alert('방이 초기화되었습니다!');
      loadRooms();
    } catch (error) {
      console.error('초기화 실패:', error);
      alert('초기화에 실패했습니다.');
    }
  };

  // 방 삭제
  const handleDeleteRoom = async (roomId, roomName) => {
    if (!confirm(`[${roomName}] 방을 삭제하시겠습니까?`)) return;

    try {
      await apiClient.delete(`/api/v1/rooms/${roomId}`);
      alert('방이 삭제되었습니다!');
      loadRooms();
    } catch (error) {
      console.error('삭제 실패:', error);
      alert('삭제에 실패했습니다.');
    }
  };

  return (
    <div className="admin-page">
      <h1>관리자 페이지</h1>

      <div className="admin-actions">
        <button onClick={handleCreateRoom}>새 방 만들기</button>
        <button onClick={handleBatchCreateRooms}>일괄 생성</button>
        <button onClick={loadRooms}>새로고침</button>
      </div>

      {loading ? (
        <p>로딩 중...</p>
      ) : (
        <div className="rooms-grid">
          {rooms.map((room) => (
            <div key={room.id} className="room-card">
              <h3>{room.roomName}</h3>
              <p>현재: {room.currentNumber} | 발급: {room.lastIssuedNumber}</p>
              <p>대기: {room.waitingCount}명</p>

              <div className="room-actions">
                <button onClick={() => handleCallNextNumber(room.roomUid, room.roomName)}>
                  호출
                </button>
                <button onClick={() => handleSendNotification(room.roomUid)}>
                  알림
                </button>
                <button onClick={() => handleResetRoom(room.roomUid, room.roomName)}>
                  초기화
                </button>
                <button onClick={() => handleDeleteRoom(room.id, room.roomName)}>
                  삭제
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
```

---

### STEP 7: WebSocket 상태 관리 (Zustand)

**파일**: `frontend/src/store/socketStore.js`

```javascript
import { create } from 'zustand';
import { socketService } from '../services/socketService';

export const useSocketStore = create((set) => ({
  client: null,
  connected: false,

  connect: () => {
    const client = socketService.createClient(
      () => set({ connected: true }),
      () => set({ connected: false })
    );
    client.activate();
    set({ client });
  },

  disconnect: () => {
    set((state) => {
      state.client?.deactivate();
      return { client: null, connected: false };
    });
  }
}));
```

**App.jsx에서 전역 연결**:

```javascript
import { useEffect } from 'react';
import { useSocketStore } from './store/socketStore';

function App() {
  const { connect, disconnect } = useSocketStore();

  useEffect(() => {
    connect();
    return () => disconnect();
  }, []);

  return (
    // ... 라우터 설정
  );
}
```

---

## 실행 가이드

### 1. 백엔드 실행

```bash
# H2 인메모리 DB 사용 (권장)
./gradlew bootRun --args='--spring.profiles.active=local'

# 또는 MariaDB 사용
./gradlew bootRun
```

**확인**: http://localhost:8080/swagger-ui/index.html

### 2. 프론트엔드 실행

```bash
cd frontend

# 환경 변수 설정
cp .env.example .env.local
# .env.local 파일에서 VITE_API_URL=http://localhost:8080 확인

# 의존성 설치
npm install

# 개발 서버 실행
npm run dev
```

**접속**: http://localhost:5173

### 3. 시연 시나리오

#### 시나리오 A: 사용자 흐름
1. `http://localhost:5173/rooms` 접속
2. 방 목록에서 원하는 방 선택
3. "번호표 발급받기" 클릭
4. 내 번호와 대기 인원 확인
5. 실시간으로 현재 번호 업데이트 확인

#### 시나리오 B: 관리자 흐름
1. `http://localhost:5173/admin` 접속
2. "새 방 만들기" 클릭하여 방 생성
3. "호출" 버튼으로 다음 번호 호출
4. "알림" 버튼으로 특정 번호에 메시지 전송
5. 사용자 화면에서 실시간 업데이트 확인

#### 시나리오 C: 멀티 브라우저 테스트
1. 브라우저 A: 사용자 페이지 (`/rooms/{roomUid}`)
2. 브라우저 B: 다른 사용자 (시크릿 모드)
3. 브라우저 C: 관리자 페이지 (`/admin`)
4. 관리자가 번호 호출 시 모든 브라우저에 실시간 반영 확인

---

## 주요 특징

### 1. Flutter와 100% API 호환
- REST 엔드포인트 동일
- WebSocket 토픽 동일
- 데이터 구조 동일
- 동일한 백엔드 동시 사용 가능

### 2. 실시간 양방향 통신
- WebSocket STOMP 프로토콜
- SockJS fallback (브라우저 호환성)
- 자동 재연결 (5초 간격)
- Heartbeat (4초 간격)

### 3. 서비스 레이어 패턴
- `queueApiService`: REST API만 담당
- `queueService`: REST + WebSocket 통합
- `socketService`: WebSocket 연결 관리
- 명확한 책임 분리

### 4. 콜백 기반 상태 관리
- Flutter의 `notifyListeners` 패턴과 동일
- `onRoomStatusUpdate`, `onTicketIssued`, `onNotification`
- React 컴포넌트에서 자유롭게 커스터마이징 가능

---

## 문제 해결

### CORS 에러
```
Access to XMLHttpRequest ... has been blocked by CORS policy
```
**해결**: 백엔드 `CorsConfig.java`에서 `allowedOriginPatterns("*")` 설정 확인

### WebSocket 연결 실패
```
WebSocket connection to 'ws://localhost:8080/ws' failed
```
**해결**:
1. 백엔드 서버 실행 확인
2. `.env.local`의 `VITE_API_URL` 확인
3. SockJS 엔드포인트 확인: `/ws`

### 환경 변수 미적용
**해결**:
1. `VITE_` 접두사 확인
2. `.env.local` 파일 저장 확인
3. 개발 서버 재시작 (`npm run dev`)

---

## 참고 문서

- **Flutter 가이드**: `FLUTTER_INTEGRATION_GUIDE.md` (절대 수정 금지)
- **React 가이드**: `REACT_QUEUE_SYSTEM_GUIDE.md`
- **API 테스트**: `API_TESTING_GUIDE.md`
- **로컬 데모**: `LOCAL_DEMO_GUIDE.md`
- **백엔드 설정**: `BACKEND_CONFIG_CHECKLIST.md`

---

## 핵심 원칙

1. **Flutter 문서 수정 금지**: Flutter 가이드는 모바일 앱 개발에 그대로 사용됩니다.
2. **API 호환성 유지**: 모든 엔드포인트와 데이터 구조는 Flutter와 동일해야 합니다.
3. **서비스 패턴 준수**: REST와 WebSocket을 명확히 분리합니다.
4. **실시간성 보장**: WebSocket을 통한 즉각적인 상태 업데이트를 구현합니다.

---

이 프롬프트를 기반으로 React 프론트엔드를 구현하면 Flutter 앱과 완벽하게 호환되며, 동일한 백엔드를 공유하여 실시간 동기화가 가능합니다.
