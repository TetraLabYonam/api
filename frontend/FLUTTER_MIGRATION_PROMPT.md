# Flutter 마이그레이션 종합 프롬프트

## 프로젝트 개요

현재 React + Vite 기반의 웹 프론트엔드 애플리케이션을 Flutter로 완전히 재구성해야 합니다. 이 애플리케이션은 번호표 시스템, 회원 관리, 위치 정보 관리, Excel 파일 업로드 등의 기능을 제공합니다.

## 원본 프로젝트 기술 스택

- **프레임워크**: React 19.1.1 + Vite 7.1.7
- **라우팅**: React Router v7.9.5
- **상태 관리**: Zustand 5.0.8
- **HTTP 클라이언트**: Axios 1.13.2
- **WebSocket**: @stomp/stompjs 7.2.1 + sockjs-client 1.6.1
- **지도**: @react-google-maps/api 2.20.7
- **빌드 도구**: Vite

## Flutter 기술 스택 매핑

| React 기술 | Flutter 대응 |
|-----------|-------------|
| React + Vite | Flutter (Dart) |
| React Router | go_router 또는 auto_route |
| Zustand | Provider, Riverpod, 또는 Bloc |
| Axios | dio 또는 http |
| @stomp/stompjs + sockjs-client | stomp_dart_client 또는 web_socket_channel |
| @react-google-maps/api | google_maps_flutter |
| CSS Modules | Flutter Widget Styling |

## 프로젝트 구조 분석

### 현재 React 프로젝트 구조
```
src/
├── components/
│   ├── common/
│   │   └── Button.jsx          # 공통 버튼 컴포넌트
│   └── Layout/
│       └── MainLayout.jsx      # 메인 레이아웃 (헤더 + 사이드바)
├── contexts/
│   └── SocketContext.jsx        # WebSocket 연결 관리
├── hooks/
│   └── useSocketEvent.js       # Socket 이벤트 훅
├── pages/
│   ├── HomePage.jsx             # 홈 페이지 (메뉴)
│   ├── RoomPage.jsx             # 번호표 시스템 페이지
│   ├── AdminPage.jsx            # 방 관리 페이지 (관리자)
│   ├── MapPage.jsx              # 지도 페이지 (URL 파라미터 기반)
│   ├── ExcelMapPage.jsx         # Excel 업로드 + 지도 표시
│   ├── MemberXlsPage.jsx        # 회원 Excel 업로드
│   ├── MemberListPage.jsx       # 회원 목록 조회
│   └── LocationListPage.jsx     # 위치 정보 조회
├── services/
│   ├── api.js                   # REST API 클라이언트
│   ├── roomService.js           # 방 관련 API 서비스
│   └── socketService.js         # WebSocket 서비스
├── stores/
│   ├── roomStore.js             # 방 상태 관리 (Zustand)
│   └── userStore.js             # 사용자 상태 관리 (Zustand)
└── utils/
    └── userKey.js               # 사용자 키 관리 (localStorage)
```

### Flutter 프로젝트 구조 제안
```
lib/
├── main.dart
├── app.dart
├── config/
│   └── app_config.dart          # 환경 변수 및 설정
├── models/
│   ├── room.dart                # 방 모델
│   ├── ticket.dart               # 번호표 모델
│   ├── member.dart               # 회원 모델
│   ├── location.dart             # 위치 모델
│   └── user.dart                 # 사용자 모델
├── services/
│   ├── api_service.dart          # REST API 서비스
│   ├── room_service.dart         # 방 관련 API
│   ├── socket_service.dart       # WebSocket 서비스
│   └── storage_service.dart      # 로컬 스토리지 (SharedPreferences)
├── providers/                    # 또는 bloc/, 또는 controllers/
│   ├── room_provider.dart        # 방 상태 관리
│   ├── user_provider.dart        # 사용자 상태 관리
│   └── socket_provider.dart      # WebSocket 상태 관리
├── screens/                      # 또는 pages/
│   ├── home/
│   │   └── home_screen.dart
│   ├── room/
│   │   ├── room_list_screen.dart
│   │   ├── room_detail_screen.dart
│   │   └── admin_room_screen.dart
│   ├── map/
│   │   ├── map_screen.dart
│   │   └── excel_map_screen.dart
│   ├── member/
│   │   ├── member_list_screen.dart
│   │   └── member_excel_screen.dart
│   └── location/
│       └── location_list_screen.dart
├── widgets/
│   ├── common/
│   │   └── custom_button.dart
│   └── layout/
│       ├── main_layout.dart
│       └── sidebar.dart
└── utils/
    ├── user_key_util.dart
    └── constants.dart
```

