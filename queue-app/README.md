# 번호표 큐 앱

번호표 발급·호출 시스템입니다. `backend/`는 Spring Boot API, `mobile/`은 이용자와 관리자 Flutter 앱입니다.

## Docker Compose 로컬 실행

Docker와 Compose가 설치된 환경에서 실제 로컬 환경 파일을 만든 뒤 한 명령으로 시작합니다.

```shell
cp .env.example .env
docker compose up --build
```

웹 UI: 기본 <http://localhost:8080>. 웹 컨테이너의 Nginx가 `/api/`와 `/actuator/`를 API로 프록시하므로 브라우저 CORS 설정이 필요 없습니다. DB는 기본적으로 호스트에 노출하지 않습니다. 8080 포트가 사용 중이면 `.env`의 `WEB_PORT=8081`처럼 변경합니다.

```shell
docker compose logs -f              # 로그
docker compose stop                 # 일시 중지
docker compose down                 # 컨테이너 제거
docker compose down -v              # 컨테이너와 DB 데이터 초기화
```

`.env`의 값은 로컬 전용 합성 값으로 유지하고 운영 비밀값을 저장소에 커밋하지 마세요.
