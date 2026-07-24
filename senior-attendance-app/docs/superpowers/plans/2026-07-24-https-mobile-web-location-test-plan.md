# HTTPS + Flutter 웹 빌드 위치 테스트 도구 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** admin-web/mobile-web nginx에 로컬용 HTTPS를 추가하고, Flutter 모바일 앱을 웹 빌드로 docker-compose에 추가해 실기기 없이 브라우저에서 출석 체크인(위치 오버라이드 포함)을 테스트할 수 있게 한다.

**Architecture:** (1) 자체 서명 인증서 1벌을 admin-web/mobile-web 두 nginx 컨테이너가 공유 마운트. (2) `mobile/`을 `flutter build web`로 빌드해 nginx로 정적 서빙하는 신규 `mobile-web` docker-compose 서비스 추가(같은 오리진 `/api/` 프록시로 CORS 회피). (3) `checkin_screen.dart`에 web 전용 위치 오버라이드 패널을 추가해 브라우저 Geolocation 결과를 보여주고, 체크인 시 전송할 위도/경도를 직접 수정할 수 있게 한다.

**Tech Stack:** nginx(자체 서명 TLS), Docker/docker-compose, Flutter web (`flutter build web`), `geolocator` web 구현체, Riverpod, `flutter_test`.

## Global Constraints

