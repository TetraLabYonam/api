# JWT 인증 시스템 구현 문서

## 개요
본 문서는 레포지토리에 JWT 기반 인증·인가 시스템을 도입한 내용을 정리합니다.
기존에는 클라이언트 생성 `userKey`(localStorage)로 사용자를 식별했으나, 보안 취약점을 보완하기 위해 서버 발급 JWT 토큰 기반 인증으로 전환했습니다.

## 주요 변경사항

### 1. 의존성 추가 (build.gradle)
```gradle
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'io.jsonwebtoken:jjwt-api:0.11.5'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.11.5'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.11.5'
```

### 2. JWT 토큰 관리 (JwtTokenProvider)
**파일**: `src/main/java/com/example/attempt/security/JwtTokenProvider.java`

**기능**:
- Access Token 생성 (짧은 만료시간, 기본 15분)
- Refresh Token 생성 (긴 만료시간, 기본 14일)
- 토큰 검증 및 Claims 추출
- 환경변수 기반 Secret Key 관리

**설정**:
- `jwt.secret`: JWT 서명 키 (환경변수 `JWT_SECRET` 권장)
- `jwt.access-exp-ms`: Access Token 만료시간 (기본 900000ms = 15분)
- `jwt.refresh-exp-ms`: Refresh Token 만료시간 (기본 1209600000ms = 14일)

### 3. JWT 인증 필터 (JwtAuthenticationFilter)
**파일**: `src/main/java/com/example/attempt/security/JwtAuthenticationFilter.java`

**동작**:
1. HTTP 요청 헤더에서 `Authorization: Bearer <token>` 추출
2. 토큰 검증 후 사용자 정보(username, roles) 파싱
3. SecurityContext에 Authentication 객체 설정

### 4. Spring Security 설정 (SecurityConfig)
**파일**: `src/main/java/com/example/attempt/config/SecurityConfig.java`

**권한 규칙**:
- **인증 불필요**: `/api/auth/**`, `/api/devices/register`, `/ws/**`, `/actuator/health`
- **읽기 허용**: GET `/api/place/**`, GET `/api/v1/member/**`
- **관리자 전용** (ROLE_ADMIN): `/api/v1/admin/**`, `/api/v1/rooms/**`
- **기타 모든 엔드포인트**: 인증 필요

**특징**:
- Stateless 세션 정책 (JWT 사용)
- CSRF 비활성화 (REST API + JWT 구조)
- BCrypt 비밀번호 암호화

### 5. 인증 API (AuthController)
**파일**: `src/main/java/com/example/attempt/controller/AuthController.java`

**엔드포인트**:

#### POST /api/auth/login
- **요청**: `{ "username": "admin", "password": "pw" }`
- **응답**: `{ "accessToken": "<jwt>" }` + `Set-Cookie: refreshToken=<token>; HttpOnly`
- **설명**: 사용자 인증 후 Access Token(JSON)과 Refresh Token(HttpOnly Cookie) 발급

#### POST /api/auth/refresh
- **요청**: refreshToken cookie 자동 전송
- **응답**: `{ "accessToken": "<new_jwt>" }`
- **설명**: Refresh Token으로 새로운 Access Token 발급

#### POST /api/auth/logout
- **요청**: refreshToken cookie 전송
- **응답**: `{ "message": "logged out" }`
- **설명**: refreshToken 쿠키 삭제 (클라이언트 측 로그아웃)

### 6. 디바이스 등록 API (DeviceController)
**파일**: `src/main/java/com/example/attempt/controller/DeviceController.java`

**엔드포인트**:

#### POST /api/devices/register
- **요청**: `{ "userKey": "<client-userKey>" }` (optional)
- **응답**: `{ "deviceToken": "<jwt>", "deviceId": "<id>" }`
- **설명**: 기존 userKey를 서버 발급 deviceToken으로 전환 (백호환 마이그레이션 지원)

**마이그레이션 전략**:
- **userKey 보존**: 기존 userKey를 제출하면 서버가 deviceId에 매핑하여 deviceToken 발급
- **userKey 미보존**: 모든 클라이언트가 새 deviceToken 발급 필수 (보안 강화)

### 7. WebSocket 인증 (WebSocketAuthInterceptor)
**파일**: `src/main/java/com/example/attempt/config/WebSocketAuthInterceptor.java`

**동작**:
1. STOMP CONNECT 시 `Authorization: Bearer <token>` 헤더 추출
2. JWT 검증 후 Principal 설정
3. WebSocket 세션에 사용자 인증 정보 바인딩

**등록**:
- `WebSocketConfig.configureClientInboundChannel()`에서 인터셉터 등록

### 8. CORS 보안 강화 (CorsConfig)
**파일**: `src/main/java/com/example/attempt/config/CorsConfig.java`

**변경사항**:
- `allowedOriginPatterns("*")` → 환경 기반 화이트리스트
- 환경변수 `ALLOWED_ORIGINS` 또는 `app.allowed-origins` 프로퍼티 사용
- 기본값: `http://localhost:5173,http://10.0.2.2:8080,http://192.168.0.100:8080`