## 주요 기능 상세 분석

### 1. 번호표 시스템 (Room System)

#### 기능 요구사항
- 방 목록 조회 (`GET /api/rooms`)
- 방 상세 정보 조회 (`GET /api/rooms/details`)
- 방 생성 (`POST /api/rooms`)
- 방 초기화 (`POST /api/rooms/{roomUid}/reset`)
- 발급 목록 조회 (`GET /api/rooms/{roomUid}/issuances`)
- 방 입장 (WebSocket: `/app/room/join`)
- 번호표 발급/확인 (WebSocket: `/app/room/issue`)
- 알림 전송 (WebSocket: `/app/room/notify`)
- 실시간 상태 업데이트 (WebSocket: `/topic/room/{roomUid}/state`)
- 개인 티켓 수신 (WebSocket: `/user/queue/ticket`)

#### Flutter 구현 포인트
- WebSocket 연결은 앱 시작 시 자동 연결
- STOMP 프로토콜 사용 (stomp_dart_client 패키지)
- 실시간 상태 업데이트는 StreamBuilder 또는 Provider로 처리
- 방 목록은 ListView.builder로 구현
- 번호표 발급은 버튼 클릭 시 WebSocket 메시지 전송

### 2. 회원 관리 시스템

#### 기능 요구사항
- Excel 파일 업로드 (`POST /api/v1/member/member-excel`)
- 회원 정보 저장 (`POST /api/v1/member/save-members`)
- 회원 목록 조회 (`GET /api/v1/member/members`)
- 검색 기능 (이름, 부서, 전화번호)

#### Flutter 구현 포인트
- 파일 선택: file_picker 패키지 사용
- Excel 파싱: 백엔드에서 처리하므로 multipart/form-data로 업로드만 구현
- 테이블 표시: DataTable 위젯 사용
- 검색: TextField + 필터링 로직

### 3. 위치 정보 관리 시스템

#### 기능 요구사항
- Excel 파일 업로드 (`POST /api/map-excel`)
- 위치 정보 저장 (`POST /api/place/save-all`)
- 위치 목록 조회 (`GET /api/place/list`)
- Google Maps 표시
- 지도에서 위치 확인

#### Flutter 구현 포인트
- Google Maps: google_maps_flutter 패키지 사용
- 마커 표시: Marker 위젯 사용
- 파일 업로드: multipart/form-data
- 위치 목록: GridView 또는 ListView

### 4. 사용자 관리

#### 기능 요구사항
- 사용자 키 자동 생성 및 저장 (localStorage)
- 형식: `USER-{랜덤문자열}`
- 영구 저장 (SharedPreferences)

#### Flutter 구현 포인트
- SharedPreferences 패키지 사용
- UUID 또는 랜덤 문자열 생성

## API 엔드포인트 명세

### REST API

#### Base URL
- 기본값: `http://localhost:8080`
- 환경 변수: `VITE_API_URL` (Flutter에서는 `.env` 또는 `flutter_dotenv`)

#### 엔드포인트 목록

**방 관련**
- `GET /api/rooms` - 방 목록 조회
- `GET /api/rooms/details` - 방 상세 정보 조회 (관리자)
- `POST /api/rooms` - 방 생성 (Content-Type: application/x-www-form-urlencoded, body: `title={title}`)
- `POST /api/rooms/{roomUid}/reset` - 방 초기화
- `GET /api/rooms/{roomUid}/issuances` - 발급 목록 조회

**회원 관련**
- `POST /api/v1/member/member-excel` - 회원 Excel 업로드 (multipart/form-data)
- `POST /api/v1/member/save-members` - 회원 정보 저장 (JSON: `{ members: [...] }`)
- `GET /api/v1/member/members` - 회원 목록 조회

**위치 관련**
- `POST /api/map-excel` - 위치 Excel 업로드 (multipart/form-data)
- `POST /api/place/save-all` - 위치 정보 저장 (JSON: locations 배열)
- `GET /api/place/list` - 위치 목록 조회

