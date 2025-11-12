# Database Diagrams

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
    }

    ROOM {
        Long id PK
        String roomUid UK "unique"
        String title
        Integer currentNumber
        LocalDateTime createdAt
    }

    TICKET_ISSUANCE {
        Long id PK
        Long room_id FK
        String userKey
        Integer number
        LocalDateTime issuedAt
    }
```

## ORM Class Diagram (객체 관계 다이어그램)

```mermaid
classDiagram
    class Member {
        -Long id
        -String username
        -String phoneNumber
        -List~Attend~ attends
        -Unit unit
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
        +Place(String name, String address, Double latitude, Double longitude)
    }

    class Room {
        -Long id
        -String roomUid
        -String title
        -Integer currentNumber
        -LocalDateTime createdAt
    }

    class TicketIssuance {
        -Long id
        -Room room
        -String userKey
        -Integer number
        -LocalDateTime issuedAt
        +TicketIssuance(Room room, String userKey, Integer number)
    }

    Member "1" --> "0..*" Attend : has
    Member "1" *-- "0..1" Unit : embeds
    Schedule "1" --> "0..*" Attend : has
    Place "1" --> "0..*" Schedule : has
    Room "1" --> "0..*" TicketIssuance : has
```

## Detailed Relationships

### 출석 관리 시스템
- **Member ↔ Unit**: Member는 Unit(사업단) 정보를 임베디드 타입으로 포함 (Embedded)
  - Unit은 독립적인 엔티티가 아닌 값 타입
  - Member 테이블에 unit_name, unit_type 컬럼으로 저장
- **Member ↔ Attend**: Member는 여러 출석 기록을 가질 수 있음 (OneToMany)
- **Schedule ↔ Attend**: Schedule은 여러 출석 기록을 가질 수 있음 (OneToMany)
- **Place ↔ Schedule**: Place는 여러 일정을 가질 수 있음 (OneToMany)

### 번호표 시스템
- **Room ↔ TicketIssuance**: Room은 여러 번호표 발급 기록을 가질 수 있음 (OneToMany)
- TicketIssuance는 room_id와 user_key, room_id와 number에 unique constraint가 있음

## Database Constraints

### TICKET_ISSUANCE
- **ux_room_user**: UNIQUE(room_id, user_key) - 한 방에서 한 사용자는 하나의 번호표만 발급
- **ux_room_number**: UNIQUE(room_id, number) - 한 방에서 같은 번호는 중복 불가
- **fk_ti_room**: FOREIGN KEY(room_id) REFERENCES rooms(id)

### ROOM
- **room_uid**: UNIQUE, 길이 16자, NOT NULL - 방 고유 식별자

## System Architecture Diagram

```mermaid
graph TB
    subgraph "Frontend - React"
        A[MemberXlsPage<br/>회원 Excel 업로드]
        B[ExcelMapPage<br/>장소 Excel 업로드]
        C[RoomPage<br/>번호표 시스템]
        D[AdminPage<br/>방 관리]
    end

    subgraph "Backend - Spring Boot"
        E[MemberController]
        F[MapController]
        G[PlaceController]
        H[WebSocketController]
        I[MemberService]
        J[ExcelService]
        K[TicketService]
    end

    subgraph "Database - JPA Entities"
        L[(Member<br/>+ Unit Embedded)]
        N[(Attend)]
        O[(Schedule)]
        P[(Place)]
        Q[(Room)]
        R[(TicketIssuance)]
    end

    A -->|Upload Excel| E
    E -->|Parse| J
    E -->|Save| I
    I -->|Persist| L

    B -->|Upload Excel| F
    F -->|Parse| J
    F -->|Geocode| G
    G -->|Save| P

    C -->|WebSocket| H
    D -->|WebSocket| H
    H -->|Manage| K
    K -->|Issue Ticket| R
    K -->|Update| Q

    L -.->|OneToMany| N
    N -.->|ManyToOne| O
    O -.->|ManyToOne| P
    Q -.->|OneToMany| R
```

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
        Long id PK "Primary Key"
        String name "장소명(사업단명)"
        String address "주소"
        Double latitude "위도"
        Double longitude "경도"
    }

    ROOM {
        Long id PK "Primary Key"
        String roomUid UK "방 고유ID (16자)"
        String title "방 제목"
        Integer currentNumber "현재 번호"
        LocalDateTime createdAt "생성일시"
    }

    TICKET_ISSUANCE {
        Long id PK "Primary Key"
        Long room_id FK "방 외래키"
        String userKey "사용자 키 (128자)"
        Integer number "발급 번호"
        LocalDateTime issuedAt "발급일시"
    }
```

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
    ExcelSvc-->>Controller: List<memberExcelData>
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

### 번호표 발급 플로우

```mermaid
sequenceDiagram
    actor User
    participant Frontend as RoomPage
    participant WS as WebSocket
    participant Controller as WebSocketController
    participant TicketSvc as TicketService
    participant DB as Database

    User->>Frontend: 방 입장
    Frontend->>WS: Connect & Join Room
    WS->>Controller: handleJoinRoom
    Controller->>TicketSvc: joinRoom(roomUid, userKey)

    alt User already has ticket
        TicketSvc->>DB: findByRoomAndUserKey
        DB-->>TicketSvc: TicketIssuance
        TicketSvc-->>Controller: ticket
    else New user
        TicketSvc->>DB: Issue new ticket
        DB-->>TicketSvc: TicketIssuance
        TicketSvc-->>Controller: new ticket
    end

    Controller->>WS: Broadcast room state
    WS->>Frontend: Update UI
    Frontend->>User: 번호표 표시
```