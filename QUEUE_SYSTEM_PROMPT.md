# 번호표 시스템 플러터 앱 개발 프롬프트

## 프로젝트 개요
현재 Spring Boot 프로젝트에 있는 번호표 기능을 Flutter 모바일 앱으로 이식하려고 합니다. 노인 일자리 사업 참여 어르신들을 위한 대기 관리 시스템입니다.

## 기술 스택
- **백엔드**: Spring Boot (기존 프로젝트 활용)
  - WebSocket (STOMP)
  - Redis (대기열 관리)
  - 위치: `/src/main/java/com/example/attempt/`
- **프론트엔드**: Flutter
  - 새로운 Flutter 프로젝트 생성 필요
  - WebSocket 통신
  - 로컬 알림/진동 기능

## 사용자 역할 구분

### 1. 관리자 (Admin)
**인증**:
- 간단한 로그인 기능 필요
- ID/PW 방식 (예: admin/1234)
- 세션 유지

**기능**:
- 번호표 방(Queue Room) 생성
  - 방 이름 설정 (예: "1번 창구", "상담실 A")
  - 방 활성화/비활성화
- 번호 호출 기능
  - 특정 번호 선택하여 호출
  - 다음 번호 자동 호출
  - 현재 호출 번호 표시
- 대기 현황 모니터링
  - 전체 대기 인원
  - 대기 중인 번호 목록

### 2. 사용자 (User)
**기능**:
- 번호표 방 목록 조회
  - 활성화된 방만 표시
  - 각 방의 현재 대기 인원 표시
- 번호표 발급
  - 원하는 방 선택
  - 번호 발급 받기
  - QR 코드 또는 번호 표시
- 대기 현황 확인
  - 현재 호출 중인 번호
  - 내 대기 번호
  - 남은 대기 인원
  - 실시간 업데이트 (WebSocket)
- 알림 기능
  - 내 번호가 호출되면 **진동 및 알림**
  - 내 차례가 3명 전일 때 사전 알림

## UI/UX 요구사항

### 전체 UI 원칙
- **대형 UI**: 어르신 사용자를 고려한 큰 버튼과 텍스트
  - 최소 폰트 크기: 20sp
  - 버튼 최소 높이: 80dp
  - 충분한 터치 영역 (최소 48dp)
- **명확한 색상**: 고대비 색상 사용
  - 현재 호출 번호: 빨간색/강조색
  - 내 번호: 파란색/하이라이트
  - 대기 번호: 회색
- **간단한 네비게이션**: 최소한의 화면 전환
- **한글 중심**: 모든 텍스트 한글로 명확하게

### 관리자 화면
```
📱 관리자 화면 구성
1. 로그인 화면
   - ID 입력 (큰 텍스트 필드)
   - PW 입력 (큰 텍스트 필드)
   - [로그인] 버튼 (대형)

2. 방 관리 화면
   - [새 방 만들기] 버튼
   - 방 목록 (카드 형태, 각 방마다)
     - 방 이름
     - 현재 호출 번호
     - 대기 인원
     - [관리하기] 버튼

3. 번호 호출 화면
   - 현재 호출 번호 (초대형 표시)
   - 대기 번호 목록
   - [다음 번호 호출] 버튼 (대형)
   - [특정 번호 호출] 버튼
   - [초기화] 버튼
```

### 사용자 화면
```
📱 사용자 화면 구성
1. 방 선택 화면
   - 활성화된 방 목록 (카드 형태)
   - 각 카드에 표시:
     - 방 이름 (큰 텍스트)
     - 현재 대기 인원
     - [번호받기] 버튼 (대형)

2. 내 번호표 화면
   - 내 번호 (초대형 표시, 화면 중앙)
   - 현재 호출 번호 (대형)
   - 남은 대기 인원
   - 진행 상황 바
   - [번호표 취소] 버튼
```

## 백엔드 API 명세 (Spring Boot)

### 필요한 엔드포인트

#### REST API
```
POST   /api/admin/login              - 관리자 로그인
POST   /api/queue/room                - 방 생성 (관리자)
GET    /api/queue/rooms               - 방 목록 조회
GET    /api/queue/room/{id}           - 방 상세 조회
POST   /api/queue/room/{id}/ticket    - 번호표 발급
DELETE /api/queue/ticket/{id}         - 번호표 취소
POST   /api/queue/room/{id}/call      - 번호 호출 (관리자)
GET    /api/queue/room/{id}/status    - 방 현황 조회
```

#### WebSocket 엔드포인트
```
SUBSCRIBE /topic/queue/{roomId}       - 방 상태 구독
SEND      /app/queue/call              - 번호 호출 (관리자)
SEND      /app/queue/update            - 상태 업데이트
```

### WebSocket 메시지 포맷
```json
{
  "type": "CALL" | "UPDATE" | "CANCEL",
  "roomId": "room-uuid",
  "currentNumber": 15,
  "calledNumber": 15,
  "waitingCount": 8,
  "timestamp": "2025-11-16T10:30:00"
}
```

## 데이터베이스 스키마