#### 요청/응답 형식

**방 목록 조회 응답 예시**
```json
[
  {
    "roomUid": "ROOM-123",
    "title": "대기실 1",
    "currentNumber": 5,
    "createdAt": "2024-01-01T00:00:00"
  }
]
```

**방 상세 정보 응답 예시**
```json
[
  {
    "roomUid": "ROOM-123",
    "title": "대기실 1",
    "currentNumber": 5,
    "issuedCount": 10,
    "createdAt": "2024-01-01T00:00:00"
  }
]
```

**Excel 업로드 응답 예시**
```json
{
  "locations": [
    {
      "address": "서울시 강남구",
      "lat": 37.5665,
      "lng": 126.9780,
      "businessUnit": "본사"
    }
  ]
}
```

또는

```json
{
  "members": [
    {
      "username": "홍길동",
      "phoneNumber": "010-1234-5678",
      "businessUnit": "개발팀"
    }
  ]
}
```

### WebSocket (STOMP)

#### 연결 URL
- `ws://localhost:8080/ws` (SockJS 엔드포인트)

#### 메시지 형식

**클라이언트 → 서버**

1. 방 입장
   - Destination: `/app/room/join`
   - Body: `{ "roomUid": "ROOM-123", "userKey": "USER-abc123" }`

2. 번호표 발급
   - Destination: `/app/room/issue`
   - Body: `{ "roomUid": "ROOM-123", "userKey": "USER-abc123" }`

3. 알림 전송 (관리자)
   - Destination: `/app/room/notify`
   - Body: `{ "roomUid": "ROOM-123", "number": 5, "message": "알림 메시지" }`

**서버 → 클라이언트**

1. 방 상태 업데이트
   - Subscription: `/topic/room/{roomUid}/state`
   - Body: `{ "currentNumber": 5, "roomUid": "ROOM-123" }`

2. 개인 티켓 응답
   - Subscription: `/user/queue/ticket`
   - Body: `{ "number": 5, "duplicated": false }`

3. 알림 수신
   - Subscription: `/topic/room/{roomUid}/notification/{userKey}`
   - Body: `{ "number": 5, "message": "알림 메시지" }`

## 상태 관리 구조

### 현재 Zustand 구조

**roomStore.js**
```javascript
{
  rooms: [],
  currentRoom: null,
  roomState: null,
  setRooms: (rooms) => void,
  setCurrentRoom: (room) => void,
  setRoomState: (state) => void,
  updateRoomState: (roomUid, state) => void,
  clearCurrentRoom: () => void
}
```

**userStore.js** (persist 적용)
```javascript
{
  userKey: null,
  currentTicket: null,
  setUserKey: (key) => void,
  setCurrentTicket: (ticket) => void,
  clearUser: () => void
}
```

### Flutter Provider 구조 제안

**RoomProvider**
```dart
class RoomProvider extends ChangeNotifier {
  List<Room> _rooms = [];
  Room? _currentRoom;
  RoomState? _roomState;
  
  // Getters
  List<Room> get rooms => _rooms;
  Room? get currentRoom => _currentRoom;
  RoomState? get roomState => _roomState;
  
  // Methods
  void setRooms(List<Room> rooms) { ... }
  void setCurrentRoom(Room? room) { ... }
  void setRoomState(RoomState? state) { ... }
  void updateRoomState(String roomUid, RoomState state) { ... }
  void clearCurrentRoom() { ... }
}
```

**UserProvider** (SharedPreferences 연동)
```dart
class UserProvider extends ChangeNotifier {
  String? _userKey;
  Ticket? _currentTicket;
  
  // Getters
  String? get userKey => _userKey;
  Ticket? get currentTicket => _currentTicket;
  
  // Methods
  Future<void> loadUserKey() async { ... } // SharedPreferences에서 로드
  Future<void> setUserKey(String key) async { ... }
  void setCurrentTicket(Ticket? ticket) { ... }
  Future<void> clearUser() async { ... }
}
```

## UI 컴포넌트 매핑

### 공통 컴포넌트

**Button.jsx → CustomButton.dart**
- Props: `variant` (primary, secondary, danger), `onClick`, `disabled`, `children`
- Flutter: `ElevatedButton`, `OutlinedButton`, `TextButton` 조합

