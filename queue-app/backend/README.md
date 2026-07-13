# 번호표 백엔드 (참고용, 미구현)

`legacy-reference/`에는 기존 Room/TicketIssuance 관련 코드가 그대로
들어있지만, 이를 노출하던 컨트롤러가 전부 삭제되어 있어 현재 빌드
가능한 프로젝트가 아니다. 신규 구축 시 `SCHEMA_DESIGN.md`의 스키마를
기준으로 새로 설계한다 (PostgreSQL 사용 예정).

`legacy-reference/` 코드를 재활용할 경우 주의할 점: 전부 `package
com.example.attempt.*`를 그대로 쓰고 있어 재-네임스페이스가 필요하고,
`JwtTokenProvider`/`ResourceNotFoundException`처럼 senior-attendance-app
쪽에 남아있는 클래스를 import하는 파일이 있다 — 이 둘은 함께 옮겨온
게 아니므로 새 프로젝트에서 직접 다시 구현해야 한다 (관리자 인증을
독립적으로 새로 만든다는 결정과도 일치한다).