### Queue Room 테이블
```sql
CREATE TABLE queue_room (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_name VARCHAR(100) NOT NULL,
    is_active BOOLEAN DEFAULT true,
    current_number INT DEFAULT 0,
    last_issued_number INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### Queue Ticket 테이블
```sql
CREATE TABLE queue_ticket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    ticket_number INT NOT NULL,
    user_device_id VARCHAR(255),  -- 디바이스 식별용
    status VARCHAR(20) DEFAULT 'WAITING',  -- WAITING, CALLED, COMPLETED, CANCELLED
    issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    called_at TIMESTAMP NULL,
    FOREIGN KEY (room_id) REFERENCES queue_room(id)
);
```

### Admin 테이블
```sql
CREATE TABLE admin (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,  -- BCrypt 암호화
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Flutter 프로젝트 구조
```
flutter_queue_app/
├── lib/
│   ├── main.dart
│   ├── models/
│   │   ├── queue_room.dart
│   │   ├── queue_ticket.dart
│   │   └── admin.dart
│   ├── services/
│   │   ├── api_service.dart
│   │   ├── websocket_service.dart
│   │   └── notification_service.dart
│   ├── providers/
│   │   ├── auth_provider.dart
│   │   ├── queue_provider.dart
│   │   └── ticket_provider.dart
│   ├── screens/
│   │   ├── admin/
│   │   │   ├── admin_login_screen.dart
│   │   │   ├── room_management_screen.dart
│   │   │   └── call_screen.dart
│   │   └── user/
│   │       ├── room_list_screen.dart
│   │       └── my_ticket_screen.dart
│   └── widgets/
│       ├── large_button.dart
│       ├── room_card.dart
│       └── number_display.dart
└── pubspec.yaml
```

## 필요한 Flutter 패키지
```yaml
dependencies:
  flutter:
    sdk: flutter

  # 상태 관리
  provider: ^6.1.1

  # HTTP 통신
  http: ^1.1.0
  dio: ^5.4.0

  # WebSocket
  web_socket_channel: ^2.4.0
  stomp_dart_client: ^1.0.0

  # 로컬 알림 및 진동
  flutter_local_notifications: ^16.3.0
  vibration: ^1.8.3

  # 로컬 저장소
  shared_preferences: ^2.2.2

  # UI
  google_fonts: ^6.1.0
```

## 구현 순서

### Phase 1: 백엔드 구현
1. Queue Room, Ticket, Admin 엔티티 및 Repository 생성
2. QueueService 구현
3. QueueController (REST API) 구현
4. WebSocket 설정 및 핸들러 구현
5. 간단한 인증 로직 구현

### Phase 2: Flutter 기본 구조
1. Flutter 프로젝트 생성 및 폴더 구조 설정
2. 모델 클래스 작성
3. API Service 구현
4. WebSocket Service 구현
5. Provider 설정

### Phase 3: 관리자 앱 구현
1. 로그인 화면
2. 방 관리 화면
3. 번호 호출 화면
4. WebSocket 연동

### Phase 4: 사용자 앱 구현
1. 방 목록 화면
2. 번호표 발급
3. 내 번호표 화면
4. 실시간 업데이트 구독
5. 알림 및 진동 구현

### Phase 5: 테스트 및 최적화
1. 동시 사용자 테스트
2. WebSocket 재연결 로직
3. 에러 처리
4. UI/UX 개선

## 특별 요구사항

### 1. 진동 알림
- 내 번호가 호출되면 **3초간 진동**
- 3번 앞 번호일 때 **짧은 진동 1회**
- 설정에서 진동 on/off 가능

### 2. 오프라인 대응
- 인터넷 연결 끊김 시 재연결 시도
- 연결 상태 표시
- 로컬 데이터 캐싱

### 3. 접근성
- 큰 폰트 지원 (최소 20sp)
- 고대비 색상
- 음성 안내 (선택사항)

## 예상 사용 시나리오

### 관리자
1. 관리자가 앱 실행 → 로그인
2. "1번 창구" 방 생성
3. 번호 호출 화면 진입
4. 현재 대기 10명 확인
5. [다음 번호 호출] 버튼 클릭 → 1번 호출
6. 1번 사용자 폰에서 진동 울림
7. 업무 완료 후 [다음 번호 호출] → 2번 호출

### 사용자
1. 사용자(어르신)가 앱 실행
2. "1번 창구" 선택
3. [번호받기] 버튼 클릭 → 10번 발급
4. 화면에 "내 번호: 10번, 현재 호출: 1번, 9명 대기중" 표시
5. 대기 중...
6. 7번 호출 시 짧은 진동 (3명 전 알림)
7. 10번 호출 시 **3초간 진동** + 화면 강조
8. 어르신이 창구로 이동

## 주의사항
- WebSocket 연결이 끊어질 경우 자동 재연결 필요
- 중복 번호 발급 방지
- 어르신을 위한 간단하고 직관적인 UI
- 에러 메시지는 한글로 명확하게
- 큰 텍스트와 버튼으로 터치 오류 최소화

---

## 요청사항
위 명세를 기반으로 다음을 구현해주세요:

1. **백엔드 (Spring Boot)**
   - Queue 관련 엔티티, 리포지토리, 서비스, 컨트롤러 구현
   - WebSocket 설정 및 메시지 핸들러 구현
   - 간단한 관리자 인증 구현

2. **프론트엔드 (Flutter)**
   - 관리자용 화면 (로그인, 방 관리, 호출)
   - 사용자용 화면 (방 목록, 번호표)
   - WebSocket 실시간 통신
   - 진동 알림 기능
   - 어르신을 위한 대형 UI

3. **문서화**
   - API 문서
   - 사용자 매뉴얼
   - 설치 가이드
