# Room API 가이드

번호표 방 관리를 위한 REST API 문서입니다.

## 목차
1. [API 엔드포인트 목록](#api-엔드포인트-목록)
2. [방 생성하기](#방-생성하기)
3. [방 조회하기](#방-조회하기)
4. [방 관리하기](#방-관리하기)
5. [사용 예제](#사용-예제)

---

## API 엔드포인트 목록

| Method | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/api/v1/rooms` | 활성화된 방 목록 조회 |
| GET | `/api/v1/rooms/all` | 전체 방 목록 조회 (비활성화 포함) |
| GET | `/api/v1/rooms/{id}` | 방 상세 조회 (ID) |
| GET | `/api/v1/rooms/uid/{roomUid}` | 방 상세 조회 (UID) |
| GET | `/api/v1/rooms/details` | 방 상세 정보 조회 (TicketService 기반) |
| GET | `/api/v1/rooms/{uid}/state` | 방 상태 조회 |
| GET | `/api/v1/rooms/{uid}/issuances` | 방 발급 내역 조회 |
| POST | `/api/v1/rooms` | 방 생성 |
| POST | `/api/v1/rooms/batch` | 방 일괄 생성 |
| POST | `/api/v1/rooms/{uid}/reset` | 방 번호 초기화 |
| PUT | `/api/v1/rooms/{id}` | 방 정보 수정 |
| PUT | `/api/v1/rooms/{id}/activate` | 방 활성화 |
| PUT | `/api/v1/rooms/{id}/deactivate` | 방 비활성화 |
| DELETE | `/api/v1/rooms/{id}` | 방 삭제 |

---

## 방 생성하기

### 1. 단일 방 생성

**요청**
```bash
POST /api/v1/rooms
Content-Type: application/json

{
  "roomName": "물금청소년문화의집",
  "roomUid": "optional-custom-uid"  // 선택사항 (없으면 자동 생성)
}
```

**응답**
```json
{
  "id": 1,
  "roomUid": "f59f0999-660e-44",
  "roomName": "물금청소년문화의집",
  "isActive": true,
  "currentNumber": 0,
  "lastIssuedNumber": 0,
  "waitingCount": 0,
  "createdAt": "2025-11-28T10:49:02.078780",
  "updatedAt": "2025-11-28T10:49:02.078797"
}
```

### 2. 여러 방 일괄 생성

**요청**
```bash
POST /api/v1/rooms/batch
Content-Type: application/json

{
  "roomNames": [
    "물금청소년문화의집",
    "동면 행정복지센터",
    "원동면 행정복지센터",
    "상북면 행정복지센터",
    "하북면 행정복지센터",
    "양산시니어클럽"
  ]
}
```

**응답**
```json
[
  {
    "id": 1,
    "roomUid": "f59f0999-660e-44",
    "roomName": "물금청소년문화의집",
    ...
  },
  {
    "id": 2,
    "roomUid": "6b32ce1c-d8e8-46",
    "roomName": "동면 행정복지센터",
    ...
  },
  ...
]
```

**curl 예제**
```bash
curl -X POST http://localhost:8080/api/v1/rooms/batch \
  -H "Content-Type: application/json" \
  -d '{
    "roomNames": [
      "물금청소년문화의집",
      "동면 행정복지센터",
      "원동면 행정복지센터",
      "상북면 행정복지센터",
      "하북면 행정복지센터",
      "양산시니어클럽"
    ]
  }'
```

---

## 방 조회하기

### 1. 활성화된 방 목록 조회

**요청**
```bash
GET /api/v1/rooms
```

**응답**
```json
[
  {
    "id": 1,
    "roomUid": "f59f0999-660e-44",
    "roomName": "물금청소년문화의집",
    "isActive": true,
    "currentNumber": 0,
    "lastIssuedNumber": 0,
    "waitingCount": 0,
    ...
  },
  ...
]
```

### 2. 전체 방 목록 조회 (비활성화 포함)

**요청**
```bash
GET /api/v1/rooms/all
```

### 3. 특정 방 조회 (ID)

**요청**
```bash
GET /api/v1/rooms/1
```

**응답**
```json
{
  "id": 1,
  "roomUid": "f59f0999-660e-44",
  "roomName": "물금청소년문화의집",
  "isActive": true,
  "currentNumber": 0,
  "lastIssuedNumber": 0,
  "waitingCount": 0,
  "createdAt": "2025-11-28T10:49:02.078780",
  "updatedAt": "2025-11-28T10:49:02.078797"
}
```

### 4. 특정 방 조회 (UID)

**요청**
```bash
GET /api/v1/rooms/uid/f59f0999-660e-44
```

### 5. 방 상태 조회

**요청**
```bash
GET /api/v1/rooms/{uid}/state
```

**응답**
```json
{
  "roomId": "f59f0999-660e-44",
  "roomName": "물금청소년문화의집",
  "currentNumber": 5,
  "lastNumber": 10,
  "count": 5
}
```

### 6. 방 발급 내역 조회

**요청**
```bash
GET /api/v1/rooms/{uid}/issuances
```

**응답**
```json
[
  {
    "number": 1,
    "userKey": "user123",
    "issuedAt": "2025-11-28T10:50:00"
  },
  ...
]
```

---

## 방 관리하기

### 1. 방 정보 수정

**요청**
```bash
PUT /api/v1/rooms/1
Content-Type: application/json

{
  "roomName": "물금청소년문화의집 (업데이트)",
  "isActive": true,
  "currentNumber": 5
}
```

**응답**
```json
{
  "id": 1,
  "roomUid": "f59f0999-660e-44",
  "roomName": "물금청소년문화의집 (업데이트)",
  "isActive": true,
  "currentNumber": 5,
  ...
}
```

### 2. 방 활성화

**요청**
```bash
PUT /api/v1/rooms/1/activate
```

**응답**
```json
{
  "message": "방이 활성화되었습니다."
}
```

### 3. 방 비활성화

**요청**
```bash
PUT /api/v1/rooms/1/deactivate
```

**응답**
```json
{
  "message": "방이 비활성화되었습니다."
}
```

### 4. 방 번호 초기화

**요청**
```bash
POST /api/v1/rooms/{uid}/reset
```

**응답**
```json
{
  "success": "true",
  "message": "방이 초기화되었습니다."
}
```

### 5. 방 삭제

**요청**
```bash
DELETE /api/v1/rooms/1
```

**응답**
```json
{
  "message": "방이 삭제되었습니다."
}
```

---

## 사용 예제

### JavaScript (Fetch API)

#### 방 목록 조회
```javascript
// 활성화된 방 목록 조회
fetch('http://localhost:8080/api/v1/rooms')
  .then(response => response.json())
  .then(rooms => {
    console.log('방 목록:', rooms);
  });
```

#### 방 생성
```javascript
// 단일 방 생성
fetch('http://localhost:8080/api/v1/rooms', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    roomName: '물금청소년문화의집'
  })
})
  .then(response => response.json())
  .then(room => {
    console.log('생성된 방:', room);
  });
```

#### 방 일괄 생성
```javascript
fetch('http://localhost:8080/api/v1/rooms/batch', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    roomNames: [
      '물금청소년문화의집',
      '동면 행정복지센터',
      '원동면 행정복지센터',
      '상북면 행정복지센터',
      '하북면 행정복지센터',
      '양산시니어클럽'
    ]
  })
})
  .then(response => response.json())
  .then(rooms => {
    console.log('생성된 방들:', rooms);
  });
