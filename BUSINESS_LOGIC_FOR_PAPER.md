# 출석 관리 및 번호표 시스템 - 핵심 비즈니스 로직

본 문서는 논문 작성을 위해 프로젝트의 핵심 비즈니스 로직을 간소화하여 정리한 문서입니다.

## 시스템 개요

출석 관리, 장소 기반 서비스, 실시간 번호표 시스템을 제공하는 웹 애플리케이션으로, Spring Boot와 React를 기반으로 구현되었습니다.

### 기술 스택
- **Backend**: Spring Boot 3.5.6, JPA/Hibernate, WebSocket (STOMP), Redis
- **Frontend**: React 19.1.1, Zustand, WebSocket
- **Database**: MariaDB
- **External API**: Google Maps Geocoding API

---

## 1. 실시간 번호표 발급 시스템

### 1.1 핵심 알고리즘: 동시성 제어를 통한 번호표 발급

번호표 시스템의 핵심은 **동시 접속 환경에서 중복 없이 순차적인 번호를 발급**하는 것입니다.

```java
@Service
@RequiredArgsConstructor
public class TicketService {

    private final RoomRepository roomRepository;
    private final TicketIssuanceRepository tiRepo;

    /**
     * 번호표 발급 - 비관적 락을 통한 동시성 제어
     * @param roomUid 방 고유 ID
     * @param userKey 사용자 고유 키 (디바이스 ID)
     * @return 발급된 번호 정보
     */
    @Transactional
    public Map<String, Object> issue(String roomUid, String userKey) {
        // 1) 비관적 락으로 방 잠금 (SELECT ... FOR UPDATE)
        Room room = roomRepository.findByRoomUidForUpdate(roomUid)
            .orElseThrow(() -> new NoSuchElementException("room not found"));

        // 2) 중복 발급 방지 - 이미 발급된 경우 기존 번호 반환
        Optional<TicketIssuance> prev = tiRepo.findByRoomIdAndUserKey(
            room.getId(), userKey
        );
        if (prev.isPresent()) {
            long count = tiRepo.countByRoomId(room.getId());
            return Map.of(
                "number", prev.get().getNumber(),
                "duplicated", true,
                "lastNumber", room.getCurrentNumber(),
                "count", count
            );
        }

        // 3) 새 번호 배정 및 증가
        int nextNumber = room.getCurrentNumber() + 1;
        room.setCurrentNumber(nextNumber);

        // 4) 번호표 발급 기록 저장
        try {
            tiRepo.save(new TicketIssuance(room, userKey, nextNumber));
            long count = tiRepo.countByRoomId(room.getId());
            return Map.of(
                "number", nextNumber,
                "duplicated", false,
                "lastNumber", nextNumber,
                "count", count
            );
        } catch (DataIntegrityViolationException e) {
            // 제약 조건 위반 시 재조회 후 기존 번호 반환
            TicketIssuance existing = tiRepo.findByRoomIdAndUserKey(
                room.getId(), userKey
            ).orElseThrow(() -> e);

            return Map.of(
                "number", existing.getNumber(),
                "duplicated", true
            );
        }
    }
}
```

**핵심 기술:**
- **비관적 락(Pessimistic Lock)**: `SELECT ... FOR UPDATE` 를 통해 트랜잭션 격리
- **유니크 제약조건**: DB 레벨에서 `(room_id, user_device_id)` 중복 방지
- **예외 처리**: 동시 요청 시 제약 위반 예외를 처리하여 안정성 확보

### 1.2 데이터 모델

```java
@Entity
@Table(name = "queue_room")
public class Room {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String roomUid;           // 방 고유 ID (UUID)

    private String title;             // 방 이름
    private Integer currentNumber;    // 현재 호출 번호
    private Integer lastIssuedNumber; // 마지막 발급 번호
    private Boolean isActive;         // 활성화 상태
}

@Entity
@Table(
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"room_id", "user_device_id"}),
        @UniqueConstraint(columnNames = {"room_id", "ticket_number"})
    }
)
public class TicketIssuance {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Room room;                // 발급 방

    private Integer number;           // 발급 번호
    private String userKey;           // 사용자 디바이스 ID
    private TicketStatus status;      // WAITING, CALLED, COMPLETED, CANCELLED
    private LocalDateTime issuedAt;   // 발급 시간
}
```

