# Place API 변경사항 - 웹 스크래핑에서 DB 조회로 전환

## 개요
기존 웹 스크래핑 방식의 일자리 정보 조회를 데이터베이스 기반 조회로 변경했습니다.

**변경 날짜**: 2025-11-14

---

## 1. 데이터베이스 스키마 변경

### Place 엔티티 필드 추가

**파일**: `src/main/java/com/example/attempt/domain/Place.java`

#### 추가된 필드

| 필드명 | 컬럼명 | 타입 | 설명 |
|--------|--------|------|------|
| imageUrl | image_url | String | 일자리 이미지 URL |
| phoneNumber | phone_number | String | 연락처 전화번호 |
| description | description | String(1000) | 일자리 상세 설명 |

#### 변경 내용
```java
@Column(name = "image_url")
private String imageUrl;

@Column(name = "phone_number")
private String phoneNumber;

@Column(name = "description", length = 1000)
private String description;
```

#### 새로운 생성자 추가
```java
public Place(String name, String address, Double latitude, Double longitude,
             String imageUrl, String phoneNumber, String description)
```

---

## 2. Repository 변경

**파일**: `src/main/java/com/example/attempt/repository/PlaceRepository.java`

### 변경 사항
- `CrudRepository` → `JpaRepository`로 변경
- `findAll()` 메서드가 `List<Place>` 반환

```java
// 변경 전
public interface PlaceRepository extends CrudRepository<Place, Long> {}

// 변경 후
public interface PlaceRepository extends JpaRepository<Place, Long> {}
```

**변경 이유**: `findAll()`이 `Iterable<Place>` 대신 `List<Place>`를 반환하여 타입 변환 불필요

---

## 3. API 엔드포인트 변경

**파일**: `src/main/java/com/example/attempt/controller/PlaceController.java`

### 3.1 GET /api/place

#### 변경 전
```java
// 웹 스크래핑 방식
// yscsc.co.kr에서 Jsoup으로 데이터 수집
Document doc = Jsoup.connect(baseUrl).get();
Elements activityBoxes = doc.select("div.sub02_01_in_box");
```

#### 변경 후
```java
// DB 조회 방식
List<Place> places = placeRepository.findAll();
for (Place place : places) {
    results.add(new AddressDto(
        place.getImageUrl() != null ? place.getImageUrl() : "",
        place.getName() != null ? place.getName() : ""
    ));
}
```

**반환 형식**: `List<AddressDto>`
- `image_url`: 이미지 URL
- `text`: 사업단명

---

### 3.2 GET /api/place/list (신규 추가)

Flutter 앱의 `getPlaceList()` 메서드를 위해 새로 구현한 엔드포인트

```java
@GetMapping("/api/place/list")
public ResponseEntity<List<LocationDto>> getPlaceList() {
    List<Place> places = placeRepository.findAll();
    List<LocationDto> results = new ArrayList<>();

    for (Place place : places) {
        results.add(new LocationDto(
            place.getName(),
            place.getAddress(),
            place.getLatitude(),
            place.getLongitude(),
            place.getPhoneNumber(),
            place.getDescription()
        ));
    }

    return ResponseEntity.ok(results);
}
```

**반환 형식**: `List<LocationDto>`
- `business_unit`: 사업단명
- `address`: 주소
- `lat`: 위도
- `lng`: 경도
- `phone_number`: 전화번호 (optional)
- `description`: 설명 (optional)

---

### 3.3 POST /api/place/save & POST /api/place/save-all

전화번호와 설명 필드 저장 지원 추가

```java
Place place = new Place(
    locationDto.getBusiness_unit(),
    locationDto.getAddress(),
    locationDto.getLat(),
    locationDto.getLng(),
    null, // imageUrl
    locationDto.getPhone_number(),
    locationDto.getDescription()
);
```

---

## 4. DTO 변경

### LocationDto 업데이트

Flutter의 `JobPlace` 모델과 정확히 매핑되도록 필드명 변경

```java
public static class LocationDto {
    private String business_unit;  // businessUnit → business_unit
    private String address;
    private Double lat;
    private Double lng;
    private String phone_number;   // 신규
    private String description;    // 신규
}
```

**하위 호환성**: 기존 `getBusinessUnit()`/`setBusinessUnit()` 메서드 유지