**보안 개선**:
- CSRF/자격 도용 위험 방지
- 운영 환경 특정 도메인만 허용

### 9. 예외 처리 확장 (GlobalExceptionHandler)
**파일**: `src/main/java/com/example/attempt/exception/GlobalExceptionHandler.java`

**추가 핸들러**:
- `AuthenticationException` → 401 Unauthorized
- `AccessDeniedException` → 403 Forbidden

**특징**:
- 사용자 친화적 에러 메시지
- 내부 예외 정보 노출 방지
- WARN 레벨 로깅으로 보안 모니터링 지원

### 10. 설정 파일 (application.yml)
**파일**: `src/main/resources/application.yml`

**추가 설정**:
```yaml
jwt:
  secret: ${JWT_SECRET:your-256-bit-secret-key-change-this-in-production-environment-must-be-very-long}
  access-exp-ms: ${JWT_ACCESS_EXP:900000}  # 15분
  refresh-exp-ms: ${JWT_REFRESH_EXP:1209600000}  # 14일

app:
  allowed-origins: ${ALLOWED_ORIGINS:http://localhost:5173,http://10.0.2.2:8080,http://192.168.0.100:8080}
```

## 커밋 이력

1. **build: add Spring Security and JWT dependencies**
   - Spring Security 및 JWT 의존성 추가

2. **feat(auth): implement JWT token provider**
   - JwtTokenProvider 구현 (토큰 생성/검증)

3. **feat(auth): implement JWT authentication filter**
   - JwtAuthenticationFilter 구현 (요청별 JWT 검증)

4. **feat(security): configure Spring Security with JWT**
   - SecurityConfig 구현 (권한 규칙, 필터 체인)

5. **feat(auth): implement authentication API endpoints**
   - AuthController 구현 (login/refresh/logout)

6. **feat(auth): implement device registration endpoint**
   - DeviceController 구현 (userKey → deviceToken 마이그레이션)

7. **feat(websocket): implement JWT authentication for WebSocket connections**
   - WebSocketAuthInterceptor 구현 및 등록

8. **security: restrict CORS to specific origins**
   - CorsConfig 수정 (와일드카드 제거, 환경 기반 화이트리스트)

9. **feat(exception): add authentication and authorization error handlers**
   - GlobalExceptionHandler 확장 (401/403 핸들러)

10. **config: add JWT and CORS configuration properties**
    - application.yml에 JWT 및 CORS 설정 추가

## 환경 변수

### 필수
- **JWT_SECRET**: JWT 서명 키 (256비트 이상 랜덤 문자열 권장)
  ```bash
  export JWT_SECRET="your-very-long-and-random-secret-key-here"
  ```

### 선택
- **JWT_ACCESS_EXP**: Access Token 만료시간 (밀리초, 기본 900000 = 15분)
- **JWT_REFRESH_EXP**: Refresh Token 만료시간 (밀리초, 기본 1209600000 = 14일)
- **ALLOWED_ORIGINS**: CORS 허용 출처 (콤마 구분)
  ```bash
  export ALLOWED_ORIGINS="https://app.example.com,http://localhost:5173,http://10.0.2.2:8080"
  ```

## 클라이언트 통합 가이드

### 웹 클라이언트 (JavaScript/React/Vue)

#### 1. 로그인
```javascript
const response = await axios.post('/api/auth/login', {
  username: 'admin',
  password: 'password'
}, { withCredentials: true });

const accessToken = response.data.accessToken;
// accessToken을 메모리 또는 state에 저장 (localStorage 비권장)
```

#### 2. API 요청
```javascript
const apiClient = axios.create({
  baseURL: 'http://localhost:8080',
  withCredentials: true // refreshToken cookie 전송
});

apiClient.interceptors.request.use(config => {
  const token = getAccessToken(); // 메모리에서 가져오기
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Access Token 만료 시 자동 갱신
apiClient.interceptors.response.use(null, async error => {
  if (error.response?.status === 401 && !error.config._retry) {
    error.config._retry = true;
    const refreshResp = await axios.post('/api/auth/refresh', {}, { withCredentials: true });
    setAccessToken(refreshResp.data.accessToken);
    error.config.headers.Authorization = `Bearer ${refreshResp.data.accessToken}`;
    return axios(error.config);
  }
  return Promise.reject(error);
});
```

#### 3. WebSocket 연결
```javascript
const client = new Client({
  webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
  connectHeaders: {
    Authorization: `Bearer ${getAccessToken()}`
  },
  // ...
});
```

### Android 클라이언트

#### 1. Base URL 설정
```kotlin
// 에뮬레이터
const val BASE_URL = "http://10.0.2.2:8080"

// 실제 기기 (PC의 로컬 IP)
const val BASE_URL = "http://192.168.0.100:8080"
```

