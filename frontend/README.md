# 번호표 시스템 - React 프론트엔드

Spring WebSocket (STOMP)을 사용하는 React 프론트엔드입니다.

## 🚀 시작하기

```bash
cd frontend
npm run dev
```

## 🔌 백엔드 연동

```bash
./gradlew bootRun
```

- REST API: http://localhost:8080
- WebSocket: ws://localhost:8080/ws

## 🔧 기술 스택

- React 18 + Vite
- React Router v6
- Axios (REST API)
- @stomp/stompjs + sockjs-client (WebSocket)
- Zustand (상태 관리)

## 📡 WebSocket (STOMP)

**클라이언트 → 서버:**
- `/app/room/join` - 방 입장
- `/app/room/issue` - 번호표 발급
- `/app/room/notify` - 알림 전송

**서버 → 클라이언트:**
- `/topic/room/{roomUid}/state` - 상태 업데이트
- `/user/queue/ticket` - 티켓 응답

자세한 내용은 프로젝트 루트의 `WEBSOCKET_MIGRATION.md` 참조
