# 번호표 큐 — 신규 데이터 모델 설계

`legacy-reference/`의 기존 `Room`/`TicketIssuance`는 "사업단을 고르고
번호표를 뽑는" 단순 대기열 모델이었지만, 실제 업무는 다르다: 3개
사업단에 각각 미배정 일자리가 여러 개 있고, 번호표 방은 그 일자리
하나하나에 대한 접수 창구다. 관리자가 방을 만들면 그 관리자가 방의
소유자가 되고, 어르신이 그 일자리(방)를 선택해 들어가는 순간 방
소유자가 그 어르신의 접수 기록에 그대로 남는다. 방은 일시적이라
언제든 닫히고 삭제될 수 있으므로, 접수 이력은 방을 FK로 참조하지
않고 그 시점의 값을 그대로 복사해서 영구 보존한다.

## 테이블

```
TicketRoom (방 — 삭제 가능)
- id
- roomUid
- unitType (varchar)       -- 이 방이 속한 사업단
- createdByAdmin (varchar) -- 방을 만든 관리자
- createdAt
- closedAt                 -- null이면 열린 상태

TicketDrawRecord (접수 이력 — 영구 보존, 방 삭제와 무관)
- id
- phoneLastDigits (varchar) -- 어르신 전화번호 뒷자리
- unitType (varchar)        -- 접수 시점 사업단 스냅샷
- ticketNumber (Long)
- adminName (varchar)       -- 접수 시점 방 소유 관리자 스냅샷
- drawnAt
```

`TicketDrawRecord`가 `TicketRoom`을 FK로 참조하지 않는 것이 핵심이다
— 방이 삭제되어도 "누가 언제 몇 번을 받았고 어느 사업단/어느
관리자였는지"는 영구히 조회 가능해야 하기 때문이다.

## DB 엔진: PostgreSQL

시니어 근태관리 쪽은 MariaDB를 유지하지만, 이 프로젝트는 백엔드를
사실상 새로 만드는 것이므로 PostgreSQL을 채택한다. MariaDB는
`lower_case_table_names=0` 환경(리눅스 기본값)에서 테이블명이
대소문자를 구분해 파일시스템 파일명과 매핑되는데, 시니어
근태관리 쪽 개발 중 마이그레이션 파일에 소문자 테이블명을 썼다가
반복적으로 걸린 버그가 정확히 이 문제였다. PostgreSQL은 따옴표
없는 식별자를 전부 소문자로 정규화해 이 문제 자체가 발생하지
않는다.

## 관리자 인증

시니어 근태관리 쪽과 완전히 독립된 Admin/JWT/RefreshToken 체계를
새로 구축한다 (SSO 없음). 근태관리 선생님과 번호표 방을 만드는
관리자는 역할 자체가 다른 사람일 가능성이 높고, 두 시스템을
독립 배포하는 것이 목표이므로 인증 서버를 공유하지 않는다.

## 이번 분할 작업에서 하지 않은 것

- 컨트롤러 실제 구현 (`legacy-reference/controller/DeviceController.java`
  참고 가능하나 새 스키마 기준으로 다시 작성해야 함)
- 신규 Gradle 프로젝트 스캐폴드, PostgreSQL 연결 설정
- 관리자/회원 모바일 앱 (`../admin-mobile/`, `../member-mobile/`)