#### 2. 토큰 저장
```kotlin
// EncryptedSharedPreferences 사용 권장
val sharedPreferences = EncryptedSharedPreferences.create(
    context,
    "secure_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

// Access Token 저장
sharedPreferences.edit()
    .putString("accessToken", token)
    .apply()
```

#### 3. Retrofit 인터셉터
```kotlin
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = getAccessToken()
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
```

## 테스트 가이드

### 1. 로그인 테스트
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}' \
  -c cookies.txt

# 응답: {"accessToken":"eyJhbGc..."}
# refreshToken은 Set-Cookie 헤더로 전달
```

### 2. 보호된 API 호출
```bash
export ACCESS_TOKEN="eyJhbGc..."

curl -X GET http://localhost:8080/api/v1/admin/some-endpoint \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

### 3. Token Refresh
```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -b cookies.txt

# 응답: {"accessToken":"eyJhbGc..."}
```

### 4. WebSocket 연결 테스트
```javascript
// STOMP 클라이언트 예시
const client = new Client({
  webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
  connectHeaders: {
    Authorization: 'Bearer eyJhbGc...'
  },
  onConnect: () => {
    console.log('Connected with JWT auth');
    client.subscribe('/user/queue/ticket', message => {
      console.log('Received:', message.body);
    });
  }
});
client.activate();
```

## 보안 권고사항

### 운영 환경
1. **HTTPS 필수**: 모든 통신은 HTTPS로 암호화
2. **JWT Secret 강화**: 256비트 이상 랜덤 문자열 사용
3. **Refresh Token 쿠키**: `Secure`, `HttpOnly`, `SameSite=Strict` 설정
4. **CORS 제한**: 운영 도메인만 화이트리스트에 추가
5. **Rate Limiting**: 로그인 엔드포인트에 요청 빈도 제한 적용

### 토큰 관리
- **Access Token**: 메모리 저장 (리로드 시 사라짐)
- **Refresh Token**: HttpOnly Cookie (XSS 공격 방지)
- **Device Token**: 플랫폼 안전 저장소 (Android EncryptedSharedPreferences, iOS Keychain)

### 감사 로그
- 인증 실패 로그 모니터링
- 관리자 작업 감사 로그 (추후 구현 권장)
- 비정상 접근 패턴 탐지

## 마이그레이션 전략

### userKey 보존 (권장)
1. 프론트엔드: 기존 localStorage의 userKey를 `/api/devices/register`에 제출
2. 백엔드: userKey와 deviceId 매핑, deviceToken 발급
3. 프론트엔드: deviceToken을 안전한 저장소에 보관
4. 유예기간(30일) 후 legacy userKey 액세스 차단

### userKey 미보존 (강제 전환)
1. 배포 시점에 클라이언트 강제 업데이트
2. 모든 사용자가 `/api/devices/register`에서 새 deviceToken 발급
3. 기존 티켓 데이터는 별도 마이그레이션 스크립트로 연결

## TODO

### 단기 (우선순위 높음)
- [ ] Admin 계정 생성 스크립트 (BCrypt 패스워드 해싱)
- [ ] Refresh Token Blacklist 구현 (로그아웃 시 서버 측 무효화)
- [ ] WebSocket SUBSCRIBE/SEND 권한 검증 추가
- [ ] Device Registration 마이그레이션 로직 구현

### 중기
- [ ] Rate Limiting (Spring Cloud Gateway 또는 Bucket4j)
- [ ] 감사 로그 (Admin 작업 기록)
- [ ] 비밀번호 정책 (최소 길이, 복잡도, 계정 잠금)

### 장기
- [ ] 2FA (Two-Factor Authentication) 지원
- [ ] OAuth2/OIDC 통합 (Google, GitHub 등)
- [ ] Token Rotation 전략 (Refresh Token 자동 갱신)
- [ ] 보안 테스트 자동화 (SAST/DAST)

## 문제 해결

### 1. 401 Unauthorized 오류
- Access Token이 만료되었거나 유효하지 않음
- `/api/auth/refresh`로 토큰 갱신 시도
- Refresh Token도 만료 시 재로그인 필요

### 2. 403 Forbidden 오류
- 권한 부족 (ROLE_ADMIN 필요)
- 관리자 계정으로 로그인 확인

### 3. WebSocket 연결 실패
- `Authorization` 헤더에 유효한 JWT 포함 확인
- STOMP CONNECT 명령어에 헤더 전달 확인

### 4. CORS 오류
- `ALLOWED_ORIGINS` 환경변수에 클라이언트 도메인 추가
- `withCredentials: true` 설정 확인

## 참고 자료

- [Spring Security Documentation](https://docs.spring.io/spring-security/reference/index.html)
- [JWT.io](https://jwt.io/)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [RFC 7519 - JSON Web Token](https://tools.ietf.org/html/rfc7519)

---

**문서 작성일**: 2026-01-05
**작성자**: Claude Sonnet 4.5
**버전**: 1.0.0