**MainLayout.jsx → MainLayout.dart**
- 구조: 헤더 + 사이드바 + 메인 콘텐츠
- Flutter: `Scaffold` + `AppBar` + `Drawer` 또는 커스텀 레이아웃

### 페이지별 UI 구조

**HomePage**
- 메뉴 리스트 (Link 컴포넌트들)
- Flutter: `ListView` 또는 `GridView` + `ListTile`

**RoomPage**
- 방 목록: 카드 형태의 방 목록
- 방 상세: 번호표 확인 버튼, 발급 목록 버튼, 내 번호표 표시, 알림 표시
- Flutter: `Card` 위젯, `DataTable`, `AlertDialog`

**AdminPage**
- 통계 카드 (전체 방 수, 총 발급된 번호표)
- 방 테이블 (방 코드, 제목, 현재 번호, 발급된 인원, 생성 시간, 관리 버튼)
- Flutter: `DataTable`, `Card`, `Row`/`Column`

**ExcelMapPage / MemberXlsPage**
- 파일 업로드 섹션
- 데이터 목록/테이블
- 저장 버튼
- Flutter: `file_picker` 패키지, `DataTable`, `ElevatedButton`

**LocationListPage / MemberListPage**
- 검색 입력 필드
- 데이터 테이블/그리드
- Flutter: `TextField`, `DataTable` 또는 `GridView`

## Flutter 패키지 의존성

### pubspec.yaml 제안

```yaml
name: attempt_frontend
description: Flutter frontend for attempt project
version: 1.0.0

environment:
  sdk: '>=3.0.0 <4.0.0'

dependencies:
  flutter:
    sdk: flutter
  
  # 상태 관리
  provider: ^6.1.1
  # 또는 riverpod: ^2.4.9
  # 또는 flutter_bloc: ^8.1.3
  
  # 라우팅
  go_router: ^13.0.0
  # 또는 auto_route: ^7.3.2
  
  # HTTP 클라이언트
  dio: ^5.4.0
  # 또는 http: ^1.1.0
  
  # WebSocket
  stomp_dart_client: ^1.0.0
  # 또는 web_socket_channel: ^2.4.0
  
  # 로컬 스토리지
  shared_preferences: ^2.2.2
  
  # Google Maps
  google_maps_flutter: ^2.5.0
  
  # 파일 선택
  file_picker: ^6.1.1
  
  # 환경 변수
  flutter_dotenv: ^5.1.0
  
  # 유틸리티
  uuid: ^4.2.1
  intl: ^0.19.0

dev_dependencies:
  flutter_test:
    sdk: flutter
  flutter_lints: ^3.0.0
```

## 구현 세부사항

### 1. 환경 설정

**config/app_config.dart**
```dart
class AppConfig {
  static const String defaultApiUrl = 'http://localhost:8080';
  static String get apiUrl => dotenv.env['API_URL'] ?? defaultApiUrl;
  static String get wsUrl => '${apiUrl.replaceFirst('http', 'ws')}/ws';
}
```

### 2. API 서비스 구현

**services/api_service.dart**
```dart
class ApiService {
  final Dio _dio;
  
  ApiService() : _dio = Dio(BaseOptions(
    baseUrl: AppConfig.apiUrl,
    headers: {'Content-Type': 'application/json'},
    withCredentials: true,
  ));
  
  // Interceptor 설정
  // Request/Response 로깅
  // 에러 처리
}
```

### 3. WebSocket 서비스 구현

**services/socket_service.dart**
```dart
class SocketService {
  StompClient? _client;
  bool _connected = false;
  
  Future<void> connect() async {
    _client = StompClient(
      config: StompConfig(
        url: AppConfig.wsUrl,
        onConnect: (frame) {
          _connected = true;
        },
        onDisconnect: (frame) {
          _connected = false;
        },
      ),
    );
    await _client?.activate();
  }
  
  void subscribe(String destination, Function(Map<String, dynamic>) callback) {
    _client?.subscribe(
      destination: destination,
      callback: (frame) {
        final data = jsonDecode(frame.body!);
        callback(data);
      },
    );
  }
  
  void send(String destination, Map<String, dynamic> data) {
    _client?.send(
      destination: destination,
      body: jsonEncode(data),
    );
  }
}
```

