# 번호표 큐 앱

이 폴더는 번호표 발급/호출 시스템을 위한 자리다. 기존 백엔드 컨트롤러는
2025년 12월 커밋(`9cc1533`)에서 삭제되어, `backend/legacy-reference/`
아래의 코드는 참고용일 뿐 실제로 작동하지 않는다.

- `backend/` — 참고용 레거시 코드 + 신규 스키마 설계 문서. 실제 Gradle
  프로젝트로 재구축하는 작업은 별도 계획에서 진행한다.
- `admin-mobile/` — 아직 없음. 신규 개발 예정.
- `member-mobile/` — 아직 없음. 신규 개발 예정.

설계 배경: `docs/superpowers/specs/2026-07-13-project-split-senior-queue-design.md`
(저장소 루트 `docs/`, 이 문서 자체는 시니어 근태관리 쪽으로 옮기지 않고
루트에 남겨진 원본을 참조한다 — 두 시스템 모두에 관련된 설계이기 때문).
