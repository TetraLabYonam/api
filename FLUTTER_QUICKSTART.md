# Flutter 빠른 시작 가이드 - 번호표 시스템

Flutter 앱에서 번호표 시스템을 5분 안에 연동하는 방법입니다.

## 1단계: 패키지 설치 (1분)

`pubspec.yaml`:
```yaml
dependencies:
  http: ^1.1.0
  stomp_dart_client: ^1.0.0
  uuid: ^4.1.0
  provider: ^6.1.1
```

```bash
flutter pub get
```

## 2단계: 서버 주소 설정 (30초)

```dart
// lib/config/api_config.dart
class ApiConfig {
  // ⚠️ 실제 서버 주소로 변경하세요

  // 로컬 개발 (Android 에뮬레이터)
  static const String baseUrl = 'http://10.0.2.2:8080';
  static const String wsUrl = 'ws://10.0.2.2:8080/ws';

  // 로컬 개발 (iOS 시뮬레이터 또는 실제 디바이스)
  // static const String baseUrl = 'http://localhost:8080';
  // static const String wsUrl = 'ws://localhost:8080/ws';

  // 프로덕션
  // static const String baseUrl = 'https://your-domain.com';
  // static const String wsUrl = 'wss://your-domain.com/ws';
}
```

## 3단계: 기본 HTTP 요청 (2분)

```dart
// lib/services/simple_queue_service.dart
import 'dart:convert';
import 'package:http/http.dart' as http;
import '../config/api_config.dart';

class SimpleQueueService {
  // 1. 활성화된 방 목록 가져오기
  static Future<List<dynamic>> getRooms() async {
    final response = await http.get(
      Uri.parse('${ApiConfig.baseUrl}/api/queue/rooms'),
    );

    if (response.statusCode == 200) {
      return json.decode(utf8.decode(response.bodyBytes));
    }
    throw Exception('Failed to load rooms');
  }

  // 2. 번호표 발급하기
  static Future<Map<String, dynamic>> issueTicket(
    String roomId,
    String deviceId,
  ) async {
    final response = await http.post(
      Uri.parse('${ApiConfig.baseUrl}/api/queue/room/$roomId/ticket'),
      headers: {'Content-Type': 'application/json'},
      body: json.encode({'userDeviceId': deviceId}),
    );

    if (response.statusCode == 200 || response.statusCode == 201) {
      return json.decode(utf8.decode(response.bodyBytes));
    }
    throw Exception('Failed to issue ticket');
  }

  // 3. 방 상태 확인하기
  static Future<Map<String, dynamic>> getRoomStatus(String roomId) async {
    final response = await http.get(
      Uri.parse('${ApiConfig.baseUrl}/api/queue/room/$roomId/status'),
    );

    if (response.statusCode == 200) {
      return json.decode(utf8.decode(response.bodyBytes));
    }
    throw Exception('Failed to load room status');
  }
}
```

## 4단계: 간단한 UI (1.5분)