```

#### 방 정보 수정
```javascript
fetch('http://localhost:8080/api/v1/rooms/1', {
  method: 'PUT',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    roomName: '물금청소년문화의집 (업데이트)',
    isActive: true
  })
})
  .then(response => response.json())
  .then(room => {
    console.log('수정된 방:', room);
  });
```

### Flutter (http package)

#### 방 목록 조회
```dart
import 'package:http/http.dart' as http;
import 'dart:convert';

Future<List<dynamic>> getRooms() async {
  final response = await http.get(
    Uri.parse('http://localhost:8080/api/v1/rooms'),
  );

  if (response.statusCode == 200) {
    return json.decode(utf8.decode(response.bodyBytes));
  } else {
    throw Exception('Failed to load rooms');
  }
}
```

#### 방 생성
```dart
Future<Map<String, dynamic>> createRoom(String roomName) async {
  final response = await http.post(
    Uri.parse('http://localhost:8080/api/v1/rooms'),
    headers: {'Content-Type': 'application/json'},
    body: json.encode({
      'roomName': roomName,
    }),
  );

  if (response.statusCode == 201) {
    return json.decode(utf8.decode(response.bodyBytes));
  } else {
    throw Exception('Failed to create room');
  }
}
```

#### 방 일괄 생성
```dart
Future<List<dynamic>> createRoomsBatch(List<String> roomNames) async {
  final response = await http.post(
    Uri.parse('http://localhost:8080/api/v1/rooms/batch'),
    headers: {'Content-Type': 'application/json'},
    body: json.encode({
      'roomNames': roomNames,
    }),
  );

  if (response.statusCode == 201) {
    return json.decode(utf8.decode(response.bodyBytes));
  } else {
    throw Exception('Failed to create rooms');
  }
}

