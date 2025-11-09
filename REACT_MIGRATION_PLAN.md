# 타임리프 → 리액트 마이그레이션 계획서

## 📋 목차
1. [현재 아키텍처 분석](#현재-아키텍처-분석)
2. [목표 아키텍처](#목표-아키텍처)
3. [기술 스택 변경](#기술-스택-변경)
4. [마이그레이션 단계](#마이그레이션-단계)
5. [컴포넌트 구조](#컴포넌트-구조)
6. [API 엔드포인트 정리](#api-엔드포인트-정리)
7. [라우팅 구조](#라우팅-구조)
8. [상태 관리](#상태-관리)
9. [Socket.IO 통합](#socketio-통합)
10. [주의사항 및 고려사항](#주의사항-및-고려사항)

---

## 현재 아키텍처 분석

### 백엔드 (Spring Boot)
- **프레임워크**: Spring Boot 3.x
- **언어**: Java
- **데이터베이스**: MariaDB (JPA/Hibernate)
- **실시간 통신**: Socket.IO (netty-socketio)
- **API 스타일**: REST API + Server-Side Rendering (Thymeleaf)

### 프론트엔드 (Thymeleaf)
- **템플릿 엔진**: Thymeleaf
- **스타일**: Inline CSS
- **JavaScript**: Vanilla JS
- **실시간 통신**: Socket.IO Client (CDN)

### 현재 페이지 구조
```
src/main/resources/
├── static/
│   └── index.html              # 메인 페이지
└── templates/
    ├── hello.html              # Hello 페이지
    ├── map.html                # 지도 페이지
    ├── excel.html              # Excel 관리
    ├── member.html             # 회원 관리
    ├── schedule.html           # 일정 관리
    ├── room.html               # 번호표 시스템 (일반 사용자)
    └── roomAdmin.html          # 방 관리 (관리자)
```

### 현재 API 엔드포인트
| 엔드포인트 | 메소드 | 설명 |
|-----------|--------|------|
| `/api/rooms` | GET | 방 목록 조회 |
| `/api/rooms` | POST | 방 생성 |
| `/api/rooms/details` | GET | 방 상세 정보 (관리자) |
| `/api/rooms/{roomUid}/reset` | POST | 방 초기화 |
| `/api/rooms/{roomUid}/issuances` | GET | 발급 목록 조회 |
| `/api/members` | GET/POST | 회원 관리 |
| `/api/places` | GET/POST | 장소 관리 |

### Socket.IO 이벤트
| 이벤트 | 방향 | 설명 |
|--------|------|------|
| `join` | Client → Server | 방 입장 |
| `issue` | Client → Server | 번호표 발급 |
| `notify` | Client → Server | 특정 번호에 알림 전송 |
| `state` | Server → Client | 방 상태 업데이트 |
| `issued` | Server → Client | 번호표 발급 완료 |
| `notification` | Server → Client | 알림 수신 |

---

## 목표 아키텍처

### 백엔드 (Spring Boot - API Only)
```
Spring Boot Application
├── REST API Server (Port 8080)
│   ├── CORS 설정 (React 개발 서버 허용)
│   ├── JSON 응답
│   └── JWT 인증 (선택사항)
└── Socket.IO Server (Port 9092)
    └── 실시간 통신
```

### 프론트엔드 (React)
```
React Application (Port 3000 - 개발 시)
├── React 18+
├── React Router v6
├── Socket.IO Client
├── Axios (HTTP Client)
├── CSS Modules / Styled Components
└── Zustand / Redux (상태 관리)
```

---

## 기술 스택 변경

### 추가될 기술
| 기술 | 용도 | 우선순위 |
|------|------|----------|
| React 18+ | UI 라이브러리 | 필수 |
| React Router v6 | 클라이언트 라우팅 | 필수 |
| Axios | HTTP 클라이언트 | 필수 |
| Socket.IO Client | 실시간 통신 | 필수 |
| Vite / Create React App | 빌드 도구 | 필수 |
| Zustand / Redux | 전역 상태 관리 | 권장 |
| React Query | 서버 상태 관리 | 권장 |
| Styled Components / Tailwind CSS | 스타일링 | 권장 |
| TypeScript | 타입 안정성 | 선택 |

### 제거될 기술
- Thymeleaf
- Server-Side Rendering
- Inline JavaScript

---

## 마이그레이션 단계

### Phase 1: 프로젝트 설정 (1-2일)

#### 1.1 React 프로젝트 초기화
```bash
# Vite 사용 (권장 - 빠른 빌드)
npm create vite@latest frontend -- --template react

# 또는 Create React App
npx create-react-app frontend
```

#### 1.2 디렉토리 구조 생성
```
attempt/
├── src/main/java/              # Spring Boot (백엔드)
├── frontend/                   # React 프로젝트 (신규)
│   ├── public/
│   ├── src/
│   │   ├── components/        # 재사용 가능한 컴포넌트
│   │   ├── pages/            # 페이지 컴포넌트
│   │   ├── services/         # API 호출 로직
│   │   ├── hooks/            # 커스텀 훅
│   │   ├── contexts/         # Context API
│   │   ├── utils/            # 유틸리티 함수
│   │   ├── styles/           # 전역 스타일
│   │   ├── App.jsx
│   │   └── main.jsx
│   ├── package.json
│   └── vite.config.js
└── build.gradle
```

#### 1.3 필수 패키지 설치
```bash
cd frontend

# 필수 패키지
npm install react-router-dom axios socket.io-client

# 상태 관리 (Zustand 권장 - 간단함)
npm install zustand

# 또는 Redux Toolkit
npm install @reduxjs/toolkit react-redux

# 스타일링 (선택)
npm install styled-components
# 또는
npm install -D tailwindcss postcss autoprefixer

# 서버 상태 관리 (선택)
npm install @tanstack/react-query

# TypeScript 사용 시
npm install -D typescript @types/react @types/react-dom
```

---

### Phase 2: 백엔드 API 정비 (2-3일)

#### 2.1 CORS 설정 추가
```java
// src/main/java/com/example/attempt/config/CorsConfig.java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000", "http://localhost:5173") // Vite 기본 포트
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
```

#### 2.2 REST API 통일
- 모든 API 응답을 JSON으로 통일
- 에러 응답 표준화
- DTO 클래스 정리

```java
// 표준 응답 형식
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String message;
    private String error;
}
```

#### 2.3 페이지 컨트롤러 제거/변경
```java
// DemoPage.java - 제거 또는 리다이렉트로 변경
// Thymeleaf 페이지 매핑은 모두 제거
// REST API만 유지
```

---

### Phase 3: 공통 컴포넌트 개발 (3-4일)

#### 3.1 레이아웃 컴포넌트
```jsx
// src/components/Layout/MainLayout.jsx
// src/components/Layout/Header.jsx
// src/components/Layout/Footer.jsx
// src/components/Layout/Sidebar.jsx
```

#### 3.2 공통 UI 컴포넌트
```jsx
// src/components/common/Button.jsx
// src/components/common/Input.jsx
// src/components/common/Card.jsx
// src/components/common/Modal.jsx
// src/components/common/Table.jsx
// src/components/common/Loading.jsx
```

#### 3.3 API 서비스 레이어
```javascript
// src/services/api.js
import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
});

export default apiClient;
```

---

### Phase 4: 페이지별 마이그레이션 (5-10일)

#### 4.1 메인 페이지 (index.html → HomePage.jsx)
**작업 내용:**
- 메뉴 링크를 React Router Link로 변환
- 스타일을 CSS Modules 또는 Styled Components로 분리

**예상 시간:** 0.5일

#### 4.2 번호표 시스템 - 일반 사용자 (room.html → RoomPage.jsx)
**작업 내용:**
- Socket.IO 연결 로직을 커스텀 훅으로 분리
- 번호표 발급/확인 로직 구현
- 알림 수신 기능 구현
- localStorage를 통한 userKey 관리

**핵심 코드:**
```jsx
// src/hooks/useSocket.js
import { useEffect, useState } from 'react';
import io from 'socket.io-client';

export const useSocket = (url) => {
  const [socket, setSocket] = useState(null);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    const newSocket = io(url, {
      path: '/socket.io',
      transports: ['websocket'],
    });

    newSocket.on('connect', () => setConnected(true));
    newSocket.on('disconnect', () => setConnected(false));

    setSocket(newSocket);

    return () => newSocket.close();
  }, [url]);

  return { socket, connected };
};
```

**예상 시간:** 2-3일

#### 4.3 방 관리 - 관리자 (roomAdmin.html → AdminPage.jsx)
**작업 내용:**
- 방 목록 테이블 컴포넌트화
- 통계 카드 컴포넌트 분리
- 알림 전송 기능 구현
- 방 초기화 기능 구현

**예상 시간:** 2-3일

#### 4.4 기타 페이지들
- Excel 관리 (excel.html → ExcelPage.jsx) - 1일
- 회원 관리 (member.html → MemberPage.jsx) - 1일
- 일정 관리 (schedule.html → SchedulePage.jsx) - 1일
- 지도 페이지 (map.html → MapPage.jsx) - 1일

---

### Phase 5: Socket.IO 통합 (2-3일)

#### 5.1 Socket Context 생성
```jsx
// src/contexts/SocketContext.jsx
import React, { createContext, useContext, useEffect, useState } from 'react';
import io from 'socket.io-client';

const SocketContext = createContext(null);

export const SocketProvider = ({ children }) => {
  const [socket, setSocket] = useState(null);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    const SOCKET_URL = import.meta.env.VITE_SOCKET_URL || 'http://localhost:9092';
    const newSocket = io(SOCKET_URL, {
      path: '/socket.io',
      transports: ['websocket'],
    });

    newSocket.on('connect', () => {
      console.log('Socket connected:', newSocket.id);
      setConnected(true);
    });

    newSocket.on('disconnect', () => {
      console.log('Socket disconnected');
      setConnected(false);
    });

    setSocket(newSocket);

    return () => newSocket.close();
  }, []);

  return (
    <SocketContext.Provider value={{ socket, connected }}>
      {children}
    </SocketContext.Provider>
  );
};

export const useSocketContext = () => {
  const context = useContext(SocketContext);
  if (!context) {
    throw new Error('useSocketContext must be used within SocketProvider');
  }
  return context;
};
```

#### 5.2 Socket 이벤트 훅
```jsx
// src/hooks/useSocketEvent.js
import { useEffect } from 'react';
import { useSocketContext } from '../contexts/SocketContext';

export const useSocketEvent = (eventName, handler) => {
  const { socket } = useSocketContext();

  useEffect(() => {
    if (!socket) return;

    socket.on(eventName, handler);

    return () => {
      socket.off(eventName, handler);
    };
  }, [socket, eventName, handler]);
};
```

---

### Phase 6: 상태 관리 구현 (2-3일)

#### 6.1 Zustand Store 예시
```javascript
// src/stores/userStore.js
import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export const useUserStore = create(
  persist(
    (set) => ({
      userKey: null,
      currentTicket: null,
      setUserKey: (key) => set({ userKey: key }),
      setCurrentTicket: (ticket) => set({ currentTicket: ticket }),
      clearUser: () => set({ userKey: null, currentTicket: null }),
    }),
    {
      name: 'user-storage',
    }
  )
);
```

```javascript
// src/stores/roomStore.js
import { create } from 'zustand';

export const useRoomStore = create((set) => ({
  rooms: [],
  currentRoom: null,
  setRooms: (rooms) => set({ rooms }),
  setCurrentRoom: (room) => set({ currentRoom: room }),
  updateRoomState: (roomUid, state) => set((prev) => ({
    rooms: prev.rooms.map(r =>
      r.roomUid === roomUid ? { ...r, ...state } : r
    ),
  })),
}));
```

---

### Phase 7: 라우팅 구현 (1일)

```jsx
// src/App.jsx
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import MainLayout from './components/Layout/MainLayout';
import HomePage from './pages/HomePage';
import RoomPage from './pages/RoomPage';
import AdminPage from './pages/AdminPage';
import ExcelPage from './pages/ExcelPage';
import MemberPage from './pages/MemberPage';
import SchedulePage from './pages/SchedulePage';
import MapPage from './pages/MapPage';
import { SocketProvider } from './contexts/SocketContext';

function App() {
  return (
    <BrowserRouter>
      <SocketProvider>
        <Routes>
          <Route path="/" element={<MainLayout />}>
            <Route index element={<HomePage />} />
            <Route path="rooms/:roomUid?" element={<RoomPage />} />
            <Route path="rooms/admin" element={<AdminPage />} />
            <Route path="excel" element={<ExcelPage />} />
            <Route path="member" element={<MemberPage />} />
            <Route path="schedule" element={<SchedulePage />} />
            <Route path="map" element={<MapPage />} />
          </Route>
        </Routes>
      </SocketProvider>
    </BrowserRouter>
  );
}

export default App;
```

---

## 컴포넌트 구조

### RoomPage 컴포넌트 예시
```
RoomPage/
├── index.jsx                    # 메인 컴포넌트
├── components/
│   ├── RoomList.jsx            # 방 목록
│   ├── RoomCard.jsx            # 방 카드
│   ├── TicketDisplay.jsx       # 내 번호표 표시
│   ├── NotificationAlert.jsx   # 알림 표시
│   └── IssuanceTable.jsx       # 발급 목록 테이블
├── hooks/
│   ├── useRoomSocket.js        # 방 소켓 로직
│   └── useTicket.js            # 번호표 로직
└── RoomPage.module.css         # 스타일
```

### AdminPage 컴포넌트 예시
```
AdminPage/
├── index.jsx
├── components/
│   ├── StatCard.jsx            # 통계 카드
│   ├── RoomTable.jsx           # 방 관리 테이블
│   ├── NotificationModal.jsx   # 알림 전송 모달
│   └── CreateRoomModal.jsx     # 방 생성 모달
└── AdminPage.module.css
```

---

## API 엔드포인트 정리

### 방 관리 API
```javascript
// src/services/roomService.js
import apiClient from './api';

export const roomService = {
  // 방 목록 조회
  getRooms: () => apiClient.get('/api/rooms'),

  // 방 상세 정보 조회 (관리자)
  getRoomDetails: () => apiClient.get('/api/rooms/details'),

  // 방 생성
  createRoom: (title) => apiClient.post('/api/rooms', { title }),

  // 방 초기화
  resetRoom: (roomUid) => apiClient.post(`/api/rooms/${roomUid}/reset`),

  // 발급 목록 조회
  getIssuances: (roomUid) => apiClient.get(`/api/rooms/${roomUid}/issuances`),
};
```

### Socket.IO API
```javascript
// src/services/socketService.js
export const socketService = {
  // 방 입장
  joinRoom: (socket, roomUid, userKey) => {
    return new Promise((resolve, reject) => {
      socket.emit('join', { roomUid, userKey }, (ack) => {
        if (ack.error) reject(ack);
        else resolve(ack);
      });
    });
  },

  // 번호표 발급/확인
  issueTicket: (socket, roomUid, userKey) => {
    return new Promise((resolve, reject) => {
      socket.emit('issue', { roomUid, userKey }, (ack) => {
        if (ack.error) reject(ack);
        else resolve(ack);
      });
    });
  },

  // 알림 전송
  sendNotification: (socket, roomUid, number, message) => {
    return new Promise((resolve, reject) => {
      socket.emit('notify', { roomUid, number, message }, (ack) => {
        if (ack.error) reject(ack);
        else resolve(ack);
      });
    });
  },
};
```

---

## 라우팅 구조

| 경로 | 컴포넌트 | 설명 |
|------|---------|------|
| `/` | HomePage | 메인 페이지 (메뉴) |
| `/rooms` | RoomPage | 방 목록 |
| `/rooms/:roomUid` | RoomPage | 특정 방 입장 |
| `/rooms/admin` | AdminPage | 관리자 페이지 |
| `/excel` | ExcelPage | Excel 관리 |
| `/member` | MemberPage | 회원 관리 |
| `/schedule` | SchedulePage | 일정 관리 |
| `/map` | MapPage | 지도 페이지 |

---

## 상태 관리

### 전역 상태 (Zustand)
- **userStore**: 사용자 정보 (userKey, 현재 티켓)
- **roomStore**: 방 목록, 현재 방 정보
- **notificationStore**: 알림 목록

### 서버 상태 (React Query - 선택사항)
- 방 목록 캐싱
- 자동 리프레시
- Optimistic Updates

```javascript
// src/hooks/useRooms.js (React Query 사용 시)
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { roomService } from '../services/roomService';

export const useRooms = () => {
  return useQuery({
    queryKey: ['rooms'],
    queryFn: roomService.getRooms,
    staleTime: 30000, // 30초
  });
};

export const useCreateRoom = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: roomService.createRoom,
    onSuccess: () => {
      queryClient.invalidateQueries(['rooms']);
    },
  });
};
```

---

## Socket.IO 통합

### 연결 관리
```jsx
// RoomPage.jsx에서 사용 예시
import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useSocketContext } from '../contexts/SocketContext';
import { useSocketEvent } from '../hooks/useSocketEvent';
import { socketService } from '../services/socketService';

function RoomPage() {
  const { roomUid } = useParams();
  const { socket, connected } = useSocketContext();
  const [roomState, setRoomState] = useState(null);
  const [notification, setNotification] = useState(null);

  // 방 입장
  useEffect(() => {
    if (!socket || !connected || !roomUid) return;

    const userKey = localStorage.getItem('userKey') || generateUserKey();

    socketService.joinRoom(socket, roomUid, userKey)
      .then((ack) => {
        console.log('Joined room:', ack);
      })
      .catch((err) => {
        console.error('Failed to join room:', err);
      });
  }, [socket, connected, roomUid]);

  // 상태 업데이트 수신
  useSocketEvent('state', (data) => {
    setRoomState(data);
  });

  // 알림 수신
  useSocketEvent('notification', (data) => {
    setNotification(data);
  });

  // ... 나머지 로직
}
```

---

## 빌드 및 배포

### 개발 환경
```bash
# 백엔드 실행 (Spring Boot)
./gradlew bootRun

# 프론트엔드 실행 (React)
cd frontend
npm run dev
```

### 프로덕션 빌드
```bash
# React 빌드
cd frontend
npm run build

# 빌드된 파일을 Spring Boot의 static 폴더로 복사
cp -r dist/* ../src/main/resources/static/

# Spring Boot 빌드 및 실행
cd ..
./gradlew build
java -jar build/libs/attempt-0.0.1-SNAPSHOT.jar
```

### Gradle 자동화 설정
```gradle
// build.gradle
task buildFrontend(type: Exec) {
    workingDir 'frontend'
    commandLine 'npm', 'run', 'build'
}

task copyFrontend(type: Copy, dependsOn: buildFrontend) {
    from 'frontend/dist'
    into 'src/main/resources/static'
}

build.dependsOn copyFrontend
```

---

## 주의사항 및 고려사항

### 1. CORS 문제
- 개발 환경에서 백엔드(8080), 프론트엔드(3000)가 다른 포트
- CORS 설정 필수
- Socket.IO도 CORS 설정 필요

### 2. 환경 변수 관리
```env
# frontend/.env.development
VITE_API_URL=http://localhost:8080
VITE_SOCKET_URL=http://localhost:9092

# frontend/.env.production
VITE_API_URL=https://your-domain.com
VITE_SOCKET_URL=https://your-domain.com
```

### 3. 인증/인가
- 현재 프로젝트에는 인증이 없음
- 관리자 페이지 접근 제어 필요 시 JWT 또는 Session 기반 인증 추가 고려

### 4. SEO 고려사항
- React는 CSR(Client-Side Rendering)
- SEO가 중요한 페이지는 SSR(Next.js) 또는 SSG 고려
- 현재 프로젝트는 내부 도구이므로 SEO 우선순위 낮음

### 5. 브라우저 호환성
- 최신 브라우저 타겟
- IE 지원 불필요 (Vite는 IE 미지원)

### 6. 성능 최적화
- Code Splitting (React.lazy)
- 이미지 최적화
- Bundle 크기 모니터링

### 7. 테스트
- 단위 테스트: Vitest 또는 Jest
- 통합 테스트: React Testing Library
- E2E 테스트: Playwright 또는 Cypress

---

## 마이그레이션 체크리스트

### Phase 1: 준비
- [ ] React 프로젝트 초기화
- [ ] 디렉토리 구조 생성
- [ ] 필수 패키지 설치
- [ ] 개발 환경 설정

### Phase 2: 백엔드
- [ ] CORS 설정
- [ ] API 응답 형식 통일
- [ ] Thymeleaf 의존성 제거
- [ ] 페이지 컨트롤러 정리

### Phase 3: 공통 컴포넌트
- [ ] 레이아웃 컴포넌트
- [ ] 공통 UI 컴포넌트
- [ ] API 서비스 레이어
- [ ] Socket Context/Hooks

### Phase 4: 페이지 마이그레이션
- [ ] HomePage (메인)
- [ ] RoomPage (번호표 - 일반)
- [ ] AdminPage (번호표 - 관리자)
- [ ] ExcelPage
- [ ] MemberPage
- [ ] SchedulePage
- [ ] MapPage

### Phase 5: 통합
- [ ] 라우팅 설정
- [ ] 상태 관리 구현
- [ ] Socket.IO 통합 테스트
- [ ] API 통합 테스트

### Phase 6: 최적화
- [ ] 성능 최적화
- [ ] 에러 처리
- [ ] 로딩 상태 처리
- [ ] 반응형 디자인

### Phase 7: 배포
- [ ] 빌드 자동화
- [ ] 프로덕션 환경 설정
- [ ] 배포 테스트

---

## 예상 일정

| Phase | 작업 내용 | 예상 기간 |
|-------|----------|----------|
| Phase 1 | 프로젝트 설정 | 1-2일 |
| Phase 2 | 백엔드 API 정비 | 2-3일 |
| Phase 3 | 공통 컴포넌트 개발 | 3-4일 |
| Phase 4 | 페이지별 마이그레이션 | 5-10일 |
| Phase 5 | Socket.IO 통합 | 2-3일 |
| Phase 6 | 상태 관리 구현 | 2-3일 |
| Phase 7 | 라우팅 구현 | 1일 |
| Phase 8 | 테스트 및 디버깅 | 3-5일 |
| Phase 9 | 최적화 및 배포 | 2-3일 |
| **총합** | | **21-36일** |

---

## 참고 자료

### 공식 문서
- [React 공식 문서](https://react.dev/)
- [React Router](https://reactrouter.com/)
- [Socket.IO Client](https://socket.io/docs/v4/client-api/)
- [Zustand](https://zustand-demo.pmnd.rs/)
- [React Query](https://tanstack.com/query/latest)
- [Vite](https://vitejs.dev/)

### 추천 라이브러리
- **UI 컴포넌트**: Material-UI, Ant Design, Chakra UI
- **폼 관리**: React Hook Form, Formik
- **날짜 처리**: date-fns, dayjs
- **아이콘**: React Icons, Lucide React
- **애니메이션**: Framer Motion

---

## 결론

타임리프에서 리액트로의 마이그레이션은 다음과 같은 이점을 제공합니다:

### 장점
1. **향상된 사용자 경험**: SPA로 인한 빠른 페이지 전환
2. **컴포넌트 재사용성**: 모듈화된 UI 컴포넌트
3. **풍부한 생태계**: 다양한 라이브러리와 도구
4. **개발 생산성**: Hot Module Replacement, DevTools
5. **타입 안정성**: TypeScript 도입 가능
6. **테스트 용이성**: 컴포넌트 단위 테스트

### 도전 과제
1. **학습 곡선**: React 생태계 학습 필요
2. **초기 설정**: 프로젝트 설정 및 빌드 파이프라인 구축
3. **SEO**: CSR의 한계 (필요시 SSR 고려)
4. **번들 크기**: 적절한 최적화 필요

전체 마이그레이션 기간은 약 **3-6주** 정도 소요될 것으로 예상되며, 단계별로 진행하여 리스크를 최소화하는 것을 권장합니다.