### 1.3 실시간 통신: WebSocket (STOMP)

```java
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final TicketService ticketService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 번호표 발급 요청 처리
     * 클라이언트 -> /app/room/issue
     */
    @MessageMapping("/room/issue")
    public void issueTicket(@Payload IssueRequest request,
                           SimpMessageHeaderAccessor headerAccessor) {

        // 번호표 발급
        Map<String, Object> result = ticketService.issue(
            request.roomUid(),
            request.userKey()
        );

        // 1) 발급받은 사용자에게 개인 메시지 전송
        messagingTemplate.convertAndSendToUser(
            headerAccessor.getSessionId(),
            "/queue/ticket",
            result
        );

        // 2) 방 전체에 상태 업데이트 브로드캐스트
        messagingTemplate.convertAndSend(
            "/topic/room/" + request.roomUid() + "/state",
            Map.of(
                "lastNumber", result.get("lastNumber"),
                "count", result.get("count")
            )
        );
    }
}
```

**실시간 통신 구조:**
- **STOMP over WebSocket**: 양방향 실시간 통신
- **개인 메시지**: `/user/queue/ticket` - 발급받은 사용자만 수신
- **브로드캐스트**: `/topic/room/{roomUid}/state` - 방의 모든 참여자에게 상태 전파

---

## 2. 위치 기반 출석 관리 시스템

### 2.1 핵심 알고리즘: Haversine 거리 계산

GPS 좌표를 이용하여 사용자의 현재 위치와 출석 장소 간의 거리를 계산합니다.

```java
@Service
@RequiredArgsConstructor
public class AttendService {

    @Value("${attendance.location.radius:100}")
    private int locationRadius; // 허용 반경 (미터)

    /**
     * 출석 체크인
     */
    @Transactional
    public AttendCheckInResponse checkIn(AttendCheckInRequest request) {
        // 1. 출석 정보 조회
        Attend attend = attendRepository.findByScheduleIdAndMemberId(
            request.getScheduleId(),
            request.getMemberId()
        ).orElseThrow(() -> new ResourceNotFoundException("출석 정보 없음"));

        // 2. 중복 출석 방지
        if (attend.isAttended()) {
            return AttendCheckInResponse.builder()
                .message("이미 출석 처리되었습니다.")
                .success(false)
                .build();
        }

        // 3. 위치 검증 - Haversine 거리 계산
        Schedule schedule = attend.getSchedule();
        Place place = schedule.getPlace();

        double distance = calculateDistance(
            request.getLatitude(), request.getLongitude(),
            place.getLatitude(), place.getLongitude()
        );

        if (distance > locationRadius) {
            throw new IllegalStateException(
                String.format("출석 가능한 위치가 아닙니다. (거리: %.1fm)", distance)
            );
        }

        // 4. 지각 여부 판단
        boolean isLate = isLate(schedule);

        // 5. 출석 처리
        if (isLate) {
            attend.markLate(request.getLatitude(), request.getLongitude(), "지각");
        } else {
            attend.markPresent(request.getLatitude(), request.getLongitude());
        }

        attendRepository.save(attend);

        return AttendCheckInResponse.builder()
            .status(attend.getStatus())
            .message(isLate ? "출석 처리 (지각)" : "출석 처리 완료")
            .distance(distance)
            .success(true)
            .build();
    }

    /**
     * Haversine 공식을 이용한 두 지점 간 거리 계산
     * @return 거리 (미터)
     */
    private double calculateDistance(Double lat1, Double lon1,
                                     Double lat2, Double lon2) {
        final int EARTH_RADIUS = 6371000; // 지구 반지름 (미터)

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2)
                * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c; // 미터 단위
    }

    /**
     * 지각 여부 판단
     */
    private boolean isLate(Schedule schedule) {
        if (schedule.getStartTime() == null) {
            return false;
        }
        LocalTime now = LocalTime.now();
        return now.isAfter(schedule.getStartTime());
    }
}
```

**핵심 알고리즘:**
- **Haversine Formula**: 구면 좌표계에서 두 지점 간 최단 거리 계산
- **위치 검증**: 허용 반경(기본 100m) 내에서만 출석 가능
- **지각 판정**: 일정 시작 시간 기준으로 자동 판정

