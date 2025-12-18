# Flutter 앱 연동 가이드 - 번호표 시스템

본 문서는 Flutter 앱에서 번호표 시스템의 REST API 및 WebSocket을 연동하는 방법을 설명합니다.

## 목차
1. [시스템 구조](#시스템-구조)
2. [필수 패키지 설치](#필수-패키지-설치)
3. [REST API 연동](#rest-api-연동)
4. [WebSocket 연동](#websocket-연동)
5. [전체 통합 예제](#전체-통합-예제)
6. [문제 해결](#문제-해결)

---

## 시스템 구조

### 엔드포인트 구성

#### REST API
```
Base URL: http://your-server:8080
또는
Base URL: https://your-domain.com
```

#### WebSocket
```
WebSocket URL: ws://your-server:8080/ws
또는
WebSocket URL: wss://your-domain.com/ws

SockJS URL: http://your-server:8080/ws
```

### 통신 흐름

```
Flutter App
    │
    ├─── REST API ────────────────────────────────┐
    │    - 방 목록 조회                            │
    │    - 번호표 발급 (HTTP)                      │
    │    - 방 현황 조회                            │
    │                                             ▼
    └─── WebSocket (STOMP) ──────────────────> Spring Boot Server
         - 실시간 방 상태 구독                      │
         - 실시간 번호표 발급                       │
         - 번호 호출 알림                          │
                                                   ▼
                                              MariaDB + Redis
```

---

## 필수 패키지 설치

### pubspec.yaml

```yaml
dependencies:
  flutter:
    sdk: flutter

  # HTTP 통신
  http: ^1.1.0

  # WebSocket (STOMP)
  stomp_dart_client: ^1.0.0

  # JSON 직렬화
  json_annotation: ^4.8.1

  # 상태 관리 (선택)
  provider: ^6.1.1
  # 또는 riverpod, bloc 등 원하는 상태관리 라이브러리

  # 디바이스 ID 생성
  uuid: ^4.1.0
  device_info_plus: ^9.1.0

dev_dependencies:
  build_runner: ^2.4.6
  json_serializable: ^6.7.1
```

설치:
```bash
flutter pub get
```

---

## REST API 연동

### 1. API 서비스 클래스

```dart
// lib/services/queue_api_service.dart

import 'dart:convert';
import 'package:http/http.dart' as http;

class QueueApiService {
  // ⚠️ 실제 서버 주소로 변경하세요
  static const String baseUrl = 'http://localhost:8080';

  final http.Client _client;

  QueueApiService({http.Client? client})
    : _client = client ?? http.Client();

  /// 1. 활성화된 방 목록 조회
  /// GET /api/queue/rooms
  Future<List<QueueRoom>> getActiveRooms() async {
    try {
      final response = await _client.get(
        Uri.parse('$baseUrl/api/queue/rooms'),
        headers: {'Content-Type': 'application/json'},
      );

      if (response.statusCode == 200) {
        final List<dynamic> data = json.decode(utf8.decode(response.bodyBytes));
        return data.map((json) => QueueRoom.fromJson(json)).toList();
      } else {
        throw Exception('방 목록 조회 실패: ${response.statusCode}');
      }
    } catch (e) {
      throw Exception('네트워크 오류: $e');
    }
  }

  /// 2. 특정 방 정보 조회
  /// GET /api/queue/room/{roomId}
  Future<QueueRoom> getRoomInfo(String roomId) async {
    final response = await _client.get(
      Uri.parse('$baseUrl/api/queue/room/$roomId'),
      headers: {'Content-Type': 'application/json'},
    );

    if (response.statusCode == 200) {
      return QueueRoom.fromJson(json.decode(utf8.decode(response.bodyBytes)));
    } else {
      throw Exception('방 정보 조회 실패: ${response.statusCode}');
    }
  }

  /// 3. 번호표 발급 (REST API 방식)
  /// POST /api/queue/room/{roomId}/ticket
  Future<TicketIssueResponse> issueTicket(String roomId, String deviceId) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/api/queue/room/$roomId/ticket'),
      headers: {'Content-Type': 'application/json'},
      body: json.encode({'userDeviceId': deviceId}),
    );

    if (response.statusCode == 200 || response.statusCode == 201) {
      return TicketIssueResponse.fromJson(
        json.decode(utf8.decode(response.bodyBytes))
      );
    } else {
      throw Exception('번호표 발급 실패: ${response.statusCode}');
    }
  }

  /// 4. 방 현황 조회
  /// GET /api/queue/room/{roomId}/status
  Future<RoomStatus> getRoomStatus(String roomId) async {
    final response = await _client.get(
      Uri.parse('$baseUrl/api/queue/room/$roomId/status'),
      headers: {'Content-Type': 'application/json'},
    );

    if (response.statusCode == 200) {
      return RoomStatus.fromJson(json.decode(utf8.decode(response.bodyBytes)));
    } else {
      throw Exception('방 현황 조회 실패: ${response.statusCode}');
    }
  }

  /// 5. 방의 모든 번호표 조회
  /// GET /api/queue/room/{roomId}/tickets
  Future<List<QueueTicket>> getRoomTickets(String roomId) async {
    final response = await _client.get(
      Uri.parse('$baseUrl/api/queue/room/$roomId/tickets'),
      headers: {'Content-Type': 'application/json'},
    );

    if (response.statusCode == 200) {
      final List<dynamic> data = json.decode(utf8.decode(response.bodyBytes));
      return data.map((json) => QueueTicket.fromJson(json)).toList();
    } else {
      throw Exception('번호표 목록 조회 실패: ${response.statusCode}');
    }
  }

  /// 6. 다음 번호 호출 (관리자용)
  /// POST /api/queue/room/{roomId}/call
  Future<int> callNextNumber(String roomId) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/api/queue/room/$roomId/call'),
      headers: {'Content-Type': 'application/json'},
    );

    if (response.statusCode == 200) {
      return json.decode(response.body) as int;
    } else {
      throw Exception('번호 호출 실패: ${response.statusCode}');
    }
  }

  void dispose() {
    _client.close();
  }
}
```

### 2. 데이터 모델 (DTO)

```dart
// lib/models/queue_models.dart

class QueueRoom {
  final int? id;
  final String roomUid;
  final String roomName;
  final bool isActive;
  final int currentNumber;
  final int lastIssuedNumber;
  final int waitingCount;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  QueueRoom({
    this.id,
    required this.roomUid,
    required this.roomName,
    required this.isActive,
    required this.currentNumber,
    required this.lastIssuedNumber,
    required this.waitingCount,
    this.createdAt,
    this.updatedAt,
  });

  factory QueueRoom.fromJson(Map<String, dynamic> json) {
    return QueueRoom(
      id: json['id'],
      roomUid: json['roomUid'] ?? json['roomId'],
      roomName: json['roomName'] ?? json['title'] ?? '',
      isActive: json['isActive'] ?? true,
      currentNumber: json['currentNumber'] ?? 0,
      lastIssuedNumber: json['lastIssuedNumber'] ?? 0,
      waitingCount: json['waitingCount'] ?? 0,
      createdAt: json['createdAt'] != null
        ? DateTime.parse(json['createdAt'])
        : null,
      updatedAt: json['updatedAt'] != null
        ? DateTime.parse(json['updatedAt'])
        : null,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'roomUid': roomUid,
      'roomName': roomName,
      'isActive': isActive,
      'currentNumber': currentNumber,
      'lastIssuedNumber': lastIssuedNumber,
      'waitingCount': waitingCount,
      'createdAt': createdAt?.toIso8601String(),
      'updatedAt': updatedAt?.toIso8601String(),
    };
  }
}

class TicketIssueResponse {
  final int number;
  final bool duplicated;
  final int lastNumber;
  final int count;

  TicketIssueResponse({
    required this.number,
    required this.duplicated,
    required this.lastNumber,
    required this.count,
  });

  factory TicketIssueResponse.fromJson(Map<String, dynamic> json) {
    return TicketIssueResponse(
      number: json['number'] ?? 0,
      duplicated: json['duplicated'] ?? false,
      lastNumber: json['lastNumber'] ?? 0,
      count: json['count'] ?? 0,
    );
  }
}

class RoomStatus {
  final String roomId;
  final String roomName;
  final int currentNumber;
  final int lastIssuedNumber;
  final int waitingCount;
  final bool isActive;

  RoomStatus({
    required this.roomId,
    required this.roomName,
    required this.currentNumber,
    required this.lastIssuedNumber,
    required this.waitingCount,
    required this.isActive,
  });

  factory RoomStatus.fromJson(Map<String, dynamic> json) {
    return RoomStatus(
      roomId: json['roomId'] ?? '',
      roomName: json['roomName'] ?? '',
      currentNumber: json['currentNumber'] ?? 0,
      lastIssuedNumber: json['lastIssuedNumber'] ?? 0,
      waitingCount: json['waitingCount'] ?? 0,
      isActive: json['isActive'] ?? true,
    );
  }
}

class QueueTicket {
  final int id;
  final int number;
  final String userDeviceId;
  final String status;
  final DateTime issuedAt;
  final DateTime? calledAt;

  QueueTicket({
    required this.id,
    required this.number,
    required this.userDeviceId,
    required this.status,
    required this.issuedAt,
    this.calledAt,
  });

  factory QueueTicket.fromJson(Map<String, dynamic> json) {
    return QueueTicket(
      id: json['id'] ?? 0,
      number: json['number'] ?? 0,
      userDeviceId: json['userDeviceId'] ?? '',
      status: json['status'] ?? 'WAITING',
      issuedAt: DateTime.parse(json['issuedAt']),
      calledAt: json['calledAt'] != null
        ? DateTime.parse(json['calledAt'])
        : null,
    );
  }
}
```

---

## WebSocket 연동

### 1. WebSocket 서비스 클래스

```dart
// lib/services/queue_websocket_service.dart

import 'dart:convert';
import 'package:stomp_dart_client/stomp_dart_client.dart';
import 'package:flutter/foundation.dart';

class QueueWebSocketService {
  // ⚠️ 실제 서버 주소로 변경하세요
  static const String websocketUrl = 'ws://localhost:8080/ws';

  StompClient? _stompClient;
  bool _isConnected = false;

  // 콜백 함수들
  Function(RoomStatus)? onRoomStatusUpdate;
  Function(TicketIssueResponse)? onTicketIssued;
  Function(Map<String, dynamic>)? onNotification;
  Function()? onConnected;
  Function(String)? onError;

  bool get isConnected => _isConnected;

  /// WebSocket 연결 초기화
  void connect() {
    if (_stompClient != null && _isConnected) {
      debugPrint('[WebSocket] Already connected');
      return;
    }

    _stompClient = StompClient(
      config: StompConfig(
        url: websocketUrl,

        // WebSocket 연결 성공 시
        onConnect: (StompFrame frame) {
          _isConnected = true;
          debugPrint('[WebSocket] Connected to $websocketUrl');
          onConnected?.call();
        },

        // 연결 끊김 시
        onDisconnect: (StompFrame frame) {
          _isConnected = false;
          debugPrint('[WebSocket] Disconnected');
        },

        // 에러 발생 시
        onStompError: (StompFrame frame) {
          debugPrint('[WebSocket] Error: ${frame.body}');
          onError?.call(frame.body ?? 'Unknown error');
        },

        // 웹 소켓 에러 시
        onWebSocketError: (dynamic error) {
          debugPrint('[WebSocket] WebSocket error: $error');
          onError?.call(error.toString());
        },

        // SockJS 사용 (브라우저 호환성)
        useSockJS: true,

        // 재연결 설정
        reconnectDelay: const Duration(seconds: 5),
        heartbeatIncoming: const Duration(seconds: 10),
        heartbeatOutgoing: const Duration(seconds: 10),
      ),
    );

    _stompClient!.activate();
  }

  /// 방 입장 및 상태 구독
  /// 구독: /topic/room/{roomUid}/state
  void joinRoom(String roomUid, String userKey) {
    if (!_isConnected || _stompClient == null) {
      debugPrint('[WebSocket] Not connected. Call connect() first.');
      return;
    }

    // 방 상태 구독
    _stompClient!.subscribe(
      destination: '/topic/room/$roomUid/state',
      callback: (StompFrame frame) {
        if (frame.body != null) {
          try {
            final data = json.decode(frame.body!);
            debugPrint('[WebSocket] Room state update: $data');

            // RoomStatus 객체로 변환하여 콜백 호출
            if (onRoomStatusUpdate != null) {
              onRoomStatusUpdate!(RoomStatus.fromJson(data));
            }
          } catch (e) {
            debugPrint('[WebSocket] Failed to parse state: $e');
          }
        }
      },
    );

    // 방 입장 메시지 전송
    _stompClient!.send(
      destination: '/app/room/join',
      body: json.encode({
        'roomUid': roomUid,
        'userKey': userKey,
      }),
    );

    debugPrint('[WebSocket] Joined room: $roomUid');
  }

  /// 번호표 발급 (WebSocket 방식)
  /// 전송: /app/room/issue
  /// 구독: /user/queue/ticket (개인), /topic/room/{roomUid}/state (전체)
  void issueTicket(String roomUid, String userKey) {
    if (!_isConnected || _stompClient == null) {
      debugPrint('[WebSocket] Not connected. Call connect() first.');
      return;
    }

    // 개인 티켓 응답 구독
    _stompClient!.subscribe(
      destination: '/user/queue/ticket',
      callback: (StompFrame frame) {
        if (frame.body != null) {
          try {
            final data = json.decode(frame.body!);
            debugPrint('[WebSocket] Ticket issued: $data');

            if (onTicketIssued != null) {
              onTicketIssued!(TicketIssueResponse.fromJson(data));
            }
          } catch (e) {
            debugPrint('[WebSocket] Failed to parse ticket: $e');
          }
        }
      },
    );

    // 티켓 발급 요청
    _stompClient!.send(
      destination: '/app/room/issue',
      body: json.encode({
        'roomUid': roomUid,
        'userKey': userKey,
      }),
    );

    debugPrint('[WebSocket] Ticket issue requested');
  }

  /// 알림 구독 (특정 번호 호출 시)
  /// 구독: /topic/room/{roomUid}/notification/{userKey}
  void subscribeToNotifications(String roomUid, String userKey) {
    if (!_isConnected || _stompClient == null) {
      debugPrint('[WebSocket] Not connected. Call connect() first.');
      return;
    }

    _stompClient!.subscribe(
      destination: '/topic/room/$roomUid/notification/$userKey',
      callback: (StompFrame frame) {
        if (frame.body != null) {
          try {
            final data = json.decode(frame.body!);
            debugPrint('[WebSocket] Notification received: $data');

            if (onNotification != null) {
              onNotification!(data);
            }
          } catch (e) {
            debugPrint('[WebSocket] Failed to parse notification: $e');
          }
        }
      },
    );

    debugPrint('[WebSocket] Subscribed to notifications');
  }

  /// 특정 번호에 알림 전송 (관리자용)
  /// 전송: /app/room/notify
  void sendNotification(String roomUid, int number, String message) {
    if (!_isConnected || _stompClient == null) {
      debugPrint('[WebSocket] Not connected');
      return;
    }

    _stompClient!.send(
      destination: '/app/room/notify',
      body: json.encode({
        'roomUid': roomUid,
        'number': number,
        'message': message,
      }),
    );

    debugPrint('[WebSocket] Notification sent to number $number');
  }

  /// 연결 종료
  void disconnect() {
    if (_stompClient != null) {
      _stompClient!.deactivate();
      _stompClient = null;
      _isConnected = false;
      debugPrint('[WebSocket] Disconnected');
    }
  }

  /// 리소스 정리
  void dispose() {
    disconnect();
  }
}
```

---

## 전체 통합 예제

### 1. 디바이스 ID 생성 유틸리티

```dart
// lib/utils/device_utils.dart

import 'package:uuid/uuid.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:device_info_plus/device_info_plus.dart';

class DeviceUtils {
  static const String _deviceIdKey = 'device_id';

  /// 디바이스 고유 ID 가져오기 (없으면 생성)
  static Future<String> getDeviceId() async {
    final prefs = await SharedPreferences.getInstance();

    // 기존에 저장된 ID가 있으면 반환
    String? deviceId = prefs.getString(_deviceIdKey);
    if (deviceId != null && deviceId.isNotEmpty) {
      return deviceId;
    }

    // 새 ID 생성
    deviceId = const Uuid().v4();
    await prefs.setString(_deviceIdKey, deviceId);

    return deviceId;
  }

  /// 실제 디바이스 정보 기반 ID (선택)
  static Future<String> getDeviceInfo() async {
    final deviceInfo = DeviceInfoPlugin();

    try {
      if (Theme.of(context).platform == TargetPlatform.android) {
        final androidInfo = await deviceInfo.androidInfo;
        return androidInfo.id; // Android ID
      } else if (Theme.of(context).platform == TargetPlatform.iOS) {
        final iosInfo = await deviceInfo.iosInfo;
        return iosInfo.identifierForVendor ?? const Uuid().v4();
      }
    } catch (e) {
      // 실패 시 UUID 생성
      return const Uuid().v4();
    }

    return const Uuid().v4();
  }
}
```

### 2. 통합 서비스 Provider

```dart
// lib/services/queue_service_provider.dart

import 'package:flutter/foundation.dart';
import 'queue_api_service.dart';
import 'queue_websocket_service.dart';
import '../utils/device_utils.dart';

class QueueServiceProvider with ChangeNotifier {
  final QueueApiService _apiService = QueueApiService();
  final QueueWebSocketService _wsService = QueueWebSocketService();

  String? _deviceId;
  String? _currentRoomUid;
  RoomStatus? _currentRoomStatus;
  TicketIssueResponse? _myTicket;
  List<QueueRoom> _rooms = [];

  // Getters
  String? get deviceId => _deviceId;
  RoomStatus? get currentRoomStatus => _currentRoomStatus;
  TicketIssueResponse? get myTicket => _myTicket;
  List<QueueRoom> get rooms => _rooms;
  bool get isWebSocketConnected => _wsService.isConnected;

  QueueServiceProvider() {
    _initialize();
  }

  /// 초기화
  Future<void> _initialize() async {
    // 디바이스 ID 로드
    _deviceId = await DeviceUtils.getDeviceId();
    debugPrint('[QueueService] Device ID: $_deviceId');

    // WebSocket 콜백 설정
    _wsService.onConnected = () {
      debugPrint('[QueueService] WebSocket connected');
      notifyListeners();
    };

    _wsService.onRoomStatusUpdate = (status) {
      _currentRoomStatus = status;
      debugPrint('[QueueService] Room status updated: ${status.currentNumber}');
      notifyListeners();
    };

    _wsService.onTicketIssued = (ticket) {
      _myTicket = ticket;
      debugPrint('[QueueService] My ticket: ${ticket.number}');
      notifyListeners();
    };

    _wsService.onNotification = (data) {
      debugPrint('[QueueService] Notification: $data');
      // 알림 처리 (다이얼로그 표시 등)
    };

    _wsService.onError = (error) {
      debugPrint('[QueueService] WebSocket error: $error');
    };
  }

  /// 1. 활성화된 방 목록 가져오기
  Future<void> fetchActiveRooms() async {
    try {
      _rooms = await _apiService.getActiveRooms();
      notifyListeners();
    } catch (e) {
      debugPrint('[QueueService] Failed to fetch rooms: $e');
      rethrow;
    }
  }

  /// 2. 방 입장 (WebSocket 연결 + 구독)
  Future<void> joinRoom(String roomUid) async {
    if (_deviceId == null) {
      throw Exception('Device ID not initialized');
    }

    _currentRoomUid = roomUid;

    // WebSocket 연결
    if (!_wsService.isConnected) {
      _wsService.connect();

      // 연결 대기 (최대 5초)
      int attempts = 0;
      while (!_wsService.isConnected && attempts < 50) {
        await Future.delayed(const Duration(milliseconds: 100));
        attempts++;
      }

      if (!_wsService.isConnected) {
        throw Exception('WebSocket connection timeout');
      }
    }

    // 방 입장 및 상태 구독
    _wsService.joinRoom(roomUid, _deviceId!);

    // 알림 구독
    _wsService.subscribeToNotifications(roomUid, _deviceId!);

    // REST API로 현재 상태도 가져오기
    await refreshRoomStatus();
  }

  /// 3. 번호표 발급 (WebSocket 방식)
  Future<void> issueTicketViaWebSocket() async {
    if (_currentRoomUid == null || _deviceId == null) {
      throw Exception('Room not joined');
    }

    _wsService.issueTicket(_currentRoomUid!, _deviceId!);
  }

  /// 4. 번호표 발급 (REST API 방식)
  Future<TicketIssueResponse> issueTicketViaRest(String roomUid) async {
    if (_deviceId == null) {
      throw Exception('Device ID not initialized');
    }

    try {
      final ticket = await _apiService.issueTicket(roomUid, _deviceId!);
      _myTicket = ticket;
      notifyListeners();
      return ticket;
    } catch (e) {
      debugPrint('[QueueService] Failed to issue ticket: $e');
      rethrow;
    }
  }

  /// 5. 방 현황 새로고침
  Future<void> refreshRoomStatus() async {
    if (_currentRoomUid == null) return;

    try {
      _currentRoomStatus = await _apiService.getRoomStatus(_currentRoomUid!);
      notifyListeners();
    } catch (e) {
      debugPrint('[QueueService] Failed to refresh status: $e');
    }
  }

  /// 6. 다음 번호 호출 (관리자용)
  Future<int> callNextNumber(String roomUid) async {
    try {
      return await _apiService.callNextNumber(roomUid);
    } catch (e) {
      debugPrint('[QueueService] Failed to call next number: $e');
      rethrow;
    }
  }

  /// 7. 방 나가기
  void leaveRoom() {
    _currentRoomUid = null;
    _currentRoomStatus = null;
    _myTicket = null;
    _wsService.disconnect();
    notifyListeners();
  }

  @override
  void dispose() {
    _apiService.dispose();
    _wsService.dispose();
    super.dispose();
  }
}
```

### 3. UI 예제 - 방 목록 화면

```dart
// lib/screens/room_list_screen.dart

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/queue_service_provider.dart';

class RoomListScreen extends StatefulWidget {
  const RoomListScreen({Key? key}) : super(key: key);

  @override
  State<RoomListScreen> createState() => _RoomListScreenState();
}

class _RoomListScreenState extends State<RoomListScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _loadRooms();
    });
  }

  Future<void> _loadRooms() async {
    final provider = context.read<QueueServiceProvider>();
    try {
      await provider.fetchActiveRooms();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('방 목록 로드 실패: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('번호표 방 목록'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loadRooms,
          ),
        ],
      ),
      body: Consumer<QueueServiceProvider>(
        builder: (context, provider, child) {
          if (provider.rooms.isEmpty) {
            return const Center(
              child: CircularProgressIndicator(),
            );
          }

          return RefreshIndicator(
            onRefresh: _loadRooms,
            child: ListView.builder(
              itemCount: provider.rooms.length,
              itemBuilder: (context, index) {
                final room = provider.rooms[index];
                return Card(
                  margin: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 8,
                  ),
                  child: ListTile(
                    title: Text(
                      room.roomName,
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                    subtitle: Text(
                      '현재 번호: ${room.currentNumber} | '
                      '대기 중: ${room.waitingCount}명',
                    ),
                    trailing: room.isActive
                        ? const Icon(Icons.check_circle, color: Colors.green)
                        : const Icon(Icons.cancel, color: Colors.grey),
                    onTap: () {
                      Navigator.pushNamed(
                        context,
                        '/room',
                        arguments: room.roomUid,
                      );
                    },
                  ),
                );
              },
            ),
          );
        },
      ),
    );
  }
}
```

### 4. UI 예제 - 방 상세 화면 (번호표 발급)

```dart
// lib/screens/room_detail_screen.dart

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/queue_service_provider.dart';

class RoomDetailScreen extends StatefulWidget {
  final String roomUid;

  const RoomDetailScreen({Key? key, required this.roomUid}) : super(key: key);

  @override
  State<RoomDetailScreen> createState() => _RoomDetailScreenState();
}

class _RoomDetailScreenState extends State<RoomDetailScreen> {
  bool _isJoining = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _joinRoom();
    });
  }

  Future<void> _joinRoom() async {
    setState(() => _isJoining = true);

    final provider = context.read<QueueServiceProvider>();
    try {
      await provider.joinRoom(widget.roomUid);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('방 입장 실패: $e')),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isJoining = false);
      }
    }
  }

  Future<void> _issueTicket() async {
    final provider = context.read<QueueServiceProvider>();

    try {
      // REST API 방식 사용
      await provider.issueTicketViaRest(widget.roomUid);

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('번호표가 발급되었습니다!')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('발급 실패: $e')),
        );
      }
    }
  }

  @override
  void dispose() {
    context.read<QueueServiceProvider>().leaveRoom();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('번호표 발급'),
        actions: [
          Consumer<QueueServiceProvider>(
            builder: (context, provider, child) {
              return IconButton(
                icon: Icon(
                  Icons.wifi,
                  color: provider.isWebSocketConnected
                    ? Colors.green
                    : Colors.grey,
                ),
                onPressed: null,
              );
            },
          ),
        ],
      ),
      body: _isJoining
          ? const Center(child: CircularProgressIndicator())
          : Consumer<QueueServiceProvider>(
              builder: (context, provider, child) {
                final status = provider.currentRoomStatus;
                final myTicket = provider.myTicket;

                return Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      // 방 정보
                      Card(
                        child: Padding(
                          padding: const EdgeInsets.all(16.0),
                          child: Column(
                            children: [
                              Text(
                                status?.roomName ?? '로딩 중...',
                                style: Theme.of(context).textTheme.headlineSmall,
                              ),
                              const SizedBox(height: 16),
                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceAround,
                                children: [
                                  _buildInfoColumn(
                                    '현재 번호',
                                    '${status?.currentNumber ?? 0}',
                                    Colors.blue,
                                  ),
                                  _buildInfoColumn(
                                    '대기 중',
                                    '${status?.waitingCount ?? 0}명',
                                    Colors.orange,
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ),

                      const SizedBox(height: 24),

                      // 내 번호표
                      if (myTicket != null) ...[
                        Card(
                          color: Colors.green.shade50,
                          child: Padding(
                            padding: const EdgeInsets.all(24.0),
                            child: Column(
                              children: [
                                const Text(
                                  '내 번호',
                                  style: TextStyle(fontSize: 16),
                                ),
                                const SizedBox(height: 8),
                                Text(
                                  '${myTicket.number}',
                                  style: const TextStyle(
                                    fontSize: 72,
                                    fontWeight: FontWeight.bold,
                                    color: Colors.green,
                                  ),
                                ),
                                const SizedBox(height: 8),
                                Text(
                                  myTicket.duplicated
                                    ? '이미 발급받은 번호입니다'
                                    : '발급 완료',
                                  style: TextStyle(
                                    color: Colors.grey.shade600,
                                  ),
                                ),
                                const SizedBox(height: 16),
                                Text(
                                  '앞에 ${(myTicket.number - (status?.currentNumber ?? 0)).clamp(0, 999)}명 대기 중',
                                  style: const TextStyle(
                                    fontSize: 18,
                                    fontWeight: FontWeight.w500,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ] else ...[
                        ElevatedButton(
                          onPressed: _issueTicket,
                          style: ElevatedButton.styleFrom(
                            padding: const EdgeInsets.all(24),
                            textStyle: const TextStyle(fontSize: 20),
                          ),
                          child: const Text('번호표 발급받기'),
                        ),
                      ],

                      const Spacer(),

                      // 새로고침 버튼
                      OutlinedButton.icon(
                        onPressed: () => provider.refreshRoomStatus(),
                        icon: const Icon(Icons.refresh),
                        label: const Text('새로고침'),
                      ),
                    ],
                  ),
                );
              },
            ),
    );
  }

  Widget _buildInfoColumn(String label, String value, Color color) {
    return Column(
      children: [
        Text(
          label,
          style: TextStyle(
            color: Colors.grey.shade600,
            fontSize: 14,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          value,
          style: TextStyle(
            fontSize: 32,
            fontWeight: FontWeight.bold,
            color: color,
          ),
        ),
      ],
    );
  }
}
```

### 5. main.dart

```dart
// lib/main.dart

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'services/queue_service_provider.dart';
import 'screens/room_list_screen.dart';
import 'screens/room_detail_screen.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (_) => QueueServiceProvider(),
      child: MaterialApp(
        title: '번호표 시스템',
        theme: ThemeData(
          primarySwatch: Colors.blue,
          useMaterial3: true,
        ),
        home: const RoomListScreen(),
        onGenerateRoute: (settings) {
          if (settings.name == '/room') {
            final roomUid = settings.arguments as String;
            return MaterialPageRoute(
              builder: (_) => RoomDetailScreen(roomUid: roomUid),
            );
          }
          return null;
        },
      ),
    );
  }
}
```

---

## 문제 해결

### 1. WebSocket 연결 실패

**증상**: WebSocket 연결이 되지 않음

**해결 방법**:
```dart
// Android: network_security_config.xml 설정
// android/app/src/main/res/xml/network_security_config.xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">localhost</domain>
        <domain includeSubdomains="true">10.0.2.2</domain>
        <domain includeSubdomains="true">your-server-ip</domain>
    </domain-config>
</network-security-config>

// AndroidManifest.xml에 추가
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

```dart
// iOS: Info.plist 설정
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <true/>
</dict>
```

### 2. CORS 에러

**서버측 설정 확인** (이미 설정되어 있음):
```java
// WebSocketConfig.java
registry.addEndpoint("/ws")
    .setAllowedOriginPatterns("*")  // 모든 출처 허용
    .withSockJS();
```

### 3. Android 에뮬레이터에서 localhost 연결

```dart
// localhost 대신 10.0.2.2 사용
static const String baseUrl = 'http://10.0.2.2:8080';
static const String websocketUrl = 'ws://10.0.2.2:8080/ws';
```

### 4. 실시간 업데이트가 안 됨

- WebSocket 연결 상태 확인
- 올바른 토픽 구독 확인
- 서버 로그 확인

### 5. JSON 파싱 에러

```dart
// 서버 응답 디버깅
response.bodyBytes를 utf8.decode() 하여 한글 깨짐 방지
final data = json.decode(utf8.decode(response.bodyBytes));
```

---

## API 엔드포인트 요약표

### REST API

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/queue/rooms` | 활성화된 방 목록 조회 |
| GET | `/api/queue/room/{roomId}` | 특정 방 정보 조회 |
| POST | `/api/queue/room/{roomId}/ticket` | 번호표 발급 |
| GET | `/api/queue/room/{roomId}/status` | 방 현황 조회 |
| GET | `/api/queue/room/{roomId}/tickets` | 방의 모든 번호표 조회 |
| POST | `/api/queue/room/{roomId}/call` | 다음 번호 호출 (관리자) |
| DELETE | `/api/queue/ticket/{ticketId}` | 번호표 취소 |

### WebSocket (STOMP)

| Type | Destination | 설명 |
|------|-------------|------|
| 전송 | `/app/room/join` | 방 입장 |
| 전송 | `/app/room/issue` | 번호표 발급 |
| 전송 | `/app/room/notify` | 알림 전송 (관리자) |
| 구독 | `/topic/room/{roomUid}/state` | 방 상태 업데이트 수신 |
| 구독 | `/user/queue/ticket` | 개인 티켓 수신 |
| 구독 | `/topic/room/{roomUid}/notification/{userKey}` | 개인 알림 수신 |

---

## 추가 참고 사항

### 보안 고려사항

1. **HTTPS/WSS 사용**: 프로덕션 환경에서는 암호화된 연결 사용
2. **인증 토큰**: 필요시 JWT 토큰 추가
3. **디바이스 ID 보안**: 안전하게 저장 및 관리

### 성능 최적화

1. **연결 재사용**: WebSocket 연결을 앱 생명주기 동안 유지
2. **캐싱**: 방 목록 등을 로컬에 캐싱
3. **에러 재시도**: 네트워크 에러 시 자동 재시도 로직

---

## 문의

문제가 발생하거나 질문이 있으시면 [GitHub Issues](https://github.com/TetraLabYonam/api/issues)에 등록해주세요.
