# 관리자용 개별 출석 관리 — 설계 (짧은 형식, constitution.md 준수)

## 이번 범위

attend 공백 3번: 관리자가 특정 일정의 출석자 개별 상태를 조회·수정할 수 있는 백엔드 API + admin-web 화면.

## 제외 범위

- 회원 본인 출석 이력 조회 (공백 4번)
- 일정 목록 브라우징 화면 — 1번 작업에서 "장소+날짜당 일정 1건"을 이미 확정했으므로, 장소+날짜를 고르면 일정이 0~1건으로 정해져 목록 화면이 불필요
- 체크인 날짜 검증, 위치 반경 이탈 처리 개선 (공백 5·6번)

## 주요 계약

**`GET /api/admin/schedules?placeId={id}&date={yyyy-MM-dd}`**
장소+날짜의 일정 1건과 출석자 목록을 반환. `{ scheduleId, title, scheduleDate, startTime, endTime, placeName, attendees: [{ attendId, memberId, memberName, status, note, attendedAt }] }`. 일정 없으면 404.

**`PATCH /api/admin/attend/{attendId}`**
`{ status, note }` → 기존 `AttendService.updateAttendStatus()` 재사용. 관리자는 회원 자기-서비스와 달리 어떤 `AttendStatus`로든 자유롭게 변경 가능. attendId 없으면 404.

**신규 리포지토리 메서드**: `ScheduleRepository.findByPlaceIdAndScheduleDate(placeId, date)` — 1번 작업의 `existsByPlaceIdAndScheduleDate`와 짝을 이룸.

**admin-web**: 신규 화면 1개("일정별 출석 관리") — 장소(기존 장소 목록 API 재사용)+날짜 선택 → 출석자 테이블 → 행 클릭 시 상태+사유 수정. 로비 화면에서 진입 링크 추가.

## 데이터 흐름 / 상태 변화

```
1. 장소+날짜 선택 → GET /api/admin/schedules
   - 없음 → 404, "해당 날짜에 일정이 없습니다"
   - 있음 → 출석자 테이블 렌더링
2. 행 수정 → PATCH /api/admin/attend/{attendId}
   - 없음 → 404
   - 성공 → 200, 목록 재조회
```

## 에러 처리

| 상황 | 응답 |
|---|---|
| 인증 없음 | 401 (기존 `/api/admin/**` 매처) |
| MEMBER 토큰 | 403 |
| 장소+날짜에 일정 없음 | 404 |
| attendId 없음 | 404 |
| 정상 처리 | 200 |

## 검증 기준

- 장소+날짜 조회가 정확히 그 일정만 반환하는가 (다른 장소/날짜 일정 안 섞이는가)
- 상태+사유 변경이 DB에 실제 반영되는가
- 401/403 권한 경계가 지켜지는가
- admin-web에서 수정 후 화면이 갱신되는가