### 2.2 출석 데이터 모델

```java
@Entity
public class Attend {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member member;            // 출석 대상 회원

    @ManyToOne(fetch = FetchType.LAZY)
    private Schedule schedule;        // 출석 일정

    @Enumerated(EnumType.STRING)
    private AttendStatus status;      // 출석 상태

    private Double latitude;          // 출석 위치 (위도)
    private Double longitude;         // 출석 위치 (경도)
    private LocalDateTime attendedAt; // 출석 시간
    private String note;              // 비고 (결석 사유 등)

    // 비즈니스 로직
    public void markPresent(Double lat, Double lon) {
        this.status = AttendStatus.PRESENT;
        this.latitude = lat;
        this.longitude = lon;
        this.attendedAt = LocalDateTime.now();
    }

    public void markLate(Double lat, Double lon, String reason) {
        this.status = AttendStatus.LATE;
        this.latitude = lat;
        this.longitude = lon;
        this.attendedAt = LocalDateTime.now();
        this.note = reason;
    }
}

// 출석 상태 열거형
public enum AttendStatus {
    SCHEDULED,  // 출석 예정
    PRESENT,    // 출석
    ABSENT,     // 결석
    LATE,       // 지각
    EXCUSED     // 사유 인정 결석
}
```

---

## 3. Excel 데이터 처리

### 3.1 Apache POI를 이용한 Excel 파싱

```java
@Service
public class ExcelService {

    /**
     * 회원 정보 Excel 파싱
     * @param file 업로드된 Excel 파일
     * @return 회원 데이터 리스트
     */
    public List<MemberExcelData> parseMemberFile(MultipartFile file)
            throws IOException {
        List<MemberExcelData> memberDataList = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);

            // 첫 번째 행(헤더) 제외하고 데이터 읽기
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null) {
                    String name = getCellValueAsString(row.getCell(0));
                    String phone = getCellValueAsString(row.getCell(1));
                    String unitName = getCellValueAsString(row.getCell(2));

                    if (unitName != null && !unitName.trim().isEmpty()) {
                        memberDataList.add(new MemberExcelData(
                            name.trim(),
                            phone.trim(),
                            unitName.trim()
                        ));
                    }
                }
            }
        }

        return memberDataList;
    }

    /**
     * 셀 값을 문자열로 변환
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // 숫자를 정수로 변환 (전화번호 등)
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return null;
        }
    }

    // DTO
    @AllArgsConstructor
    public static class MemberExcelData {
        private String memberName;
        private String phoneNumber;
        private String unitName;
    }
}
```

**핵심 기능:**
- **Apache POI**: Excel 파일(.xlsx) 읽기
- **타입 변환**: 숫자형 셀을 문자열로 변환 (전화번호 처리)
- **데이터 검증**: 필수 필드(사업단명) 누락 시 제외

---

## 4. 시스템 아키텍처

### 4.1 전체 구조

```
┌─────────────────────────────────────────────────────────────┐
│                      Frontend (React)                        │
│  - React Router 7: 라우팅                                     │
│  - Zustand: 상태 관리                                         │
│  - STOMP over WebSocket: 실시간 통신                          │
│  - Axios: HTTP 통신                                           │
└───────────────────┬─────────────────────────────────────────┘
                    │ HTTP / WebSocket
┌───────────────────┴─────────────────────────────────────────┐
│              Backend (Spring Boot)                           │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ Controller Layer                                     │    │
│  │  - REST API (@RestController)                       │    │
│  │  - WebSocket (@MessageMapping)                      │    │
│  └────────────────┬────────────────────────────────────┘    │
│                   │                                          │
│  ┌────────────────┴────────────────────────────────────┐    │
│  │ Service Layer (비즈니스 로직)                        │    │
│  │  - TicketService: 번호표 발급, 동시성 제어          │    │
│  │  - AttendService: 출석 관리, 위치 검증              │    │
│  │  - ExcelService: Excel 파싱                         │    │
│  │  - RoomService: 방 관리                             │    │
│  │  - WebSocketService: 실시간 메시지 전송             │    │
│  └────────────────┬────────────────────────────────────┘    │
│                   │                                          │
│  ┌────────────────┴────────────────────────────────────┐    │
│  │ Repository Layer (JPA)                              │    │
│  │  - Spring Data JPA                                  │    │
│  │  - Query Methods                                    │    │
│  │  - Custom Queries (@Query)                          │    │
│  └────────────────┬────────────────────────────────────┘    │
└───────────────────┼─────────────────────────────────────────┘
                    │
         ┌──────────┴──────────┐
         │                     │
    ┌────▼────┐          ┌────▼────┐
    │ MariaDB │          │  Redis  │
    │  (주DB)  │          │ (캐시)   │
    └─────────┘          └─────────┘
```