- 기존 80 포트(admin-web)는 유지한 채 443만 추가한다. 포트 제거/변경 금지.
- `mobile-web`은 admin-web과 호스트 포트가 겹치지 않도록 `8080:80`, `8443:443`을 쓴다.
- `mobile-web`도 admin-web과 동일하게 `/api/` → `backend:8080` 리버스 프록시로 같은 오리진을 만든다. CORS 설정은 추가하지 않는다.
- 백엔드(Java) 코드는 변경하지 않는다. 반경 초과 에러 메시지는 기존 `AttendService`의 메시지를 그대로 재사용한다.
- 인증서는 자체 서명만 다룬다(Let's Encrypt/도메인 인증서는 범위 밖).
- Flutter 네이티브(`kIsWeb == false`) 경로는 기존 동작과 100% 동일해야 한다. 기존 `checkin_screen_test.dart`의 모든 테스트는 수정 없이 그대로 통과해야 한다.
- 클라이언트에 거리 계산 로직을 새로 만들지 않는다. 반경 초과는 백엔드 에러 메시지를 그대로 노출한다.

---

### Task 1: 자체 서명 인증서 + admin-web HTTPS

**Files:**
- Create: `scripts/gen-dev-certs.sh`
- Modify: `.gitignore` (repo root, `/Users/rinaeshin/IdeaProjects/senior/.gitignore`)
- Modify: `senior-attendance-app/admin-web/nginx.conf`
- Modify: `senior-attendance-app/docker-compose.yml`

**Interfaces:**
- Produces: `senior-attendance-app/certs/dev.crt`, `senior-attendance-app/certs/dev.key` (파일 경로) — Task 2가 동일 경로를 mobile-web에도 마운트해서 재사용한다.

- [ ] **Step 1: 인증서 생성 스크립트 작성**

`senior-attendance-app/scripts/gen-dev-certs.sh` 생성:

```bash
#!/usr/bin/env bash
set -euo pipefail

CERT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/certs"
mkdir -p "$CERT_DIR"

openssl req -x509 -nodes -days 365 \
  -newkey rsa:2048 \
  -keyout "$CERT_DIR/dev.key" \
  -out "$CERT_DIR/dev.crt" \
  -subj "/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

echo "Generated $CERT_DIR/dev.crt and $CERT_DIR/dev.key"
```

실행 권한 부여:

```bash
chmod +x senior-attendance-app/scripts/gen-dev-certs.sh
```

- [ ] **Step 2: 스크립트 실행 및 인증서 생성 확인**

Run: `./senior-attendance-app/scripts/gen-dev-certs.sh`
Expected: `Generated .../certs/dev.crt and .../certs/dev.key` 출력, 두 파일 존재

검증:

```bash
openssl x509 -in senior-attendance-app/certs/dev.crt -noout -subject -ext subjectAltName
```

Expected: `subject=CN = localhost`와 `DNS:localhost, IP Address:127.0.0.1` 출력

- [ ] **Step 3: certs/ 디렉터리 git-ignore 처리**

`/Users/rinaeshin/IdeaProjects/senior/.gitignore`의 `### docker-compose 배포용 비밀값 ###` 섹션 바로 아래에 추가:

```gitignore
### docker-compose 배포용 비밀값 ###
.env
senior-attendance-app/.env

### 로컬 dev HTTPS 인증서 ###
senior-attendance-app/certs/
```

검증:

```bash
cd /Users/rinaeshin/IdeaProjects/senior && git status --short senior-attendance-app/certs
```

Expected: 아무 출력 없음 (무시되어 untracked 목록에 안 뜸)

- [ ] **Step 4: admin-web/nginx.conf에 443 리스너 추가**

`senior-attendance-app/admin-web/nginx.conf` 전체를 다음으로 교체:

```nginx
server {
    listen 80;
    listen 443 ssl;
    server_name _;

    ssl_certificate     /etc/nginx/certs/dev.crt;
    ssl_certificate_key /etc/nginx/certs/dev.key;

    root /usr/share/nginx/html;
    index index.html;

    # 백엔드 API는 같은 오리진의 /api/ 경로로 리버스 프록시한다.
    # (별도 CORS 설정 없이 쿠키 기반 refreshToken이 동작하도록)
    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # React Router의 클라이언트 사이드 라우팅을 위한 SPA fallback
    location / {
        try_files $uri /index.html;
    }
}
```

- [ ] **Step 5: docker-compose.yml의 admin-web 서비스에 443 포트 + 인증서 볼륨 추가**

`senior-attendance-app/docker-compose.yml`에서 다음 블록을:

```yaml
  admin-web:
    build:
      context: ./admin-web
      args:
        VITE_API_BASE_URL: ""
    restart: unless-stopped
    ports:
      - "80:80"
    depends_on:
      - backend
    mem_limit: 64m
```

다음으로 교체:

```yaml
  admin-web:
    build:
      context: ./admin-web
      args:
        VITE_API_BASE_URL: ""
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./certs:/etc/nginx/certs:ro
    depends_on:
      - backend
    mem_limit: 64m
```

- [ ] **Step 6: 빌드 및 동작 확인**

Run:

```bash
cd senior-attendance-app && docker compose up -d --build admin-web
sleep 3
curl -sk -o /dev/null -w "%{http_code}\n" https://localhost/
curl -s -o /dev/null -w "%{http_code}\n" http://localhost/
```

Expected: 두 명령 모두 `200` 출력 (기존 80 포트와 신규 443 포트 둘 다 정상 응답)

- [ ] **Step 7: 커밋**

```bash
cd /Users/rinaeshin/IdeaProjects/senior
git add .gitignore senior-attendance-app/scripts/gen-dev-certs.sh senior-attendance-app/admin-web/nginx.conf senior-attendance-app/docker-compose.yml
git commit -m "feat(infra): admin-web nginx에 자체 서명 HTTPS 추가"
```

---

### Task 2: mobile-web Docker 서비스 (Flutter 웹 빌드)

**Files:**
- Create: `senior-attendance-app/mobile/Dockerfile`
- Create: `senior-attendance-app/mobile/nginx.conf`
- Create: `senior-attendance-app/mobile/.dockerignore`
- Modify: `senior-attendance-app/docker-compose.yml`

**Interfaces:**
- Consumes: Task 1의 `senior-attendance-app/certs/dev.crt`, `dev.key` (동일 경로를 볼륨 마운트)
- Produces: `https://localhost:8443` (mobile-web 접속 지점) — Task 3의 수동 브라우저 검증에서 사용

- [ ] **Step 1: mobile/nginx.conf 작성**

`senior-attendance-app/mobile/nginx.conf` 생성:

```nginx
server {
    listen 80;
    listen 443 ssl;
    server_name _;

    ssl_certificate     /etc/nginx/certs/dev.crt;
    ssl_certificate_key /etc/nginx/certs/dev.key;

    root /usr/share/nginx/html;
    index index.html;

    # 백엔드 API는 같은 오리진의 /api/ 경로로 리버스 프록시한다 (CORS 불필요).
    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Flutter web의 클라이언트 사이드 라우팅을 위한 SPA fallback
    location / {
        try_files $uri /index.html;
    }
}
```

- [ ] **Step 2: mobile/.dockerignore 작성**

`senior-attendance-app/mobile/.dockerignore` 생성 (web 빌드에 불필요한 네이티브 플랫폼 디렉터리와 산출물을 빌드 컨텍스트에서 제외):

```
build
.dart_tool
.git
android
ios
linux
macos
windows
test
```

- [ ] **Step 3: mobile/Dockerfile 작성**

`senior-attendance-app/mobile/Dockerfile` 생성 (admin-web과 동일한 2단계 빌드 패턴):

```dockerfile
# ---- Build stage ----
FROM ghcr.io/cirruslabs/flutter:3.38.1 AS build
WORKDIR /app

COPY pubspec.yaml pubspec.lock ./
RUN flutter pub get

COPY . .
RUN flutter build web --release

# ---- Runtime stage ----
FROM nginx:1.27-alpine
COPY --from=build /app/build/web /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
```

- [ ] **Step 4: docker-compose.yml에 mobile-web 서비스 추가**

`senior-attendance-app/docker-compose.yml`의 `admin-web:` 서비스 블록 바로 다음(과 `volumes:` 최상위 키 이전)에 추가:

```yaml
  mobile-web:
    build:
      context: ./mobile
    restart: unless-stopped
    ports:
      - "8080:80"
      - "8443:443"
    volumes:
      - ./certs:/etc/nginx/certs:ro
    depends_on:
      - backend
    mem_limit: 64m
```

- [ ] **Step 5: 빌드 및 동작 확인**

Run:

```bash
cd senior-attendance-app && docker compose up -d --build mobile-web
sleep 3
docker compose ps mobile-web
curl -sk -o /dev/null -w "%{http_code}\n" https://localhost:8443/
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/
```

Expected: `mobile-web` 컨테이너 `Up` 상태, 두 curl 모두 `200` 출력 (Flutter web의 `index.html`)

- [ ] **Step 6: 커밋**

```bash
cd /Users/rinaeshin/IdeaProjects/senior
git add senior-attendance-app/mobile/Dockerfile senior-attendance-app/mobile/nginx.conf senior-attendance-app/mobile/.dockerignore senior-attendance-app/docker-compose.yml
git commit -m "feat(infra): Flutter 웹 빌드를 서빙하는 mobile-web docker-compose 서비스 추가"
```

---

### Task 3: Flutter 체크인 화면 웹 모드 (baseUrl 분기 + 위치 오버라이드 패널)

**Files:**
- Modify: `senior-attendance-app/mobile/lib/features/auth/auth_provider.dart`
- Modify: `senior-attendance-app/mobile/lib/features/checkin/checkin_screen.dart`
- Modify: `senior-attendance-app/mobile/test/features/checkin/checkin_screen_test.dart`

**Interfaces:**
- Consumes: 없음 (Task 1/2와 코드 의존성 없이 독립적으로 구현/테스트 가능. 실제 브라우저 동작 확인만 Task 2의 `mobile-web` 서비스가 필요)
- Produces: `CheckinScreen({Key? key, bool? forceWebMode})` — `forceWebMode`는 테스트에서 web 모드를 강제하기 위한 파라미터, 기본값은 `kIsWeb`

- [ ] **Step 1: auth_provider.dart의 baseUrl을 web/native로 분기**

`senior-attendance-app/mobile/lib/features/auth/auth_provider.dart`에서:

```dart
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/api_client.dart';
import '../../core/token_storage.dart';
import 'auth_repository.dart';

final apiClientProvider = Provider<ApiClient>((ref) {
  return ApiClient(baseUrl: 'http://10.0.2.2:8080');
});
```

를 다음으로 교체:

```dart
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/api_client.dart';
import '../../core/token_storage.dart';
import 'auth_repository.dart';

final apiClientProvider = Provider<ApiClient>((ref) {
  // web 빌드는 nginx가 같은 오리진의 /api/를 backend로 프록시하므로 상대경로(baseUrl'')를 쓴다.
  // 네이티브(Android 에뮬레이터)는 10.0.2.2가 호스트 PC를 가리키는 별칭이라 기존 값을 유지한다.
  return ApiClient(baseUrl: kIsWeb ? '' : 'http://10.0.2.2:8080');
});
```

이 변경은 별도 유닛 테스트를 추가하지 않는다 — `kIsWeb`은 `flutter test`(VM)에서 항상 `false`라 분기의 web 쪽을 이 안에서 검증할 방법이 없고, 실제 확인은 Task 2의 `https://localhost:8443` 브라우저 접속으로 한다.

- [ ] **Step 2: checkin_screen.dart에 forceWebMode 파라미터와 상태 필드 추가**

`senior-attendance-app/mobile/lib/features/checkin/checkin_screen.dart` 상단 import를:

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import '../../design_system/atm_bottom_action_bar.dart';
import '../../design_system/atm_colors.dart';
import '../attendance_history/attendance_history_screen.dart';
import '../auth/auth_provider.dart';
import '../auth/login_screen.dart';
import 'checkin_repository.dart';
```

다음으로 교체:

```dart
import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import '../../design_system/atm_bottom_action_bar.dart';
import '../../design_system/atm_colors.dart';
import '../attendance_history/attendance_history_screen.dart';
import '../auth/auth_provider.dart';
import '../auth/login_screen.dart';
import 'checkin_repository.dart';
```

클래스 선언부를:

```dart
class CheckinScreen extends ConsumerStatefulWidget {
  const CheckinScreen({super.key});

  @override
  ConsumerState<CheckinScreen> createState() => _CheckinScreenState();
}
```

다음으로 교체:

```dart
class CheckinScreen extends ConsumerStatefulWidget {
  const CheckinScreen({super.key, this.forceWebMode});

  /// 테스트에서 web 모드(위치 오버라이드 패널)를 강제로 켜기 위한 파라미터.
  /// null이면 실제 플랫폼 값인 [kIsWeb]을 따른다.
  final bool? forceWebMode;

  @override
  ConsumerState<CheckinScreen> createState() => _CheckinScreenState();
}
```

State 클래스 필드 선언부를:

```dart
class _CheckinScreenState extends ConsumerState<CheckinScreen> {
  bool _loadingToday = true;
  TodayAttend? _today;
  bool _checkingIn = false;
  bool _declining = false;
  String? _errorMessage;
  CheckinResult? _result;

  @override
  void initState() {
    super.initState();
    _loadToday();
  }
```

다음으로 교체:

```dart
class _CheckinScreenState extends ConsumerState<CheckinScreen> {
  bool _loadingToday = true;
  TodayAttend? _today;
  bool _checkingIn = false;
  bool _declining = false;
  String? _errorMessage;
  CheckinResult? _result;

  final TextEditingController _latController = TextEditingController();
  final TextEditingController _lngController = TextEditingController();
  Position? _actualPosition;
  bool _fetchingActualPosition = false;
  String? _actualPositionError;

  bool get _isWebMode => widget.forceWebMode ?? kIsWeb;

  @override
  void initState() {
    super.initState();
    _loadToday();
    if (_isWebMode) {
      _fetchActualPosition();
    }
  }

  @override
  void dispose() {
    _latController.dispose();
    _lngController.dispose();
    super.dispose();
  }

  Future<void> _fetchActualPosition() async {
    setState(() {
      _fetchingActualPosition = true;
      _actualPositionError = null;
    });
    try {
      final permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) {
        if (!mounted) return;
        setState(() => _actualPositionError = '위치 권한이 필요합니다.');
        return;
      }
      final position = await Geolocator.getCurrentPosition();
      if (!mounted) return;
      final isFirstFetch = _actualPosition == null;
      setState(() {
        _actualPosition = position;
        if (isFirstFetch) {
          _latController.text = position.latitude.toStringAsFixed(6);
          _lngController.text = position.longitude.toStringAsFixed(6);
        }
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _actualPositionError = '실제 위치를 가져오지 못했습니다.');
    } finally {
      if (mounted) setState(() => _fetchingActualPosition = false);
    }
  }
```

- [ ] **Step 3: _confirmCheckIn이 web 모드에서는 편집된 좌표를 쓰도록 변경**

`_confirmCheckIn` 메서드 전체를:

```dart
  Future<void> _confirmCheckIn() async {
    final scheduleId = _today?.scheduleId;
    if (scheduleId == null || _checkingIn) return;

    setState(() {
      _checkingIn = true;
      _errorMessage = null;
    });
    try {
      final permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) {
        if (!mounted) return;
        setState(() => _errorMessage = '위치 권한이 필요합니다. 설정에서 위치 권한을 허용해주세요.');
        return;
      }

      final position = await Geolocator.getCurrentPosition();
      final repo = CheckinRepository(dio: ref.read(apiClientProvider).dio);
      final result = await repo.checkIn(
        scheduleId: scheduleId,
        latitude: position.latitude,
        longitude: position.longitude,
      );
      if (!mounted) return;
      setState(() => _result = result);
    } catch (e) {
      if (mounted) {
        setState(() => _errorMessage = '위치 확인에 실패했습니다. 위치 서비스가 켜져 있는지 확인해주세요.');
      }
    } finally {
      if (mounted) setState(() => _checkingIn = false);
    }
  }
```

다음으로 교체:

```dart
  Future<void> _confirmCheckIn() async {
    final scheduleId = _today?.scheduleId;
    if (scheduleId == null || _checkingIn) return;

    setState(() {
      _checkingIn = true;
      _errorMessage = null;
    });
    try {
      double latitude;
      double longitude;

      if (_isWebMode) {
        final lat = double.tryParse(_latController.text);
        final lng = double.tryParse(_lngController.text);
        if (lat == null || lng == null) {
          if (!mounted) return;
          setState(() => _errorMessage = '위도/경도를 올바르게 입력해주세요.');
          return;
        }
        latitude = lat;
        longitude = lng;
      } else {
        final permission = await Geolocator.requestPermission();
        if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) {
          if (!mounted) return;
          setState(() => _errorMessage = '위치 권한이 필요합니다. 설정에서 위치 권한을 허용해주세요.');
          return;
        }
        final position = await Geolocator.getCurrentPosition();
        latitude = position.latitude;
        longitude = position.longitude;
      }

      final repo = CheckinRepository(dio: ref.read(apiClientProvider).dio);
      final result = await repo.checkIn(
        scheduleId: scheduleId,
        latitude: latitude,
        longitude: longitude,
      );
      if (!mounted) return;
      setState(() => _result = result);
    } catch (e) {
      if (mounted) {
        setState(() => _errorMessage = '위치 확인에 실패했습니다. 위치 서비스가 켜져 있는지 확인해주세요.');
      }
    } finally {
      if (mounted) setState(() => _checkingIn = false);
    }
  }
```

- [ ] **Step 4: build()에 web 전용 위치 오버라이드 패널 삽입**

`build` 메서드에서 다음 부분을:

```dart
                  if (canAct)
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        border: Border.all(color: AtmColors.border, width: 2),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _InfoRow(icon: Icons.place_outlined, label: '근무 장소', value: _today!.placeName ?? '-'),
                          const SizedBox(height: 16),
                          _InfoRow(
                            icon: Icons.access_time,
                            label: '근무 시간',
                            value: '${_today!.startTime ?? ''} — ${_today!.endTime ?? ''}',
                          ),
                        ],
                      ),
                    )
                  else
                    const Text('오늘은 예정된 출석이 없습니다', style: TextStyle(color: AtmColors.onSurfaceVariant, fontSize: 16)),
                  if (_errorMessage != null)