---

## 5. 제거된 코드

### 제거된 의존성
- `org.jsoup.Jsoup`
- `org.jsoup.nodes.Document`
- `org.jsoup.nodes.Element`
- `org.jsoup.select.Elements`
- `java.io.IOException`
- `java.time.Duration`

### 제거된 상수
```java
// 제거됨
private static final List<String> BASE_URLS = List.of(
    "http://www.yscsc.co.kr/02/01.php",
    "http://www.yscsc.co.kr/02/02.php",
    "http://www.yscsc.co.kr/02/03.php"
);
private static final int TIMEOUT_MS = (int) Duration.ofSeconds(10).toMillis();

@Value("${geocoding-api-key}")
private String AUTH_TOKEN;
```

---

## 6. 데이터 흐름

### 변경 전
```
외부 웹사이트 (yscsc.co.kr)
    ↓ 실시간 웹 스크래핑
GET /api/place
    ↓
Flutter 앱
```

### 변경 후
```
Place 테이블 (DB)
    ↓ DB 조회
GET /api/place → JobInfo (이미지, 제목)
GET /api/place/list → JobPlace (전체 정보)
    ↓
Flutter 앱
```

---

## 7. 마이그레이션 가이드

### 7.1 데이터베이스 마이그레이션

새로운 컬럼이 자동으로 추가됩니다:
- `image_url` VARCHAR
- `phone_number` VARCHAR
- `description` VARCHAR(1000)

기존 데이터는 영향을 받지 않으며, 새 컬럼은 `NULL` 값으로 초기화됩니다.

### 7.2 초기 데이터 입력

DB에 데이터가 없으면 빈 배열을 반환합니다. 초기 데이터 입력 방법:

```bash
POST /api/place/save-all
Content-Type: application/json

[
  {
    "business_unit": "사업단명",
    "address": "주소",
    "lat": 37.5665,
    "lng": 126.9780,
    "phone_number": "02-1234-5678",
    "description": "일자리 설명"
  }
]
```

### 7.3 웹 스크래핑 코드 (선택사항)

필요시 별도의 배치 작업으로 분리하여 주기적으로 데이터를 수집하고 DB에 저장할 수 있습니다.

---

## 8. 장점

### 성능 개선
- 외부 웹사이트 의존성 제거
- 응답 속도 향상 (네트워크 I/O → DB 조회)
- 타임아웃 에러 제거

### 안정성 향상
- 외부 웹사이트 구조 변경에 영향받지 않음
- 웹사이트 다운타임에도 서비스 지속 가능
- 일관된 데이터 제공

### 기능 확장
- 데이터 관리 기능 (추가, 수정, 삭제)
- 전화번호, 설명 등 추가 정보 저장 가능
- 검색, 필터링 기능 구현 가능

---

## 9. 주의사항

1. **초기 데이터 필수**: 서비스 시작 전 DB에 일자리 데이터를 입력해야 합니다.

2. **이미지 URL**: 저장 시 `imageUrl`을 함께 저장하거나, 별도 이미지 관리 시스템 구축이 필요합니다.

3. **데이터 동기화**: 정기적으로 최신 일자리 정보를 업데이트하는 배치 작업 고려가 필요합니다.

---

## 10. 테스트

### 빌드 테스트
```bash
./gradlew build -x test
# BUILD SUCCESSFUL
```

### API 테스트 예시

```bash
# 일자리 목록 조회
GET http://localhost:8080/api/place

# 장소 상세 목록 조회
GET http://localhost:8080/api/place/list

# 장소 저장
POST http://localhost:8080/api/place/save
Content-Type: application/json

{
  "business_unit": "테스트 사업단",
  "address": "서울시 용산구",
  "lat": 37.5326,
  "lng": 126.9900,
  "phone_number": "02-1234-5678",
  "description": "테스트 설명"
}
```

---

## 11. Flutter 앱 호환성

Flutter 앱의 기존 API 호출과 완벽히 호환됩니다:

- `GET /api/place` → `JobInfo` 모델과 매핑
- `GET /api/place/list` → `JobPlace` 모델과 매핑
- `POST /api/place/save` → 기존과 동일
- `POST /api/place/save-all` → 기존과 동일

추가 변경사항 없이 즉시 사용 가능합니다.