### 4.2 주요 기술적 특징

1. **동시성 제어**
   - 비관적 락 (Pessimistic Lock): 번호표 발급 시 데이터 무결성 보장
   - DB 제약 조건: 중복 방지를 위한 UNIQUE 제약

2. **실시간 통신**
   - STOMP over WebSocket: 표준 메시징 프로토콜
   - SimpMessagingTemplate: 개인/그룹 메시지 전송

3. **위치 기반 서비스**
   - Haversine Formula: GPS 좌표 간 거리 계산
   - 반경 기반 검증: 100m 내 출석 허용

4. **데이터 처리**
   - Apache POI: Excel 파일 파싱
   - 일괄 처리 (Batch): `saveAll()` 을 통한 성능 최적화

---

## 5. 핵심 알고리즘 요약

### 5.1 번호표 발급 알고리즘

```
입력: roomUid (방 ID), userKey (사용자 디바이스 ID)
출력: 발급된 번호 정보

1. BEGIN TRANSACTION
2. 비관적 락으로 Room 조회 (SELECT ... FOR UPDATE)
3. IF 사용자가 이미 번호표를 발급받았으면:
      기존 번호 반환 (duplicated = true)
4. ELSE:
      currentNumber += 1
      새 TicketIssuance 저장
      번호 반환 (duplicated = false)
5. COMMIT TRANSACTION
6. WebSocket으로 전체 사용자에게 상태 브로드캐스트
```

### 5.2 위치 기반 출석 알고리즘

```
입력: scheduleId, memberId, latitude, longitude
출력: 출석 처리 결과

1. Attend 조회 (scheduleId, memberId)
2. IF 이미 출석 처리됨:
      에러 반환
3. Place 좌표 조회
4. distance = Haversine(사용자 위치, 장소 위치)
5. IF distance > 허용반경:
      에러 발생 ("출석 가능한 위치 아님")
6. IF 현재 시간 > 일정 시작 시간:
      지각 처리 (LATE)
   ELSE:
      출석 처리 (PRESENT)
7. Attend 저장
8. 결과 반환
```

---

## 6. 성능 최적화 및 확장성

### 6.1 데이터베이스 최적화

- **인덱싱**: `roomUid`, `(room_id, user_device_id)` 복합 인덱스
- **지연 로딩**: `@ManyToOne(fetch = FetchType.LAZY)` 사용
- **쿼리 최적화**: N+1 문제 방지를 위한 Fetch Join

### 6.2 동시성 처리

- **비관적 락**: 높은 경합 상황에서 데이터 무결성 보장
- **트랜잭션 격리 수준**: READ_COMMITTED
- **예외 처리**: DataIntegrityViolationException 처리로 안정성 확보

### 6.3 실시간 통신

- **브로드캐스트 최적화**: 방별 토픽 분리 (`/topic/room/{roomUid}`)
- **개인 메시지**: 사용자별 큐 (`/user/queue/ticket`)
- **연결 관리**: SockJS fallback으로 브라우저 호환성 확보

---

## 결론

본 시스템은 다음과 같은 핵심 기술을 통해 안정적이고 실시간성이 보장되는 출석 관리 및 번호표 서비스를 제공합니다:

1. **비관적 락을 통한 동시성 제어**: 동시 접속 환경에서 번호 중복 방지
2. **Haversine 거리 계산**: 정확한 위치 기반 출석 검증
3. **STOMP over WebSocket**: 실시간 양방향 통신
4. **Apache POI**: Excel 기반 대량 데이터 처리

이러한 기술들은 노인 일자리 사업 참여자 관리라는 실무적 요구사항을 효과적으로 해결하며, 확장 가능한 아키텍처로 설계되어 향후 기능 추가에도 유연하게 대응할 수 있습니다.