```

다음으로 교체:

```dart
                  if (canAct)
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        border: Border.all(color: AtmColors.border, width: 2),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _InfoRow(icon: Icons.place_outlined, label: '근무 장소', value: _today!.placeName ?? '-'),
                          const SizedBox(height: 16),
                          _InfoRow(
                            icon: Icons.access_time,
                            label: '근무 시간',
                            value: '${_today!.startTime ?? ''} — ${_today!.endTime ?? ''}',
                          ),
                        ],
                      ),
                    )
                  else
                    const Text('오늘은 예정된 출석이 없습니다', style: TextStyle(color: AtmColors.onSurfaceVariant, fontSize: 16)),
                  if (_isWebMode && canAct) ...[
                    const SizedBox(height: 20),
                    _WebLocationTestPanel(
                      actualPosition: _actualPosition,
                      actualPositionError: _actualPositionError,
                      fetching: _fetchingActualPosition,
                      onRefresh: _fetchActualPosition,
                      latController: _latController,
                      lngController: _lngController,
                    ),
                  ],
                  if (_errorMessage != null)
```

- [ ] **Step 5: _WebLocationTestPanel 위젯 추가**

파일 맨 끝(`_InfoRow` 클래스 뒤)에 추가:

```dart

class _WebLocationTestPanel extends StatelessWidget {
  final Position? actualPosition;
  final String? actualPositionError;
  final bool fetching;
  final VoidCallback onRefresh;
  final TextEditingController latController;
  final TextEditingController lngController;

