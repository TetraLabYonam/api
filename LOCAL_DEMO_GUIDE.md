# 로컬 환경 시연 가이드

본 문서는 번호표 시스템을 로컬 환경에서 빠르게 시연하는 방법을 설명합니다.

## 목차
1. [사전 요구사항](#사전-요구사항)
2. [백엔드 설정 및 실행](#백엔드-설정-및-실행)
3. [프론트엔드 설정 및 실행](#프론트엔드-설정-및-실행)
4. [시연 시나리오](#시연-시나리오)
5. [문제 해결](#문제-해결)

---

## 사전 요구사항

### 필수 소프트웨어
- ✅ Java 17 이상
- ✅ Node.js 18 이상
- ✅ npm 또는 yarn

### 선택 사항 (권장)
- MariaDB 10.6 이상 (또는 H2 인메모리 DB 사용)
- IntelliJ IDEA 또는 VS Code

---

## 백엔드 설정 및 실행

### 방법 1: H2 인메모리 DB 사용 (빠른 시연 - 권장)

H2 데이터베이스를 사용하면 별도의 DB 설치 없이 바로 시연할 수 있습니다.

#### 1단계: 백엔드 실행

```bash
cd /path/to/attempt

# 로컬 프로필로 실행 (H2 DB 사용)
./gradlew bootRun --args='--spring.profiles.active=local'
```

또는 IntelliJ IDEA에서:
1. `AttemptApplication.java` 파일 열기
2. Run Configuration 편집
3. VM Options에 `-Dspring.profiles.active=local` 추가
4. 실행

#### 2단계: 서버 실행 확인

```bash
# 서버 상태 확인
curl http://localhost:8080/api/queue/rooms

# 또는 브라우저에서
http://localhost:8080/swagger-ui/index.html
```

#### 3단계: H2 콘솔 접속 (선택)

데이터베이스 내용을 직접 확인하고 싶을 때:

```
URL: http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:queueapp
Username: sa
Password: (비어있음)
```

### 방법 2: MariaDB 사용

#### 1단계: MariaDB 설치 및 설정

**macOS (Homebrew):**
```bash
brew install mariadb
brew services start mariadb
```

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install mariadb-server
sudo systemctl start mariadb
```

#### 2단계: 데이터베이스 생성

```bash
# MariaDB 접속
mysql -u root -p

# 데이터베이스 및 사용자 생성
CREATE DATABASE queueapp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'queue'@'localhost' IDENTIFIED BY 'queuepw';
GRANT ALL PRIVILEGES ON queueapp.* TO 'queue'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

#### 3단계: 백엔드 실행

```bash
# 기본 프로필로 실행 (MariaDB 사용)
./gradlew bootRun
```

---

## 프론트엔드 설정 및 실행

### 1단계: 환경 변수 설정

```bash
cd frontend

# .env.example을 복사하여 .env.local 생성
cp .env.example .env.local

# .env.local 파일 수정
nano .env.local  # 또는 원하는 에디터 사용
```

`.env.local` 파일 내용:
```env
# 백엔드 API URL (로컬 서버)
VITE_API_URL=http://localhost:8080

# Google Maps API 키 (선택 - 장소 관리 기능 사용 시 필요)
VITE_GOOGLE_MAPS_API_KEY=YOUR_API_KEY_HERE
```

### 2단계: 의존성 설치

```bash
npm install
```

### 3단계: 개발 서버 실행

```bash
npm run dev
```

서버가 시작되면 브라우저에서 자동으로 열립니다:
```
http://localhost:5173
```

---

## 시연 시나리오

### 시나리오 1: 사용자 - 번호표 발급 받기

#### 1. 방 목록 확인
```
URL: http://localhost:5173/rooms
```
- 활성화된 방 목록이 표시됩니다
- 방이 없다면 관리자 페이지에서 먼저 생성하세요

#### 2. 방 입장
- 방 카드를 클릭하여 입장
- WebSocket 연결 상태 확인 (✓ 실시간 연결됨)

#### 3. 번호표 발급
- "번호표 발급받기" 버튼 클릭
- 발급된 번호 확인
- 대기 인원 확인

#### 4. 실시간 업데이트 확인
- 다른 브라우저 탭이나 시크릿 모드로 동일한 방 입장
- 번호표 발급 시 모든 탭에서 실시간으로 업데이트 확인

### 시나리오 2: 관리자 - 방 관리

#### 1. 관리자 페이지 접속
```
URL: http://localhost:5173/admin
```

#### 2. 새 방 생성
- "새 방 만들기" 버튼 클릭
- 방 제목 입력 (예: "물금청소년문화의집")
- 생성된 방 코드 확인

#### 3. 일괄 생성 (선택)
- "일괄 생성" 버튼 클릭
- 쉼표로 구분하여 여러 방 이름 입력
  ```
  물금청소년문화의집, 동면 행정복지센터, 원동면 행정복지센터
  ```
- 한 번에 여러 방 생성 확인

#### 4. 번호 호출
- "호출" 버튼 클릭
- 현재 번호가 1씩 증가하는 것 확인
- 사용자 화면에서 실시간 업데이트 확인

#### 5. 알림 전송
- "알림" 버튼 클릭
- 번호 입력 (예: 3)
- 메시지 입력 (예: "창구로 와주세요")
- 해당 번호를 가진 사용자에게 알림 전송

#### 6. 방 초기화
- "초기화" 버튼 클릭
- 방의 모든 번호가 0으로 리셋
- 모든 발급 기록 삭제

### 시나리오 3: 전체 흐름 (2개 브라우저)

#### 브라우저 A (사용자 1)
1. `http://localhost:5173/rooms` 접속
2. 방 선택 및 입장
3. 번호표 발급 → 번호 1 받음

#### 브라우저 B (사용자 2)
1. 동일한 방 입장 (시크릿 모드 사용 권장)
2. 번호표 발급 → 번호 2 받음
3. 브라우저 A에서 대기 인원이 2명으로 업데이트됨 확인

#### 브라우저 C (관리자)
1. `http://localhost:5173/admin` 접속
2. "호출" 버튼 클릭 → 현재 번호 1로 변경
3. 브라우저 A와 B에서 실시간으로 현재 번호 업데이트 확인
4. 브라우저 A (번호 1)에서 대기 0명 표시 확인

---

## API 테스트 (curl)

### 1. 방 목록 조회
```bash
curl http://localhost:8080/api/queue/rooms
```

### 2. 번호표 발급
```bash
curl -X POST http://localhost:8080/api/queue/room/{roomUid}/ticket \
  -H "Content-Type: application/json" \
  -d '{"userDeviceId": "test-device-001"}'
```

### 3. 방 현황 조회
```bash
curl http://localhost:8080/api/queue/room/{roomUid}/status
```

### 4. 방 생성 (관리자)
```bash
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{
    "roomName": "테스트 방"
  }'
```

### 5. 다음 번호 호출
```bash
curl -X POST http://localhost:8080/api/queue/room/{roomUid}/call
```

---

## 문제 해결

### 백엔드 관련

#### 1. 포트 8080이 이미 사용 중
```bash
# 포트 사용 중인 프로세스 확인
lsof -i :8080

# 프로세스 종료
kill -9 <PID>
```

또는 다른 포트로 실행:
```bash
./gradlew bootRun --args='--server.port=8081 --spring.profiles.active=local'
```

#### 2. MariaDB 연결 실패
- MariaDB 서비스 실행 확인:
  ```bash
  # macOS
  brew services list
  brew services start mariadb

  # Linux
  sudo systemctl status mariadb
  sudo systemctl start mariadb
  ```
- 데이터베이스 및 사용자 생성 확인
- 비밀번호 확인 (`queuepw`)

#### 3. H2 콘솔 접속 안 됨
- `application-local.yml` 확인:
  ```yaml
  spring:
    h2:
      console:
        enabled: true
  ```
- 프로필이 `local`로 실행되었는지 확인

### 프론트엔드 관련

#### 1. CORS 에러
```
Access to XMLHttpRequest at 'http://localhost:8080/api/...'
from origin 'http://localhost:5173' has been blocked by CORS policy
```

**해결 방법:**
- 백엔드 서버가 실행 중인지 확인
- `CorsConfig.java`가 제대로 설정되어 있는지 확인 (이미 설정됨)
- 브라우저 캐시 삭제 후 재시도

#### 2. WebSocket 연결 실패
```
WebSocket connection to 'ws://localhost:8080/ws' failed
```

**해결 방법:**
- 백엔드 서버가 실행 중인지 확인
- `WebSocketConfig.java` 설정 확인 (이미 설정됨)
- 프론트엔드 `.env.local`의 `VITE_API_URL` 확인
- 브라우저 개발자 도구 > Network > WS 탭에서 에러 확인

#### 3. API 호출 실패
```
Failed to fetch
```

**해결 방법:**
- `.env.local` 파일 확인:
  ```env
  VITE_API_URL=http://localhost:8080
  ```
- 개발 서버 재시작:
  ```bash
  npm run dev
  ```
- 백엔드 서버 URL 확인:
  ```bash
  curl http://localhost:8080/api/queue/rooms
  ```

#### 4. 환경 변수가 적용 안 됨
- Vite는 `VITE_` 접두사로 시작하는 변수만 클라이언트에 노출
- `.env.local` 파일 저장 확인
- 개발 서버 재시작 필수

---

## 빠른 시작 체크리스트

### 백엔드
- [ ] Java 17 이상 설치 확인
- [ ] 프로젝트 디렉토리로 이동
- [ ] `./gradlew bootRun --args='--spring.profiles.active=local'` 실행
- [ ] `http://localhost:8080/swagger-ui/index.html` 접속 확인

### 프론트엔드
- [ ] Node.js 18 이상 설치 확인
- [ ] `frontend` 디렉토리로 이동
- [ ] `.env.local` 파일 생성 및 `VITE_API_URL=http://localhost:8080` 설정
- [ ] `npm install` 실행
- [ ] `npm run dev` 실행
- [ ] `http://localhost:5173` 자동 오픈 확인

### 시연
- [ ] 관리자 페이지에서 방 생성 (`/admin`)
- [ ] 사용자 페이지에서 번호표 발급 (`/rooms`)
- [ ] 다른 브라우저로 동일한 방 입장하여 실시간 업데이트 확인
- [ ] 관리자 페이지에서 번호 호출 및 알림 전송

---

## 추가 리소스

- **API 문서**: http://localhost:8080/swagger-ui/index.html
- **Flutter 가이드**: [FLUTTER_INTEGRATION_GUIDE.md](FLUTTER_INTEGRATION_GUIDE.md)
- **React 가이드**: [REACT_QUEUE_SYSTEM_GUIDE.md](REACT_QUEUE_SYSTEM_GUIDE.md)
- **API 테스트 가이드**: [API_TESTING_GUIDE.md](API_TESTING_GUIDE.md)

---

## 데모 데이터 생성 (선택)

시연을 위해 미리 데이터를 생성하고 싶다면:

### 방법 1: Swagger UI 사용
1. `http://localhost:8080/swagger-ui/index.html` 접속
2. `Room API` 섹션 펼치기
3. `POST /api/v1/rooms/batch` 실행
4. Request Body:
   ```json
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

### 방법 2: curl 사용
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

## 요약

✅ **H2 DB 사용 시**: 별도 DB 설치 없이 바로 시연 가능
✅ **로컬 프로필**: `--spring.profiles.active=local`
✅ **프론트엔드 환경 변수**: `.env.local` 파일에 `VITE_API_URL=http://localhost:8080`
✅ **2개 이상의 브라우저**: 실시간 업데이트 확인
✅ **Swagger UI**: API 테스트 및 데이터 생성

로컬 환경에서 5분 안에 시연을 시작할 수 있습니다! 🚀