### 4. 사용자 키 관리

**utils/user_key_util.dart**
```dart
class UserKeyUtil {
  static const String _storageKey = 'userKey';
  
  static Future<String> getUserKey() async {
    final prefs = await SharedPreferences.getInstance();
    String? userKey = prefs.getString(_storageKey);
    
    if (userKey == null) {
      userKey = _generateUserKey();
      await prefs.setString(_storageKey, userKey);
    }
    
    return userKey;
  }
  
  static String _generateUserKey() {
    final random = Random();
    final randomString = random.nextInt(100000000).toRadixString(36);
    return 'USER-$randomString';
  }
}
```

### 5. 라우팅 설정

**app.dart**
```dart
final router = GoRouter(
  routes: [
    GoRoute(
      path: '/',
      builder: (context, state) => MainLayout(
        child: HomeScreen(),
      ),
    ),
    GoRoute(
      path: '/rooms',
      builder: (context, state) => MainLayout(
        child: RoomListScreen(),
      ),
    ),
    GoRoute(
      path: '/rooms/:roomUid',
      builder: (context, state) {
        final roomUid = state.pathParameters['roomUid']!;
        return MainLayout(
          child: RoomDetailScreen(roomUid: roomUid),
        );
      },
    ),
    // ... 기타 라우트
  ],
);
```

## 마이그레이션 체크리스트

### Phase 1: 프로젝트 설정
- [ ] Flutter 프로젝트 생성
- [ ] pubspec.yaml 의존성 추가
- [ ] 환경 변수 설정 (.env 파일)
- [ ] 프로젝트 구조 생성

### Phase 2: 모델 및 서비스
- [ ] 데이터 모델 클래스 생성 (Room, Ticket, Member, Location, User)
- [ ] API 서비스 구현 (Dio 기반)
- [ ] WebSocket 서비스 구현 (STOMP)
- [ ] 로컬 스토리지 서비스 구현 (SharedPreferences)

### Phase 3: 상태 관리
- [ ] Provider/Riverpod/Bloc 설정
- [ ] RoomProvider 구현
- [ ] UserProvider 구현
- [ ] SocketProvider 구현

### Phase 4: UI 컴포넌트
- [ ] 공통 위젯 (CustomButton, MainLayout)
- [ ] 홈 화면
- [ ] 방 목록 화면
- [ ] 방 상세 화면
- [ ] 관리자 화면

### Phase 5: 기능 페이지
- [ ] 회원 Excel 업로드 화면
- [ ] 회원 목록 화면
- [ ] 위치 Excel 업로드 화면
- [ ] 위치 목록 화면
- [ ] 지도 화면 (Google Maps)

### Phase 6: 통합 및 테스트
- [ ] WebSocket 연결 테스트
- [ ] API 통신 테스트
- [ ] 파일 업로드 테스트
- [ ] 상태 관리 테스트
- [ ] UI/UX 검증

## 주의사항

1. **WebSocket 연결**: Flutter에서는 SockJS를 직접 사용할 수 없으므로, STOMP over WebSocket을 사용하거나 백엔드에서 일반 WebSocket도 지원하는지 확인 필요

2. **CORS**: 웹에서는 CORS 문제가 있을 수 있으므로, Flutter 웹 빌드 시 백엔드 CORS 설정 확인

3. **파일 업로드**: `file_picker` 패키지는 플랫폼별로 다른 동작을 할 수 있으므로 테스트 필요

4. **Google Maps**: Android/iOS에서 각각 API 키 설정 필요 (AndroidManifest.xml, Info.plist)

5. **상태 관리**: Provider, Riverpod, Bloc 중 하나를 선택하여 일관성 유지

6. **에러 처리**: 네트워크 에러, WebSocket 연결 실패 등에 대한 적절한 에러 핸들링 및 사용자 피드백 필요

## 참고 자료

- Flutter 공식 문서: https://flutter.dev/docs
- Dio 문서: https://pub.dev/packages/dio
- STOMP Dart Client: https://pub.dev/packages/stomp_dart_client
- Google Maps Flutter: https://pub.dev/packages/google_maps_flutter
- Provider: https://pub.dev/packages/provider
- go_router: https://pub.dev/packages/go_router

