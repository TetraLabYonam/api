# Queue 도메인 재구성 문서

## 개요
QUEUE_SYSTEM_PROMPT.md의 백엔드 API 명세와 데이터베이스 스키마에 맞게 기존 Room 도메인을 재구성했습니다.

---

## 변경된 엔티티

### 1. Room → QueueRoom
**파일**: `/src/main/java/com/example/attempt/domain/Room.java`

#### 변경 사항
| 항목 | 이전 | 변경 후 |
|------|------|---------|
| 테이블명 | `rooms` | `queue_room` |
| 컬럼명 (title) | `title` | `room_name` |

#### 추가된 필드
```java
@Column(name="is_active", nullable=false)
private Boolean isActive = true;  // 방 활성화 여부

@Column(name="last_issued_number", nullable=false)
private Integer lastIssuedNumber = 0;  // 마지막으로 발급된 번호

@Column(name="updated_at", nullable=false)
private LocalDateTime updatedAt = LocalDateTime.now();  // 업데이트 시간
```

#### 추가 기능
- `@PreUpdate` 메소드로 updatedAt 자동 갱신

---

### 2. TicketIssuance → QueueTicket
**파일**: `/src/main/java/com/example/attempt/domain/TicketIssuance.java`

#### 변경 사항
| 항목 | 이전 | 변경 후 |
|------|------|---------|
| 테이블명 | `ticket_issuances` | `queue_ticket` |
| 컬럼명 (number) | `number` | `ticket_number` |
| 컬럼명 (userKey) | `user_key` | `user_device_id` |

#### 추가된 필드
```java
@Enumerated(EnumType.STRING)
@Column(length=20, nullable=false)
private TicketStatus status = TicketStatus.WAITING;  // 티켓 상태

@Column(name="called_at")
private LocalDateTime calledAt;  // 호출된 시간
```

#### 추가된 Enum
```java
public enum TicketStatus {
    WAITING,    // 대기 중
    CALLED,     // 호출됨
    COMPLETED,  // 완료
    CANCELLED   // 취소됨
}
```

---

### 3. Admin (신규 생성)
**파일**: `/src/main/java/com/example/attempt/domain/Admin.java`

#### 스키마
```java
@Entity
@Table(name = "admin")
public class Admin {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50, unique = true, nullable = false)
    private String username;  // 관리자 아이디

    @Column(length = 255, nullable = false)
    private String password;  // BCrypt 암호화된 패스워드

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

---

## 변경된 Repository

### 1. RoomRepository
**파일**: `/src/main/java/com/example/attempt/repository/RoomRepository.java`

#### 추가된 메소드
```java
// 활성화된 방만 조회
List<Room> findByIsActiveTrue();

// 활성화된 방 전체 조회 (생성일 내림차순)
List<Room> findByIsActiveTrueOrderByCreatedAtDesc();
```

---

### 2. TicketIssuanceRepository
**파일**: `/src/main/java/com/example/attempt/repository/TicketIssuanceRepository.java`

#### 추가된 메소드
```java
// Status별 티켓 조회
List<TicketIssuance> findByRoomIdAndStatus(Long roomId, TicketIssuance.TicketStatus status);

// Status별 티켓 개수
long countByRoomIdAndStatus(Long roomId, TicketIssuance.TicketStatus status);

// 대기 중인 티켓만 번호순 조회
List<TicketIssuance> findByRoomIdAndStatusOrderByNumberAsc(
    Long roomId,
    TicketIssuance.TicketStatus status
);
```

---

### 3. AdminRepository (신규 생성)
**파일**: `/src/main/java/com/example/attempt/repository/AdminRepository.java`

```java
public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByUsername(String username);
    boolean existsByUsername(String username);
}
```

---

## 데이터베이스 마이그레이션

### 기존 데이터 마이그레이션 SQL

```sql
-- 1. rooms 테이블을 queue_room으로 이름 변경
RENAME TABLE rooms TO queue_room;

-- 2. queue_room에 새 컬럼 추가
ALTER TABLE queue_room
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN last_issued_number INT NOT NULL DEFAULT 0,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CHANGE COLUMN title room_name VARCHAR(100);

-- 3. ticket_issuances 테이블을 queue_ticket으로 이름 변경
RENAME TABLE ticket_issuances TO queue_ticket;