  const _WebLocationTestPanel({
    required this.actualPosition,
    required this.actualPositionError,
    required this.fetching,
    required this.onRefresh,
    required this.latController,
    required this.lngController,
  });

  @override
  Widget build(BuildContext context) {
    final actualText = fetching
        ? '실제 위치 확인 중...'
        : actualPosition != null
            ? '실제 위치: ${actualPosition!.latitude.toStringAsFixed(6)}, ${actualPosition!.longitude.toStringAsFixed(6)}'
            : (actualPositionError ?? '실제 위치를 아직 가져오지 못했습니다.');

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        border: Border.all(color: AtmColors.border, width: 2),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '[테스트] 체크인 위치 조정',
            style: TextStyle(fontWeight: FontWeight.bold, color: AtmColors.primary),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(child: Text(actualText, key: const Key('actualPositionText'))),
              TextButton(
                onPressed: fetching ? null : onRefresh,
                child: const Text('새로고침'),
              ),
            ],
          ),
          const SizedBox(height: 12),
          TextField(
            key: const Key('latitudeField'),
            controller: latController,
            keyboardType: const TextInputType.numberWithOptions(decimal: true, signed: true),
            decoration: const InputDecoration(labelText: '위도(latitude)'),
          ),
          const SizedBox(height: 8),
          TextField(
            key: const Key('longitudeField'),
            controller: lngController,
            keyboardType: const TextInputType.numberWithOptions(decimal: true, signed: true),
            decoration: const InputDecoration(labelText: '경도(longitude)'),
          ),
        ],
      ),
    );
  }
}
```

- [ ] **Step 6: 기존 테스트가 그대로 통과하는지 확인 (회귀 없음)**

Run: `cd senior-attendance-app/mobile && flutter test test/features/checkin/checkin_screen_test.dart`
Expected: 기존 9개 테스트 모두 `PASS` (native 경로 무변화 확인 — `forceWebMode`를 지정하지 않았으므로 `kIsWeb`이 항상 `false`인 테스트 환경에서는 패널이 렌더링되지 않는다)

- [ ] **Step 7: web 모드 위젯 테스트 추가 — 패널 노출 + 실제 위치 자동 채움**

`senior-attendance-app/mobile/test/features/checkin/checkin_screen_test.dart`의 `main()` 함수 마지막 `testWidgets` 블록(로그아웃 관련 마지막 테스트) 뒤, `});`(main 함수를 닫는 괄호) 바로 앞에 추가:

```dart

  testWidgets('web 모드에서는 위치 테스트 패널이 보이고 실제 위치가 자동으로 채워진다', (tester) async {
    GeolocatorPlatform.instance = _FakeGeolocatorPlatform(
      permission: LocationPermission.whileInUse,
      position: _fakePosition(),
    );

    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          return jsonResponse('{"hasSchedule":true,"scheduleId":1,"placeName":"중앙공원"}');
        })),
      ],
      child: const MaterialApp(home: CheckinScreen(forceWebMode: true)),
    ));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('latitudeField')), findsOneWidget);
    expect(find.byKey(const Key('longitudeField')), findsOneWidget);

    final latField = tester.widget<TextField>(find.byKey(const Key('latitudeField')));
    final lngField = tester.widget<TextField>(find.byKey(const Key('longitudeField')));
    expect(latField.controller!.text, '35.300000');
    expect(lngField.controller!.text, '129.000000');

    expect(find.textContaining('실제 위치: 35.300000, 129.000000'), findsOneWidget);
  });

  testWidgets('web 모드가 아니면 위치 테스트 패널이 보이지 않는다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          return jsonResponse('{"hasSchedule":true,"scheduleId":1,"placeName":"중앙공원"}');
        })),
      ],
      child: const MaterialApp(home: CheckinScreen()),
    ));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('latitudeField')), findsNothing);
  });

  testWidgets('web 모드에서 위치 필드를 수정하면 체크인 시 수정된 값이 전달된다', (tester) async {
    GeolocatorPlatform.instance = _FakeGeolocatorPlatform(
      permission: LocationPermission.whileInUse,
      position: _fakePosition(),
    );
    Map? capturedBody;

    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          if (options.path == '/api/v1/attend/today') {
            return jsonResponse('{"hasSchedule":true,"scheduleId":1,"placeName":"중앙공원"}');
          }
          if (options.path == '/api/v1/attend/check-in') {
            capturedBody = options.data as Map;
          }
          return jsonResponse('{"success":true,"message":"출석 처리되었습니다."}');
        })),
      ],
      child: const MaterialApp(home: CheckinScreen(forceWebMode: true)),
    ));
    await tester.pumpAndSettle();

    await tester.enterText(find.byKey(const Key('latitudeField')), '10.123456');
    await tester.enterText(find.byKey(const Key('longitudeField')), '20.654321');
    await tester.tap(find.widgetWithText(ElevatedButton, '네'));
    await tester.pumpAndSettle();

    expect(capturedBody, isNotNull);
    expect(capturedBody!['latitude'], 10.123456);
    expect(capturedBody!['longitude'], 20.654321);
    expect(find.text('출석 처리되었습니다.'), findsOneWidget);
  });
