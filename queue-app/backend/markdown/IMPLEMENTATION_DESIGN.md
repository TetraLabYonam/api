# 공용 번호표 백엔드 구현 내역

## 동작 범위

- Spring Boot 3 / Java 21 / PostgreSQL / Flyway 기반 실행 가능한 백엔드다.
- 손님은 `jobId`와 그 일자리의 열린 `sessionUid`를 모두 지정해야 번호를
  발급받는다. 사업단 또는 일자리만으로 발급하는 API는 없다.
- 모든 일자리와 접수 세션은 `global_ticket_counter`의 번호를 공유한다.
- Redis, WebSocket, 메시지 브로커는 사용하지 않는다.

## 주요 API

- `GET /api/v1/jobs`: 공개된 OPEN 일자리 목록
- `POST /api/v1/jobs/{jobId}/ticket-sessions/{sessionUid}/tickets`: 공용 번호 발급
- `POST /api/v1/admin/jobs`: 일자리 생성
- `POST /api/v1/admin/jobs/{jobId}/ticket-sessions`: 접수 세션 생성
- `POST /api/v1/admin/ticket-sessions/{sessionUid}/close`: 접수 세션 마감

관리 API는 `QUEUE_ADMIN_TOKEN`을 사용하는 bootstrap Bearer 인증으로 보호한다.
운영 사용자·권한 관리가 추가되면 독립 JWT/RBAC로 교체해야 한다.

## 정확성 및 개인정보

발급 트랜잭션은 세션, 일자리, 전역 카운터 순으로 PostgreSQL 행을 잠근다.
같은 세션과 전화번호의 재요청은 세션이 닫힌 뒤에도 기존 번호를 반환한다.
전화번호는 E.164로 정규화한 뒤 HMAC-SHA-256으로 변환하며 원문을 저장하지
않는다. 활성 키와 이전 버전 키 후보를 조회해 키 회전 중 중복 발급을 막는다.

## 실행

필수 환경 변수:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `QUEUE_HMAC_KEY`, `QUEUE_HMAC_KEY_VERSION`
- `QUEUE_ADMIN_TOKEN`

```shell
gradle --no-daemon test bootJar
gradle bootRun
```

애플리케이션 설정은 `backend/src/main/resources/application.yml`, 초기 스키마는
`backend/src/main/resources/db/migration/V1__init.sql`에 있다. Actuator health와
Prometheus metric endpoint를 제공하며 전화번호와 HMAC은 로그·metric label에
포함하지 않는다.

## IaC

`terraform/`은 AWS 배포 기준이다. 실제 계정의 VPC, ECS, RDS PostgreSQL,
Secrets Manager 및 관측성 값은 환경별 변수로 주입해야 한다.