-- 4. queue_ticket에 새 컬럼 추가 및 컬럼명 변경
ALTER TABLE queue_ticket
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    ADD COLUMN called_at TIMESTAMP NULL,
    CHANGE COLUMN number ticket_number INT NOT NULL,
    CHANGE COLUMN user_key user_device_id VARCHAR(255);

-- 5. Admin 테이블 생성
CREATE TABLE admin (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 6. 기본 관리자 계정 추가 (비밀번호: admin1234, BCrypt)
-- 실제 사용 시에는 BCrypt로 암호화된 패스워드를 사용해야 합니다
INSERT INTO admin (username, password)
VALUES ('admin', '$2a$10$N9qo8u.u9s5/2RV/xNbYxe5XvXzZ7n8bnY8VHKV8rGLO.gY6YZKxy');
```

### 새로 시작하는 경우 (Fresh Install)

```sql
-- JPA가 자동으로 생성하므로 별도 SQL 불필요
-- application.properties에서 설정:
spring.jpa.hibernate.ddl-auto=update
```

---

## 호환성 유지

### 기존 코드와의 호환성
기존 TicketService는 대부분 그대로 동작합니다:

- `userKey` 필드명은 유지 (컬럼명만 `user_device_id`로 변경)
- `number` 필드명은 유지 (컬럼명만 `ticket_number`로 변경)
- `title` 필드명은 유지 (컬럼명만 `room_name`으로 변경)

### 주의사항
1. **TicketService 수정 필요**:
   - 티켓 발급 시 `status = WAITING` 설정 (이미 기본값으로 설정됨)
   - 번호 호출 시 `status = CALLED`, `calledAt` 설정 필요
   - 방 생성 시 `isActive = true` 설정 (이미 기본값으로 설정됨)

2. **데이터베이스 마이그레이션**:
   - 운영 환경에서는 반드시 백업 후 마이그레이션 진행
   - 테이블명 변경으로 인한 외래키 제약조건 확인 필요

---

## API 명세와의 매핑

### REST API 엔드포인트 (구현 필요)

| 엔드포인트 | 메소드 | 설명 | 상태 |
|-----------|--------|------|------|
| `/api/admin/login` | POST | 관리자 로그인 | ❌ 미구현 |
| `/api/queue/room` | POST | 방 생성 | ✅ 기존 존재 (수정 필요) |
| `/api/queue/rooms` | GET | 방 목록 조회 | ✅ 기존 존재 |
| `/api/queue/room/{id}` | GET | 방 상세 조회 | ❌ 미구현 |
| `/api/queue/room/{id}/ticket` | POST | 번호표 발급 | ✅ 기존 존재 |
| `/api/queue/ticket/{id}` | DELETE | 번호표 취소 | ❌ 미구현 |
| `/api/queue/room/{id}/call` | POST | 번호 호출 | ❌ 미구현 |
| `/api/queue/room/{id}/status` | GET | 방 현황 조회 | ✅ 기존 존재 |

### WebSocket 엔드포인트 (구현 필요)

| 엔드포인트 | 타입 | 설명 | 상태 |
|-----------|------|------|------|
| `/topic/queue/{roomId}` | SUBSCRIBE | 방 상태 구독 | ❌ 미구현 |
| `/app/queue/call` | SEND | 번호 호출 | ❌ 미구현 |
| `/app/queue/update` | SEND | 상태 업데이트 | ❌ 미구현 |

---

## 다음 단계

### Phase 1: 백엔드 API 구현
1. ✅ 도메인 재구성 완료
2. ⬜ AdminService 구현 (로그인 로직)
3. ⬜ QueueService 수정 (status, called_at 처리)
4. ⬜ QueueController 구현 (REST API)
5. ⬜ WebSocket Handler 구현

### Phase 2: 테스트 및 검증
1. ⬜ 단위 테스트 작성
2. ⬜ 통합 테스트
3. ⬜ API 문서 작성 (Swagger)

### Phase 3: Flutter 앱 개발
1. ⬜ Flutter 프로젝트 생성
2. ⬜ 모델 클래스 작성
3. ⬜ API Service 구현
4. ⬜ WebSocket 연동
5. ⬜ UI 구현

---

## 문서 작성 정보
- **작성일**: 2025-11-16
- **프로젝트**: attempt
- **버전**: 1.0
- **관련 문서**: QUEUE_SYSTEM_PROMPT.md