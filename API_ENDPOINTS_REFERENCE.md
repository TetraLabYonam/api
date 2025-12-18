# API 엔드포인트 레퍼런스

본 문서는 번호표 시스템의 모든 REST API 및 WebSocket 엔드포인트를 정리한 레퍼런스입니다.

---

## 목차

1. [번호표 시스템 API](#1-번호표-시스템-api)
   - [QueueController](#queuecontroller---사용자-api)
   - [RoomController](#roomcontroller---관리자-api)
   - [WebSocketController](#websocketcontroller---실시간-통신)
2. [출석 관리 API](#2-출석-관리-api)
3. [회원 관리 API](#3-회원-관리-api)
4. [장소 관리 API](#4-장소-관리-api)
5. [일정 관리 API](#5-일정-관리-api)
6. [지도 및 Excel API](#6-지도-및-excel-api)
7. [기타 API](#7-기타-api)

---

## 1. 번호표 시스템 API

### QueueController - 사용자 API

**Base Path**: `/api/queue`

Flutter/React 프론트엔드에서 주로 사용하는 사용자 대상 API입니다.

| HTTP | 엔드포인트 | 설명 | Request Body | Response |
|------|-----------|------|--------------|----------|
| **GET** | `/rooms` | 활성화된 방 목록 조회 | - | `List<QueueRoomDto>` |
| **GET** | `/room/{roomId}` | 특정 방 정보 조회 | - | `QueueRoomDto` |
| **POST** | `/room/{roomId}/ticket` | 번호표 발급 | `TicketIssueRequest` | `TicketIssueResponse` |
| **DELETE** | `/ticket/{ticketId}` | 번호표 취소 | - | `String` |
| **GET** | `/room/{roomId}/status` | 방 현황 조회 | - | `RoomStatusResponse` |
| **GET** | `/room/{roomId}/tickets` | 방의 모든 번호표 조회 | - | `List<QueueTicketDto>` |
| **POST** | `/room/{roomId}/call` | 다음 번호 호출 (관리자) | - | `Integer` |

#### 주요 DTO

**TicketIssueRequest**
```json
{
  "userDeviceId": "string"
}
```

**TicketIssueResponse**
```json
{
  "number": 1,
  "duplicated": false,
  "lastNumber": 1,
  "count": 1
}
```

**RoomStatusResponse**
```json
{
  "roomName": "캡스톤 경진대회",
  "currentNumber": 0,
  "lastIssuedNumber": 1,
  "waitingCount": 1
}
```

---

### RoomController - 관리자 API

**Base Path**: `/api/v1/rooms`

방 생성, 수정, 삭제 등 관리자 기능을 제공합니다.

#### 조회 API

| HTTP | 엔드포인트 | 설명 | Response |
|------|-----------|------|----------|
| **GET** | `/` | 활성화된 방 목록 조회 | `List<QueueRoomDto>` |
| **GET** | `/all` | 전체 방 목록 조회 (비활성 포함) | `List<QueueRoomDto>` |
| **GET** | `/details` | 방 상세 목록 조회 | `List<Map<String, Object>>` |
| **GET** | `/{id}` | 방 ID로 조회 | `QueueRoomDto` |
| **GET** | `/uid/{roomUid}` | 방 UID로 조회 | `QueueRoomDto` |
| **GET** | `/{uid}/state` | 방 상태 조회 | `Map<String, Object>` |
| **GET** | `/{uid}/issuances` | 방 발급 내역 조회 | `List<Map<String, Object>>` |

#### 생성 API

| HTTP | 엔드포인트 | 설명 | Request Body | Response |
|------|-----------|------|--------------|----------|
| **POST** | `/` | 새 방 생성 | `RoomCreateRequest` | `QueueRoomDto` |
| **POST** | `/batch` | 여러 방 일괄 생성 | `{ "roomNames": ["방1", "방2"] }` | `List<QueueRoomDto>` |

**RoomCreateRequest**
```json
{
  "roomName": "캡스톤 경진대회"
}
```

#### 수정 API

| HTTP | 엔드포인트 | 설명 | Request Body | Response |
|------|-----------|------|--------------|----------|
| **PUT** | `/{id}` | 방 정보 수정 | `RoomUpdateRequest` | `QueueRoomDto` |
| **POST** | `/{uid}/reset` | 방 번호 초기화 | - | `{ "success": "true", "message": "..." }` |
| **PUT** | `/{id}/activate` | 방 활성화 | - | `{ "message": "..." }` |
| **PUT** | `/{id}/deactivate` | 방 비활성화 | - | `{ "message": "..." }` |

#### 삭제 API

| HTTP | 엔드포인트 | 설명 | Response |
|------|-----------|------|----------|
| **DELETE** | `/{id}` | 방 삭제 (물리적 삭제) | `{ "message": "..." }` |

---

### WebSocketController - 실시간 통신

**Base Path**: WebSocket STOMP

실시간 양방향 통신을 위한 WebSocket 엔드포인트입니다.

#### 연결

- **엔드포인트**: `/ws`
- **프로토콜**: STOMP over SockJS
- **클라이언트 라이브러리**: `@stomp/stompjs`, `sockjs-client`

#### 메시지 전송 (클라이언트 → 서버)

| Destination | 설명 | Payload | 응답 토픽 |
|------------|------|---------|----------|
| `/app/room/join` | 방 입장 | `JoinRequest` | `/topic/room/{roomUid}/state` |
| `/app/room/issue` | 번호표 발급 | `IssueRequest` | `/user/queue/ticket` + `/topic/room/{roomUid}/state` |
| `/app/room/notify` | 알림 전송 (관리자) | `NotifyRequest` | `/topic/room/{roomUid}/notification/{userKey}` |

#### 구독 (서버 → 클라이언트)

| Topic | 설명 | Payload |
|-------|------|---------|
| `/topic/room/{roomUid}/state` | 방 상태 실시간 업데이트 | `{ currentNumber, lastNumber, count }` |
| `/user/queue/ticket` | 개인 티켓 수신 | `{ number, duplicated, lastNumber, count }` |
| `/topic/room/{roomUid}/notification/{userKey}` | 개인 알림 수신 | `{ number, message }` |
| `/queue/errors` | 에러 메시지 수신 | `{ error: "..." }` |

#### WebSocket DTO

**JoinRequest**
```json
{
  "roomUid": "92c41ecc-d25d-4e",
  "userKey": "device-123"
}
```

**IssueRequest**
```json
{
  "roomUid": "92c41ecc-d25d-4e",
  "userKey": "device-123"
}
```

**NotifyRequest**
```json
{
  "roomUid": "92c41ecc-d25d-4e",
  "number": 3,
  "message": "창구로 와주세요"
}
```

---

## 2. 출석 관리 API

**Base Path**: `/api/v1/attend`

**Controller**: `AttendController`

| HTTP | 엔드포인트 | 설명 | Request Body / Params | Response |
|------|-----------|------|-----------------------|----------|
| **POST** | `/check-in` | 출석 체크인 (위치 기반) | `AttendCheckInRequest` | `AttendCheckInResponse` |
| **PUT** | `/{attendId}/absent` | 결석 처리 | `?reason=사유(선택)` | `Void` |
| **PUT** | `/{attendId}/excused` | 사유 인정 결석 처리 | `?reason=사유(필수)` | `Void` |
| **PUT** | `/{attendId}/status` | 출석 상태 변경 (관리자) | `?status=PRESENT&note=비고` | `Void` |

### 주요 DTO

**AttendCheckInRequest**
```json
{
  "scheduleId": 1,
  "memberId": 1,
  "latitude": 35.123456,
  "longitude": 129.123456
}
```

**AttendCheckInResponse**
```json
{
  "attendId": 1,
  "status": "PRESENT",
  "distance": 45.2,
  "message": "출석 처리되었습니다."
}
```

---

## 3. 회원 관리 API

**Base Path**: `/api/v1/member`

**Controller**: `MemberController`

### 기본 CRUD

| HTTP | 엔드포인트 | 설명 | Request Body | Response |
|------|-----------|------|--------------|----------|
| **POST** | `/` | 회원 등록 | `Member` | `Member` |
| **GET** | `/` | 전체 회원 조회 | - | `List<Member>` |
| **GET** | `/{id}` | 단건 회원 조회 | - | `Member` |
| **PUT** | `/{id}` | 회원 정보 수정 | `UpdateMemberRequest` | `Member` |
| **DELETE** | `/{id}` | 회원 삭제 | - | `Void` |

### Excel 관련

| HTTP | 엔드포인트 | 설명 | Request | Response |
|------|-----------|------|---------|----------|
| **POST** | `/member-excel` | Excel 파일 파싱 | `MultipartFile` (파일) | `MemberExcelResponse` |
| **POST** | `/save-members` | Excel 데이터 일괄 저장 | `SaveMembersRequest` | `SaveMembersResponse` |

### 주요 DTO

**UpdateMemberRequest**
```json
{
  "username": "홍길동",
  "phoneNumber": "010-1234-5678"
}
```

**SaveMembersRequest**
```json
{
  "members": [
    {
      "username": "홍길동",
      "phoneNumber": "010-1234-5678",
      "placeName": "물금청소년문화의집"
    }
  ]
}
```

---

## 4. 장소 관리 API

**Base Path**: `/api/place`

**Controller**: `PlaceController`

| HTTP | 엔드포인트 | 설명 | Request Body | Response |
|------|-----------|------|--------------|----------|
| **POST** | `/save` | 단일 장소 저장 | `PlaceDto` | `String` |
| **POST** | `/save-all` | 여러 장소 일괄 저장 | `List<PlaceDto>` | `String` |
| **GET** | `/` | 장소 목록 조회 (간단) | - | `List<AddressDto>` |
| **GET** | `/list` | 장소 목록 조회 (상세) | - | `List<PlaceDto>` |

### 주요 DTO

**PlaceDto**
```json
{
  "business_unit": "물금청소년문화의집",
  "place_address": "경상남도 양산시...",
  "latitude": 35.123456,
  "longitude": 129.123456,
  "phone_number": "055-1234-5678",
  "image_url": "http://...",
  "description": "설명"
}
```

---

## 5. 일정 관리 API

**Base Path**: `/api/v1/schedule`

**Controller**: `ScheduleController`

### 생성 및 조회

| HTTP | 엔드포인트 | 설명 | Request / Params | Response |
|------|-----------|------|------------------|----------|
| **POST** | `/create` | 일정 생성 | `ScheduleCreateRequest` | `ScheduleCreateResponse` |
| **GET** | `/{scheduleId}` | 일정 상세 조회 | - | `ScheduleDetailResponse` |
| **GET** | `/member/{memberId}` | 회원별 일정 조회 | `?startDate=2025-01-01&endDate=2025-01-31` | `List<ScheduleDetailResponse>` |
| **GET** | `/date` | 날짜별 일정 조회 | `?date=2025-01-15` | `List<ScheduleDetailResponse>` |
| **GET** | `/range` | 날짜 범위 일정 조회 | `?startDate=2025-01-01&endDate=2025-01-31` | `List<ScheduleDetailResponse>` |

### 수정

| HTTP | 엔드포인트 | 설명 | Response |
|------|-----------|------|----------|
| **PUT** | `/{scheduleId}/activate` | 일정 활성화 | `Void` |
| **PUT** | `/{scheduleId}/deactivate` | 일정 비활성화 | `Void` |

### 주요 DTO

**ScheduleCreateRequest**
```json
{
  "placeId": 1,
  "title": "프로그래밍 수업",
  "dates": ["2025-01-15", "2025-01-22"],
  "startTime": "14:00",
  "endTime": "16:00",
  "memberIds": [1, 2, 3]
}
```

---

## 6. 지도 및 Excel API

**Controller**: `MapController`

### 뷰 렌더링 (Thymeleaf)

| HTTP | 엔드포인트 | 설명 | Response |
|------|-----------|------|----------|
| **GET** | `/map-excel` | Excel 업로드 페이지 | HTML 뷰 |
| **GET** | `/member` | 회원 관리 페이지 | HTML 뷰 |
| **GET** | `/schedule` | 일정 관리 페이지 | HTML 뷰 |

### REST API

| HTTP | 엔드포인트 | 설명 | Request | Response |
|------|-----------|------|---------|----------|
| **POST** | `/map-excel` | Excel 파일로 지도 데이터 생성 (뷰) | `MultipartFile` | HTML 뷰 |
| **POST** | `/api/map-excel` | Excel 파일로 지도 데이터 생성 (JSON) | `MultipartFile` | `MapResponse` |

### 주요 DTO

**MapResponse**
```json
{
  "locations": [
    {
      "businessUnit": "물금청소년문화의집",
      "address": "경상남도 양산시...",
      "lat": 35.123456,
      "lng": 129.123456
    }
  ]
}
```

---

## 7. 기타 API

### HelloController

**뷰 렌더링 전용**

| HTTP | 엔드포인트 | 설명 | Response |
|------|-----------|------|----------|
| **GET** | `/hello` | 테스트 페이지 | HTML 뷰 |

---

## API 인증 및 보안

### 현재 설정
- **인증**: 현재 인증 없음 (개발 환경)
- **CORS**: 모든 출처 허용 (`allowedOriginPatterns("*")`)
- **WebSocket CORS**: 모든 출처 허용

### 프로덕션 권장 사항
1. JWT 또는 세션 기반 인증 추가
2. CORS 출처 제한 (특정 도메인만 허용)
3. HTTPS 사용
4. Rate Limiting 적용

---

## 공통 응답 형식

### 성공 응답

**HTTP 200 OK**
```json
{
  "data": { ... }
}
```

**HTTP 201 CREATED** (생성)
```json
{
  "id": 1,
  "roomUid": "92c41ecc-d25d-4e",
  ...
}
```

### 에러 응답

**HTTP 400 Bad Request**
```json
{
  "error": "유효하지 않은 요청입니다."
}
```

**HTTP 404 Not Found**
```json
{
  "error": "리소스를 찾을 수 없습니다."
}
```

**HTTP 500 Internal Server Error**
```json
{
  "error": "서버 오류가 발생했습니다."
}
```

---

## 테스트 방법

### 1. Swagger UI

```
http://localhost:8080/swagger-ui/index.html
```

모든 REST API를 브라우저에서 직접 테스트할 수 있습니다.

### 2. curl

**방 목록 조회**
```bash
curl http://localhost:8080/api/queue/rooms
```

**방 생성**
```bash
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"roomName": "캡스톤 경진대회"}'
```

**번호표 발급**
```bash
curl -X POST http://localhost:8080/api/queue/room/{roomUid}/ticket \
  -H "Content-Type: application/json" \
  -d '{"userDeviceId": "device-123"}'
```

### 3. WebSocket 테스트 (브라우저 콘솔)

```javascript
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, (frame) => {
  console.log('Connected:', frame);

  // 방 상태 구독
  stompClient.subscribe('/topic/room/92c41ecc-d25d-4e/state', (message) => {
    console.log('방 상태 업데이트:', JSON.parse(message.body));
  });

  // 방 입장
  stompClient.send('/app/room/join', {}, JSON.stringify({
    roomUid: '92c41ecc-d25d-4e',
    userKey: 'device-123'
  }));
});
```

---

## 버전 정보

- **Spring Boot**: 3.5.6
- **Java**: 17
- **API 버전**: v1

---

## 관련 문서

- **플러터 통합**: `FLUTTER_INTEGRATION_GUIDE.md`
- **React 통합**: `REACT_QUEUE_SYSTEM_GUIDE.md`, `FRONTEND_INTEGRATION_PROMPT.md`
- **API 테스트 가이드**: `API_TESTING_GUIDE.md`
- **로컬 데모**: `LOCAL_DEMO_GUIDE.md`
- **백엔드 설정**: `BACKEND_CONFIG_CHECKLIST.md`

---

**마지막 업데이트**: 2025-12-18
