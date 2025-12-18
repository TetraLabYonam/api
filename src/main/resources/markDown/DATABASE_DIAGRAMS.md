# Database Diagrams

> 마지막 업데이트: 2025-11-17
>
> 본 문서는 프로젝트의 데이터베이스 구조와 엔티티 관계를 시각화합니다.

## 📋 목차

1. [ER Diagram (Entity Relationship Diagram)](#er-diagram-entity-relationship-diagram)
2. [ORM Class Diagram](#orm-class-diagram-객체-관계-다이어그램)
3. [Detailed Entity Relationships](#detailed-entity-relationships-with-cardinality)
4. [Database Constraints](#database-constraints)
5. [System Architecture](#system-architecture-diagram)
6. [Business Logic Flow](#business-logic-flow)

---

## ER Diagram (Entity Relationship Diagram)

```mermaid
erDiagram
    MEMBER ||--o{ ATTEND : has
    SCHEDULE ||--o{ ATTEND : has
    PLACE ||--o{ SCHEDULE : has
    ROOM ||--o{ TICKET_ISSUANCE : has

    MEMBER {
        Long id PK
        String username
        String phoneNumber
        String unit_name "Embedded"
        String unit_type "Embedded"
    }

    ATTEND {
        Long id PK
        Long member_id FK
        Long schedule_id FK
        Double latitude
        Double longitude
    }

    SCHEDULE {
        Long id PK
        Long place_id FK
        Date attendDate
    }

    PLACE {
        Long id PK
        String name "unit_name"
        String address "place_address"
        Double latitude
        Double longitude
        String imageUrl
        String phoneNumber
        String description
    }

    ROOM {
        Long id PK
        String roomUid UK "unique"
        String title "room_name"
        Boolean isActive
        Integer currentNumber
        Integer lastIssuedNumber
        LocalDateTime createdAt
        LocalDateTime updatedAt
    }

    TICKET_ISSUANCE {
        Long id PK
        Long room_id FK
        String userKey "user_device_id"
        Integer number "ticket_number"
        String status "ENUM"
        LocalDateTime issuedAt
        LocalDateTime calledAt
    }

    ADMIN {
        Long id PK
        String username UK "unique"
        String password "BCrypt"
        LocalDateTime createdAt
    }
```

---

## ORM Class Diagram (객체 관계 다이어그램)

```mermaid
classDiagram
    class Member {
        -Long id
        -String username
        -String phoneNumber
        -List~Attend~ attends
        -Unit unit
        +Member(String username, String phoneNumber)
    }

    class Unit {
        <<Embeddable>>
        -String name
        -String type
        +Unit(String name)
    }

    class Attend {
        -Long id
        -Member member
        -Schedule schedule
        -Double latitude
        -Double longitude
        +Attend(Member member, Schedule schedule, Double latitude, Double longitude)
    }

    class Schedule {
        -Long id
        -List~Attend~ attends
        -Place place
        -Date AttendDate
    }

    class Place {
        -Long id
        -String name
        -String address
        -List~Schedule~ schedules
        -Double latitude
        -Double longitude
        -String imageUrl
        -String phoneNumber
        -String description
        +Place(String name, String address, Double latitude, Double longitude)
        +Place(String name, String address, Double latitude, Double longitude, String imageUrl, String phoneNumber, String description)
    }

    class Room {
        -Long id
        -String roomUid
        -String title
        -Boolean isActive
        -Integer currentNumber
        -Integer lastIssuedNumber
        -LocalDateTime createdAt
        -LocalDateTime updatedAt
        +onUpdate() void
    }

    class TicketIssuance {
        -Long id
        -Room room
        -String userKey
        -Integer number
        -TicketStatus status
        -LocalDateTime issuedAt
        -LocalDateTime calledAt
        +TicketIssuance(Room room, String userKey, Integer number)
    }

    class TicketStatus {
        <<enumeration>>
        WAITING
        CALLED
        COMPLETED
        CANCELLED
    }

    class Admin {
        -Long id
        -String username
        -String password
        -LocalDateTime createdAt
        +Admin(String username, String password)
    }

    Member "1" --> "0..*" Attend : has
    Member "1" *-- "0..1" Unit : embeds
    Schedule "1" --> "0..*" Attend : has
    Place "1" --> "0..*" Schedule : has
    Room "1" --> "0..*" TicketIssuance : has
    TicketIssuance --> TicketStatus : uses
```

---

## Detailed Relationships

### 출석 관리 시스템 (Attendance Management)

- **Member ↔ Unit**: Member는 Unit(사업단) 정보를 임베디드 타입으로 포함 (Embedded)
  - Unit은 독립적인 엔티티가 아닌 값 타입 (Value Type)
  - Member 테이블에 `unit_name`, `unit_type` 컬럼으로 저장
  - 데이터 중복 최소화 및 도메인 모델 명확화

- **Member ↔ Attend**: Member는 여러 출석 기록을 가질 수 있음 (OneToMany)
  - 양방향 관계, Attend가 연관관계의 주인
  - LAZY 로딩으로 성능 최적화

- **Schedule ↔ Attend**: Schedule은 여러 출석 기록을 가질 수 있음 (OneToMany)
  - 일정별 출석자 관리
  - 출석 위치 정보 포함 (latitude, longitude)

- **Place ↔ Schedule**: Place는 여러 일정을 가질 수 있음 (OneToMany)
  - 장소별 일정 관리
  - Place에는 상세 정보 포함 (주소, 좌표, 이미지, 전화번호, 설명)

### 번호표 시스템 (Queue Ticket System)

- **Room ↔ TicketIssuance**: Room은 여러 번호표 발급 기록을 가질 수 있음 (OneToMany)
  - 방별 번호표 관리
  - 중복 발급 방지를 위한 UNIQUE 제약조건
  - 티켓 상태 관리 (WAITING, CALLED, COMPLETED, CANCELLED)

- **TicketIssuance Status Flow**:
  ```
  WAITING → CALLED → COMPLETED
            ↓
        CANCELLED (언제든지 가능)
  ```

### 관리자 시스템 (Admin System)

- **Admin**: 독립적인 엔티티
  - 시스템 관리자 계정 관리
  - BCrypt 암호화된 비밀번호 저장
  - username은 UNIQUE 제약조건

---

## Database Constraints

### MEMBER
- **id**: Primary Key, Auto-increment
- **username**: NOT NULL
- **phoneNumber**: 회원 연락처
- **unit_name, unit_type**: Embedded Unit 정보

### ATTEND
- **id**: Primary Key, Auto-increment
- **member_id**: Foreign Key → MEMBER(id)
- **schedule_id**: Foreign Key → SCHEDULE(id)
- **latitude, longitude**: 출석 위치 좌표

### SCHEDULE
- **id**: Primary Key, Auto-increment
- **place_id**: Foreign Key → PLACE(id)
- **AttendDate**: 출석 일자

### PLACE
- **id**: Primary Key (PLACE_ID), Auto-increment
- **name**: 장소명 (unit_name)
- **address**: 주소 (place_address)
- **latitude, longitude**: 장소 좌표
- **imageUrl**: 장소 이미지 URL
- **phoneNumber**: 전화번호 (phone_number)
- **description**: 장소 설명 (최대 1000자)

### ROOM
- **id**: Primary Key, Auto-increment
- **roomUid**: UNIQUE, 길이 16자, NOT NULL - 방 고유 식별자
- **title**: 방 제목 (room_name)
- **isActive**: 활성화 상태, NOT NULL, 기본값 true
- **currentNumber**: 현재 호출 번호, NOT NULL, 기본값 0
- **lastIssuedNumber**: 마지막 발급 번호, NOT NULL, 기본값 0
- **createdAt**: 생성일시, NOT NULL
- **updatedAt**: 수정일시, NOT NULL, @PreUpdate로 자동 갱신

### TICKET_ISSUANCE
- **id**: Primary Key, Auto-increment
- **room_id**: Foreign Key → ROOM(id), NOT NULL
  - **fk_ticket_room**: Foreign Key Constraint
- **userKey**: 사용자 디바이스 ID (user_device_id), 최대 255자
- **number**: 발급 번호 (ticket_number), NOT NULL
- **status**: 티켓 상태 (ENUM), NOT NULL, 기본값 WAITING
  - `WAITING`: 대기 중
  - `CALLED`: 호출됨
  - `COMPLETED`: 완료
  - `CANCELLED`: 취소됨
- **issuedAt**: 발급일시, NOT NULL
- **calledAt**: 호출일시 (nullable)

#### Unique Constraints
- **ux_room_device**: UNIQUE(room_id, user_device_id)
  - 한 방에서 한 사용자는 하나의 번호표만 발급 가능
- **ux_room_number**: UNIQUE(room_id, ticket_number)
  - 한 방에서 같은 번호는 중복 불가

### ADMIN
- **id**: Primary Key, Auto-increment
- **username**: UNIQUE, 최대 50자, NOT NULL
- **password**: BCrypt 암호화, 최대 255자, NOT NULL
- **createdAt**: 생성일시, NOT NULL, updatable=false

---

## System Architecture Diagram

```mermaid
graph TB
    subgraph "Frontend - React / Flutter"
        A[MemberXlsPage<br/>회원 Excel 업로드]
        B[ExcelMapPage<br/>장소 Excel 업로드]
        C[RoomPage<br/>번호표 시스템]
        D[AdminPage<br/>방 관리]
        E[FlutterApp<br/>모바일 앱]
    end

    subgraph "Backend - Spring Boot REST API"
        F[MemberController]
        G[MapController]
        H[PlaceController<br/>/api/place/*]
        I[QueueController<br/>/api/queue/*]
        J[WebSocketController]
        K[MemberService]
        L[ExcelService]
        M[TicketService]
        N[QueueService]
        O[PlaceService]
    end

    subgraph "Database - JPA Entities"
        P[(Member<br/>+ Unit Embedded)]
        Q[(Attend)]
        R[(Schedule)]
        S[(Place)]
        T[(Room)]
        U[(TicketIssuance)]
        V[(Admin)]
    end

    subgraph "Real-time Communication"
        W[WebSocket<br/>STOMP]
    end

    A -->|Upload Excel| F
    F -->|Parse| L
    F -->|Save| K
    K -->|Persist| P

    B -->|Upload Excel| G
    G -->|Parse| L
    G -->|Geocode| H
    H -->|Save via| O
    O -->|Persist| S

    E -->|REST API| H
    E -->|REST API| I
    E -->|WebSocket| W

    C -->|WebSocket| J
    D -->|WebSocket| J
    J -->|Manage| M
    M -->|Issue Ticket| U
    M -->|Update| T

    I -->|Business Logic| N
    N -->|Manage| U
    N -->|Update| T
    N -->|Broadcast| W

    W -.->|Real-time Update| C
    W -.->|Real-time Update| D
    W -.->|Real-time Update| E

    P -.->|OneToMany| Q
    Q -.->|ManyToOne| R
    R -.->|ManyToOne| S
    T -.->|OneToMany| U
```

---

## Detailed Entity Relationships with Cardinality

```mermaid
erDiagram
    MEMBER ||--o{ ATTEND : "has many"
    ATTEND }o--|| SCHEDULE : "for"
    SCHEDULE }o--|| PLACE : "at"
    ROOM ||--o{ TICKET_ISSUANCE : "issues"

    MEMBER {
        Long id PK "Primary Key"
        String username "회원 이름"
        String phoneNumber "전화번호"
        String unit_name "사업단명 (Embedded)"
        String unit_type "사업단 타입 (Embedded)"
    }

    ATTEND {
        Long id PK "Primary Key"
        Long member_id FK "회원 외래키"
        Long schedule_id FK "일정 외래키"
        Double latitude "출석 위치 위도"
        Double longitude "출석 위치 경도"
    }

    SCHEDULE {
        Long id PK "Primary Key"
        Long place_id FK "장소 외래키"
        Date attendDate "출석 일자"
    }

    PLACE {
        Long id PK "Primary Key (PLACE_ID)"
        String name "장소명(사업단명)"
        String address "주소 (place_address)"
        Double latitude "위도"
        Double longitude "경도"
        String imageUrl "이미지 URL (image_url)"
        String phoneNumber "전화번호 (phone_number)"
        String description "설명 (최대 1000자)"
    }

    ROOM {
        Long id PK "Primary Key"
        String roomUid UK "방 고유ID (16자, UNIQUE)"
        String title "방 제목 (room_name)"
        Boolean isActive "활성화 상태"
        Integer currentNumber "현재 호출 번호"
        Integer lastIssuedNumber "마지막 발급 번호"
        LocalDateTime createdAt "생성일시"
        LocalDateTime updatedAt "수정일시"
    }

    TICKET_ISSUANCE {
        Long id PK "Primary Key"
        Long room_id FK "방 외래키"
        String userKey "사용자 디바이스 ID (255자)"
        Integer number "발급 번호 (ticket_number)"
        String status "상태 (WAITING/CALLED/COMPLETED/CANCELLED)"
        LocalDateTime issuedAt "발급일시"
        LocalDateTime calledAt "호출일시 (nullable)"
    }

    ADMIN {
        Long id PK "Primary Key"
        String username UK "관리자 ID (UNIQUE, 50자)"
        String password "비밀번호 (BCrypt, 255자)"
        LocalDateTime createdAt "생성일시"
    }
```

---

## Business Logic Flow

### 회원 Excel 업로드 및 저장 플로우

```mermaid
sequenceDiagram
    actor User
    participant Frontend as MemberXlsPage
    participant Controller as MemberController
    participant ExcelSvc as ExcelService
    participant MemberSvc as MemberService
    participant MemberRepo as MemberRepository
    participant DB as Database

    User->>Frontend: Excel 파일 업로드
    Frontend->>Controller: POST /member-excel (file)
    Controller->>ExcelSvc: parseMemberFile(file)
    ExcelSvc-->>Controller: List<MemberExcelData>
    Controller-->>Frontend: {members: [...]}
    Frontend->>Frontend: 데이터 테이블 표시

    User->>Frontend: "DB에 저장" 클릭
    Frontend->>Controller: POST /save-members (members)
    Controller->>MemberSvc: saveMembersFromExcel(members)

    loop For each member
        MemberSvc->>MemberSvc: Create Unit (Embedded)
        MemberSvc->>MemberSvc: Create Member with Unit
        MemberSvc->>MemberRepo: save(member)
        MemberRepo->>DB: INSERT (Member + Unit)
    end

    MemberSvc-->>Controller: savedCount
    Controller-->>Frontend: {savedCount, message}
    Frontend->>User: 성공 메시지 표시
```

### 번호표 발급 플로우 (Enhanced)

```mermaid
sequenceDiagram
    actor User
    participant App as Flutter App
    participant API as QueueController
    participant Service as QueueService
    participant Repo as TicketRepository
    participant WS as WebSocket
    participant DB as Database

    User->>App: 방 입장 및 번호표 요청
    App->>API: POST /api/queue/room/{roomId}/ticket
    API->>Service: issueTicket(roomId, deviceId)

    Service->>Repo: Lock Room (findByRoomUidForUpdate)
    Repo->>DB: SELECT FOR UPDATE
    DB-->>Repo: Room (locked)

    alt 이미 발급된 경우
        Service->>Repo: findByRoomIdAndUserKey
        DB-->>Service: Existing Ticket
        Service-->>API: {number, duplicated: true, ...}
    else 새로운 발급
        Service->>Service: Increment lastIssuedNumber
        Service->>Repo: save(new TicketIssuance)
        Repo->>DB: INSERT Ticket (status: WAITING)
        Service->>WS: Broadcast room update
        WS-->>App: Real-time status update
        Service-->>API: {number, duplicated: false, ...}
    end

    API-->>App: TicketIssueResponse
    App->>User: 번호표 표시
```

### 번호표 호출 플로우

```mermaid
sequenceDiagram
    actor Admin
    participant AdminApp as Admin Page
    participant API as QueueController
    participant Service as QueueService
    participant Repo as TicketRepository
    participant WS as WebSocket
    participant DB as Database
    actor User

    Admin->>AdminApp: 다음 번호 호출
    AdminApp->>API: POST /api/queue/room/{roomId}/call
    API->>Service: callNextNumber(roomId)

    Service->>Repo: Lock Room
    Service->>Service: currentNumber++
    Service->>Repo: Update Room
    Service->>Repo: Find Ticket by number

    alt 티켓이 존재하면
        Service->>Repo: Update Ticket Status to CALLED
        Service->>Repo: Set calledAt timestamp
        Repo->>DB: UPDATE status=CALLED
    end

    Service->>WS: Broadcast to /topic/room/{roomId}
    WS-->>AdminApp: Update current number
    WS-->>User: 호출 알림

    Service-->>API: Called number
    API-->>AdminApp: {calledNumber}
```

### Flutter 앱 API 통신 플로우

```mermaid
sequenceDiagram
    actor User
    participant Flutter as Flutter App
    participant PlaceAPI as PlaceController
    participant QueueAPI as QueueController
    participant PlaceSvc as PlaceService
    participant QueueSvc as QueueService
    participant DB as Database

    Note over User,DB: 일자리 정보 조회
    User->>Flutter: 앱 실행
    Flutter->>PlaceAPI: GET /api/place/list
    PlaceAPI->>PlaceSvc: getPlaceList()
    PlaceSvc->>DB: SELECT * FROM Place
    DB-->>PlaceSvc: List<Place>
    PlaceSvc-->>PlaceAPI: List<PlaceDto>
    PlaceAPI-->>Flutter: JSON Response
    Flutter->>User: 지도에 일자리 표시

    Note over User,DB: 대기열 입장
    User->>Flutter: 특정 장소 선택
    Flutter->>QueueAPI: GET /api/queue/rooms
    QueueAPI->>QueueSvc: getActiveRooms()
    QueueSvc->>DB: SELECT * FROM Room WHERE isActive=true
    DB-->>QueueSvc: List<Room>
    QueueSvc-->>QueueAPI: List<QueueRoomDto>
    QueueAPI-->>Flutter: JSON Response
    Flutter->>User: 활성 대기실 목록 표시

    Note over User,DB: 번호표 발급
    User->>Flutter: 대기실 입장
    Flutter->>QueueAPI: POST /api/queue/room/{id}/ticket
    QueueAPI->>QueueSvc: issueTicket(roomId, deviceId)
    QueueSvc->>DB: Transaction & Insert
    DB-->>QueueSvc: Ticket Created
    QueueSvc-->>QueueAPI: TicketIssueResponse
    QueueAPI-->>Flutter: {number, lastNumber, count}
    Flutter->>User: 번호표 화면 표시
```

---

## 테이블 생성 순서 (DDL)

데이터베이스 초기화 시 테이블 생성 순서:

1. **ADMIN** - 독립 엔티티
2. **MEMBER** (with embedded UNIT)
3. **PLACE** - 독립 엔티티
4. **SCHEDULE** - PLACE 참조
5. **ATTEND** - MEMBER, SCHEDULE 참조
6. **ROOM** - 독립 엔티티
7. **TICKET_ISSUANCE** - ROOM 참조

---

## 인덱스 최적화 권장사항

### 성능 향상을 위한 인덱스

```sql
-- ROOM
CREATE INDEX idx_room_uid ON queue_room(room_uid);
CREATE INDEX idx_room_active ON queue_room(is_active);

-- TICKET_ISSUANCE
CREATE INDEX idx_ticket_room_status ON queue_ticket(room_id, status);
CREATE INDEX idx_ticket_issued_at ON queue_ticket(issued_at);

-- PLACE
CREATE INDEX idx_place_coordinates ON place(latitude, longitude);

-- SCHEDULE
CREATE INDEX idx_schedule_date ON schedule(attend_date);
CREATE INDEX idx_schedule_place ON schedule(place_id);

-- ATTEND
CREATE INDEX idx_attend_member ON attend(member_id);
CREATE INDEX idx_attend_schedule ON attend(schedule_id);
```

---

## 변경 이력

### 2025-11-17
- Admin 엔티티 추가 (관리자 계정 관리)
- Room 엔티티에 `isActive`, `lastIssuedNumber`, `updatedAt` 필드 추가
- TicketIssuance 엔티티에 `status` (enum), `calledAt` 필드 추가
- Place 엔티티에 `imageUrl`, `phoneNumber`, `description` 필드 추가
- REST API 엔드포인트 다이어그램 추가
- Flutter 앱 통신 플로우 추가
- 인덱스 최적화 권장사항 추가

### 초기 버전
- Member, Attend, Schedule, Place, Room, TicketIssuance 엔티티 정의
- 기본 ER 다이어그램 및 관계 정의

---

**문서 관리자**: Development Team
**최종 검토**: 2025-11-17