// 사용 예
final roomNames = [
  '물금청소년문화의집',
  '동면 행정복지센터',
  '원동면 행정복지센터',
  '상북면 행정복지센터',
  '하북면 행정복지센터',
  '양산시니어클럽',
];
final rooms = await createRoomsBatch(roomNames);
```

---

## Response DTO 구조

### QueueRoomDto
```json
{
  "id": 1,                          // 방 ID (Long)
  "roomUid": "f59f0999-660e-44",    // 방 고유 식별자 (String)
  "roomName": "물금청소년문화의집",   // 방 이름 (String)
  "isActive": true,                 // 활성화 상태 (Boolean)
  "currentNumber": 0,               // 현재 번호 (Integer)
  "lastIssuedNumber": 0,            // 마지막 발급 번호 (Integer)
  "waitingCount": 0,                // 대기 인원 (Integer)
  "createdAt": "2025-11-28T10:49:02.078780",  // 생성 시간 (LocalDateTime)
  "updatedAt": "2025-11-28T10:49:02.078797"   // 수정 시간 (LocalDateTime)
}
```

---

## Swagger UI

API 문서는 Swagger UI에서도 확인할 수 있습니다:

```
http://localhost:8080/swagger-ui/index.html
```

**Room API 섹션**에서 모든 엔드포인트를 확인하고 직접 테스트할 수 있습니다.

---

## 에러 처리

### 400 Bad Request
```json
{
  "message": "roomNames는 필수입니다.",
  "timestamp": "2025-11-28T10:49:02",
  "path": "/api/v1/rooms/batch"
}
```

### 404 Not Found
```json
{
  "message": "방을 찾을 수 없습니다. ID: 999",
  "timestamp": "2025-11-28T10:49:02",
  "path": "/api/v1/rooms/999"
}
```

### 500 Internal Server Error
```json
{
  "message": "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
  "timestamp": "2025-11-28T10:49:02",
  "path": "/api/v1/rooms"
}
```

---

## 참고사항

1. **방 UID 자동 생성**: `roomUid`를 제공하지 않으면 UUID 기반으로 자동 생성됩니다.
2. **방 번호 초기화**: `/reset` 엔드포인트는 `currentNumber`와 `lastIssuedNumber`를 0으로 초기화합니다.
3. **비활성화 vs 삭제**:
   - 비활성화: 데이터는 유지되지만 활성 목록에서 제외
   - 삭제: 물리적으로 데이터 삭제
4. **하위 호환성**: 기존 TicketService 기반 API(`/details`, `/state`, `/issuances`)도 계속 지원됩니다.

---

## 변경 이력

- **2025-11-28**: Room API v1.0 최초 작성
  - 방 CRUD API 추가
  - 일괄 생성 기능 추가
  - Swagger 문서 통합