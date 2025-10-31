# Attendance Management System

출석 관리 및 장소 기반 서비스를 제공하는 Spring Boot 애플리케이션입니다.

## 주요 기능

- 회원 관리 (Member)
- 장소(Place) 정보 관리 및 좌표 변환
- 스케줄 및 출석(Attend) 관리
- 엑셀 파일에서 주소 데이터 읽기
- Google Geocoding API를 통한 주소 → 좌표 변환
- 외부 웹사이트 스크래핑을 통한 장소 정보 수집

## 기술 스택

- **Framework**: Spring Boot 3.5.6
- **Language**: Java 17
- **Database**: H2 (In-Memory)
- **ORM**: JPA / Hibernate 6
- **Template Engine**: Thymeleaf
- **Build Tool**: Gradle
- **External APIs**: Google Maps Geocoding API
- **Libraries**:
  - Apache POI (엑셀 처리)
  - Jsoup (웹 스크래핑)
  - Lombok
  - p6spy (SQL 로깅)

## 시작하기

### 사전 요구사항

- Java 17 이상
- Google Cloud Platform 계정 (Geocoding API 키 필요)

### 설정

#### 1. 저장소 클론

```bash
git clone <repository-url>
cd attempt
```

#### 2. Google API 키 설정

**중요**: 애플리케이션 실행 전 반드시 Google Geocoding API 키를 설정해야 합니다.

##### Google Cloud Console에서 API 키 발급

1. [Google Cloud Console](https://console.cloud.google.com/) 접속
2. 프로젝트 생성 또는 기존 프로젝트 선택
3. 좌측 메뉴에서 "API 및 서비스" > "사용자 인증 정보" 클릭
4. "사용자 인증 정보 만들기" > "API 키" 선택
5. [Geocoding API 활성화](https://console.cloud.google.com/apis/library/geocoding-backend.googleapis.com)
6. 발급받은 API 키 복사

##### 설정 파일 생성

```bash
# 예제 파일을 복사하여 설정 파일 생성
cd src/main/resources
cp application-API-KEY.properties.example application-API-KEY.properties
```

`application-API-KEY.properties` 파일을 열고 발급받은 API 키로 수정:

```properties
geocoding-api-key=YOUR_GOOGLE_GEOCODING_API_KEY_HERE
```

> **참고**: `application-API-KEY.properties` 파일은 `.gitignore`에 포함되어 Git에 커밋되지 않습니다.

### 실행

#### Gradle로 실행

```bash
# 프로젝트 루트 디렉토리에서
./gradlew bootRun
```

#### IDE에서 실행

1. IntelliJ IDEA 또는 Eclipse에서 프로젝트 열기
2. `AttemptApplication.java` 파일의 `main` 메서드 실행

### 접속

애플리케이션이 시작되면 다음 URL로 접속할 수 있습니다:

- 기본 URL: `http://localhost:8080`
- H2 Console: `http://localhost:8080/h2-console`
  - JDBC URL: `jdbc:h2:mem:test`
  - Username: `sa`
  - Password: (비어있음)

## API 엔드포인트

### Member API

- `GET /api/v1/member` - 회원 목록 조회 (테스트용)

### Place API

- `GET /api/place` - 외부 사이트에서 장소 정보 스크래핑
- `POST /api/place/save` - 단일 장소 저장
- `POST /api/place/save-all` - 다수 장소 일괄 저장

### Map API

- `GET /mapV1` - 지도 뷰 (단일 위치)
- `GET /map-excel` - 엑셀 업로드 페이지
- `POST /map-excel` - 엑셀 파일에서 주소 추출 및 좌표 변환

## 디렉토리 구조

```
src/main/java/com/example/attempt/
├── controller/          # REST 컨트롤러
│   ├── AttendController.java
│   ├── HelloController.java
│   ├── MapController.java
│   ├── MemberController.java
│   └── PlaceController.java
├── domain/              # JPA 엔티티
│   ├── Attend.java
│   ├── Member.java
│   ├── Place.java
│   ├── Schedule.java
│   └── Unit.java
├── Repository/          # 데이터 접근 계층
│   ├── AttendRepository.java
│   ├── MemberRepository.java
│   ├── PlaceRepository.java
│   ├── ScheduleRepository.java
│   └── UnitRepository.java
└── service/             # 비즈니스 로직
    └── MemberService.java
```

## 보안 주의사항

- **API 키 관리**: 절대로 API 키를 Git에 커밋하지 마세요
- **프로덕션 배포**:
  - H2 콘솔 비활성화 (`spring.h2.console.enabled=false`)
  - `ddl-auto`를 `validate`로 변경
  - 실제 데이터베이스(MySQL, PostgreSQL 등) 사용 권장

## 개발 환경

### 테스트 실행

```bash
./gradlew test
```

### 빌드

```bash
./gradlew build
```

## 라이선스

이 프로젝트는 개인 학습/개발 목적으로 작성되었습니다.

## 문의

프로젝트 관련 문의사항은 이슈를 등록해주세요.