```dart
// lib/main.dart
import 'package:flutter/material.dart';
import 'package:uuid/uuid.dart';
import 'services/simple_queue_service.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '번호표 앱',
      theme: ThemeData(primarySwatch: Colors.blue),
      home: const QuickStartPage(),
    );
  }
}

class QuickStartPage extends StatefulWidget {
  const QuickStartPage({Key? key}) : super(key: key);

  @override
  State<QuickStartPage> createState() => _QuickStartPageState();
}

class _QuickStartPageState extends State<QuickStartPage> {
  List<dynamic> rooms = [];
  String deviceId = const Uuid().v4();
  Map<String, dynamic>? myTicket;
  bool isLoading = false;

  @override
  void initState() {
    super.initState();
    loadRooms();
  }

  Future<void> loadRooms() async {
    setState(() => isLoading = true);
    try {
      final data = await SimpleQueueService.getRooms();
      setState(() {
        rooms = data;
        isLoading = false;
      });
    } catch (e) {
      setState(() => isLoading = false);
      showError('방 목록 로드 실패: $e');
    }
  }

  Future<void> getTicket(String roomId) async {
    try {
      final ticket = await SimpleQueueService.issueTicket(roomId, deviceId);
      setState(() => myTicket = ticket);

      showDialog(
        context: context,
        builder: (_) => AlertDialog(
          title: const Text('번호표 발급 완료'),
          content: Text(
            '발급 번호: ${ticket['number']}\n'
            '${ticket['duplicated'] ? '(이미 발급받은 번호)' : ''}',
            style: const TextStyle(fontSize: 24),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('확인'),
            ),
          ],
        ),
      );
    } catch (e) {
      showError('번호표 발급 실패: $e');
    }
  }

  void showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('번호표 시스템'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: loadRooms,
          ),
        ],
      ),
      body: isLoading
          ? const Center(child: CircularProgressIndicator())
          : rooms.isEmpty
              ? const Center(child: Text('방이 없습니다'))
              : ListView.builder(
                  itemCount: rooms.length,
                  itemBuilder: (context, index) {
                    final room = rooms[index];
                    return Card(
                      margin: const EdgeInsets.all(8),
                      child: ListTile(
                        title: Text(room['roomName'] ?? ''),
                        subtitle: Text(
                          '현재 번호: ${room['currentNumber']} | '
                          '대기: ${room['waitingCount']}명',
                        ),
                        trailing: ElevatedButton(
                          onPressed: () => getTicket(room['roomUid']),
                          child: const Text('번호표'),
                        ),
                      ),
                    );
                  },
                ),
      bottomSheet: myTicket != null
          ? Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              color: Colors.green.shade100,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text('내 번호', style: TextStyle(fontSize: 16)),
                  Text(
                    '${myTicket!['number']}',
                    style: const TextStyle(
                      fontSize: 48,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              ),
            )
          : null,
    );
  }
}
```

## 완료! 🎉

앱을 실행하면:
1. 활성화된 방 목록이 표시됩니다
2. 각 방에서 번호표를 발급받을 수 있습니다
3. 발급받은 번호가 하단에 표시됩니다

---

## 다음 단계

### WebSocket으로 실시간 업데이트 받기

더 자세한 내용은 [FLUTTER_INTEGRATION_GUIDE.md](FLUTTER_INTEGRATION_GUIDE.md)를 참조하세요.

### 테스트 방법

1. **서버 실행 확인**
   ```bash
   # 브라우저에서 접속
   http://localhost:8080/swagger-ui/index.html
   ```

2. **Flutter 앱 실행**
   ```bash
   flutter run
   ```

3. **Android 에뮬레이터 네트워크 설정**
   - `localhost` 대신 `10.0.2.2` 사용
   - `AndroidManifest.xml`에 인터넷 권한 추가:
     ```xml
     <uses-permission android:name="android.permission.INTERNET"/>
     ```

---

## 주요 API 엔드포인트

| API | 용도 |
|-----|------|
| `GET /api/queue/rooms` | 방 목록 조회 |
| `POST /api/queue/room/{roomId}/ticket` | 번호표 발급 |
| `GET /api/queue/room/{roomId}/status` | 방 현황 조회 |
| `POST /api/queue/room/{roomId}/call` | 다음 번호 호출 (관리자) |

---

## 문제 해결

### "Failed to load rooms" 에러
- 서버가 실행 중인지 확인
- 네트워크 연결 확인
- `ApiConfig.baseUrl`이 올바른지 확인

### Android 에뮬레이터에서 연결 안 됨
- `10.0.2.2` 사용 (localhost 대신)
- 인터넷 권한 확인

### iOS 시뮬레이터에서 연결 안 됨
- `localhost` 또는 실제 IP 주소 사용
- `Info.plist`에 ATS 설정 추가

---

## 더 알아보기

- **전체 가이드**: [FLUTTER_INTEGRATION_GUIDE.md](FLUTTER_INTEGRATION_GUIDE.md)
- **API 문서**: http://localhost:8080/swagger-ui/index.html
- **비즈니스 로직**: [BUSINESS_LOGIC_FOR_PAPER.md](BUSINESS_LOGIC_FOR_PAPER.md)