```

- [ ] **Step 8: 신규 테스트 실행 확인**

Run: `cd senior-attendance-app/mobile && flutter test test/features/checkin/checkin_screen_test.dart`
Expected: 기존 9개 + 신규 3개 = 12개 테스트 모두 `PASS`

- [ ] **Step 9: 전체 mobile 테스트 스위트 회귀 확인**

Run: `cd senior-attendance-app/mobile && flutter test`
Expected: 모든 테스트 `PASS` (기존 다른 기능에 영향 없음)

- [ ] **Step 10: 커밋**

```bash
cd /Users/rinaeshin/IdeaProjects/senior
git add senior-attendance-app/mobile/lib/features/auth/auth_provider.dart senior-attendance-app/mobile/lib/features/checkin/checkin_screen.dart senior-attendance-app/mobile/test/features/checkin/checkin_screen_test.dart
git commit -m "feat(mobile): 체크인 화면에 web 전용 위치 오버라이드 테스트 패널 추가"
```

- [ ] **Step 11 (수동 검증, Task 2 완료 후에만 가능): 실제 브라우저에서 위치 오버라이드 확인**

```bash
cd senior-attendance-app && docker compose up -d --build mobile-web
```

브라우저에서 `https://localhost:8443` 접속 → 인증서 경고 통과 → 로그인 → 체크인 화면에서 "실제 위치"가 채워지는지, 위도/경도 필드를 반경 밖 값으로 바꾼 뒤 "네"를 눌렀을 때 백엔드의 "출석 가능한 위치가 아닙니다. (거리: ...)" 메시지가 그대로 노출되는지 확인.
