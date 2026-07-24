# nginx HTTPS + Flutter 웹 빌드 위치 테스트 도구 설계

## 이번 범위

| 구분 | 내용 |
|---|---|
| HTTPS | `admin-web`, 신규 `mobile-web` 두 nginx 서비스에 자체 서명 인증서로 443 포트 지원 추가 (기존 80 포트는 유지) |
| mobile-web | 기존 Flutter 모바일 앱(`mobile/`)을 `flutter build web`로 빌드해 docker-compose에 새 서비스로 추가. 실제 모바일 기기 없이 LAN의 브라우저(폰 포함)로 접속해 출석 체크인 플로우 검증 |
| 위치 테스트 패널 | `checkin_screen.dart`에 web 전용 위치 오버라이드 패널 추가: 브라우저 Geolocation API의 실제 위치를 보여주고, 체크인 시 전송할 위도/경도를 직접 수정 가능 |

## 제외 범위

- 실제 도메인/Let's Encrypt 인증서 (로컬/LAN 테스트 목적만, 프로덕션 배포용 인증서는 별도 작업)
- IP 기반 외부 지오로케이션 API 연동
- Flutter 통합/e2e 테스트 자동화 (이 도구 자체가 수동 QA용이므로 위젯 테스트로 충분)
- 백엔드 변경 없음 — 거리 초과 시 기존 에러 메시지(`거리: %.1fm, 허용 반경: %dm`)를 그대로 재사용

## 주요 계약

### nginx / docker-compose

| 파일 | 변경 |
|---|---|
| `admin-web/nginx.conf`, `mobile/nginx.conf`(신규) | 같은 `server{}`에 `listen 80;`과 `listen 443 ssl;` 동시 선언, `ssl_certificate`/`ssl_certificate_key`는 마운트된 `/etc/nginx/certs/dev.crt`, `dev.key` 참조. `mobile-web`도 admin-web과 동일하게 `/api/` → `backend:8080` 프록시 (같은 오리진이라 CORS 불필요) |
| `docker-compose.yml` | `admin-web`은 `443:443` 추가(기존 `80:80` 유지). 신규 `mobile-web`은 호스트 포트 충돌을 피해 `8080:80`, `8443:443`로 매핑(둘 다 `./certs:/etc/nginx/certs:ro` 볼륨 추가, build context `./mobile`, `depends_on: backend`) |
| `mobile/Dockerfile`(신규) | 1단계: Flutter 이미지에서 `flutter build web`. 2단계: `nginx:1.27-alpine`에 `build/web` 정적 배포 (admin-web Dockerfile과 동일 구조) |
| `mobile/.dockerignore`(신규) | `build`, `.dart_tool` 등 제외 |
| `scripts/gen-dev-certs.sh`(신규) | `openssl`로 자체 서명 인증서 1회 생성, `certs/`는 git-ignore |

### Flutter (`mobile/lib`)

| 항목 | 내용 |
|---|---|
| `auth_provider.dart` | `ApiClient(baseUrl: ...)`를 `kIsWeb ? '' : 'http://10.0.2.2:8080'`로 변경 (web은 같은 오리진 상대경로) |
| `checkin_screen.dart` | web 전용(`kIsWeb`, 테스트용 `forceWebMode` 오버라이드 파라미터로 생성자 주입 가능) 패널 추가:  ① 실제 위치 표시(읽기 전용, `Geolocator.getCurrentPosition()` 결과) + 새로고침 버튼  ② 체크인에 사용할 위도/경도 편집 필드(최초값은 ①로 자동 채움, 자유 수정 가능) |
| `_confirmCheckIn()` | web 모드일 때 패널의 편집된 위도/경도 값을 그대로 전송. 네이티브 모드는 기존 로직(직접 `Geolocator` 호출) 유지 |

## 데이터 흐름

1. (web 전용) 화면 진입 시 브라우저 Geolocation API로 실제 위치를 가져와 "실제 위치" 표시 + 편집 필드 초기값으로 채움
2. 사용자가 편집 필드 값을 그대로 두거나 임의의 값으로 수정
3. "네" 버튼 클릭 → 편집 필드의 위도/경도로 `/api/v1/attend/check-in` 호출 (네이티브는 기존과 동일하게 즉시 `Geolocator` 조회값 사용)
4. 반경 초과 시 백엔드가 반환하는 기존 에러 메시지를 그대로 화면에 노출 (클라이언트 거리 계산 로직 추가 없음)

## 에러 처리

- 브라우저 위치 권한 거부/조회 실패: 기존 네이티브 흐름과 동일한 안내 문구 재사용, "실제 위치" 영역은 미표시 상태 유지, 편집 필드는 수동 입력으로 채워 체크인 계속 가능
- 자체 서명 인증서로 인한 브라우저 경고: 문서화만 하고 코드로 처리하지 않음 (사용자가 브라우저에서 예외 처리)

## 검증 기준

- 기존 `checkin_screen_test.dart` 전체 그대로 통과 (네이티브 경로 무변경 확인)
- 신규 위젯 테스트(`forceWebMode: true`): 패널 노출, 실제 위치 자동 채움, 값 수정 후 체크인 API에 수정값 전달 확인
- `docker compose up -d --build` 후 `https://localhost` (admin-web), `https://localhost:8443` (mobile-web) 접속해 인증서 경고 통과 후 정상 로딩 확인
- LAN의 실제 폰 브라우저에서 `https://<호스트 PC IP>:8443`로 mobile-web 접속 → 로그인 → 체크인 화면에서 편집 필드로 반경 밖 좌표 입력 시 백엔드 에러 메시지 노출 확인
