# 공용 번호표 구현 계획

## 업무 불변 조건

1. 손님은 정확한 일자리 하나를 선택하고, 그 일자리의 열린 접수 세션을
   지정한 뒤 공용 번호표를 발급받는다.
2. 사업단만 선택하거나 서버가 임의로 일자리·세션을 고르는 발급은 허용하지
   않는다.
3. 모든 일자리와 세션은 하나의 전역 번호 범위를 공유한다.
4. 같은 세션에서 같은 전화번호의 재시도는 세션 마감 후에도 기존 번호를
   반환한다.
5. 발급 이력은 일자리명, 사업단, 전화번호 끝 4자리와 발급 시각을 snapshot으로
   보존한다. 전화번호 원문은 저장하거나 관측성 데이터에 기록하지 않는다.

## 구현 단계

### 1. 실행 기반

- Spring Boot 3, Java 21, PostgreSQL, Flyway Gradle 프로젝트를 구성한다.
- Redis, WebSocket, Kafka 등 현재 규모에 불필요한 인프라는 추가하지 않는다.
- JPA 자동 DDL 대신 `V1__init.sql`을 유일한 clean baseline으로 사용한다.

### 2. 데이터 모델

- `job_postings`: 일자리와 사업단, OPEN/CLOSED 상태
- `ticket_sessions`: 접수 회차; 일자리당 열린 세션은 부분 유니크 인덱스로 하나
- `global_ticket_counter`: `singleton=true`인 전역 카운터 한 행
- `draw_records`: 전역 번호, 세션/일자리 복합 참조, HMAC과 snapshot 이력

`draw_records.global_number`는 전역 UNIQUE이며 세션과 일자리의 조합도 복합
외래키로 검증한다. HMAC은 32바이트, 끝 4자리는 숫자만 허용한다.

### 3. 발급 트랜잭션

1. E.164 전화번호를 정규화하고 활성·이전 HMAC 후보를 계산한다.
2. 기존 record를 먼저 조회해 있으면 lifecycle 상태와 무관하게 반환한다.
3. 신규 발급은 `ticket_sessions → job_postings → global_ticket_counter` 순서로
   `FOR UPDATE` 잠금을 획득한다.
4. 잠금 안에서 중복과 OPEN 상태, path의 정확한 일자리 관계를 다시 검증한다.
5. 전역 번호 증가와 immutable record 삽입을 같은 트랜잭션에서 commit한다.
6. `Long.MAX_VALUE`는 503으로 거절하고 상태를 변경하지 않는다.

### 4. API 및 인증

- `GET /api/v1/jobs`는 OPEN 일자리와 발급에 필요한 열린 `sessionUid`를 반환한다.
- `POST /api/v1/jobs/{jobId}/ticket-sessions/{sessionUid}/tickets`만 공개 발급 경로다.
- 관리자 일자리·세션 변경 API는 별도 bootstrap Bearer token으로 보호한다.
  운영 공개 전에는 관리자 JWT/RBAC 또는 사설망·mTLS 방식으로 교체한다.
- 공개 발급은 전화번호 소유 확인이 없는 현 업무 계약이다. 외부 공개 서비스로
  전환할 경우 OTP, rate limit과 abuse 방지를 별도 보안 단계로 추가한다.

### 5. 개인정보 및 키 운영

- 전화번호는 ASCII 공백·하이픈·괄호만 제거한 후
  `^\+[1-9][0-9]{7,14}$`를 만족해야 한다.
- HMAC key와 관리자 token은 32바이트 이상이며 환경/Secrets Manager에서만
  주입한다.
- rolling key rotation은 모든 task에 active+previous key 집합을 먼저 배포하고,
  그 다음 active version을 전환하며, 이전 키는 최대 재시도 기간까지 보존한다.
  혼합 버전에서 이전 키 집합이 보장되지 않으면 rotation을 진행하지 않는다.

### 6. 관측성과 IaC

- 발급 결과와 session/job lock wait를 낮은 cardinality metric으로 기록한다.
- 전화번호, HMAC, job/session 식별자는 metric label이나 로그에 넣지 않는다.
- Terraform은 기존 VPC에 ALB, private ECS Fargate 2~4개, private Multi-AZ RDS,
  Secrets Manager, CloudWatch 로그를 구성한다.
- RDS master password는 AWS 관리형 secret을 사용하며 Terraform state에 평문을
  넣지 않는다.

## 검증 기준

- Gradle unit test와 bootJar가 성공한다.
- Testcontainers PostgreSQL에서 Flyway migration이 성공한다.
- 서로 다른 일자리·세션의 100개 동시 요청이 전역 1~100을 중복 없이 발급한다.
- 발급 후 세션 마감과 재시도는 같은 번호를 반환하고 카운터를 늘리지 않는다.
- 잘못된 일자리/세션 조합과 overflow는 record/counter를 변경하지 않는다.
- 관리자 API는 token 누락·오류를 거절하고 정확한 token만 허용한다.
- Terraform은 설치된 환경에서 `fmt -check`, `init -backend=false`, `validate`를
  통과해야 한다.

## 운영 전 후속 보안 게이트

현재 구현은 내부 접수 시스템의 bootstrap 범위다. 인터넷 공개 운영 전에는
관리자 신원 기반 JWT/RBAC, 공개 발급 OTP/rate limiting, audit log, Prometheus
수집/경보 및 actuator 접근 제한을 완료해야 한다.
