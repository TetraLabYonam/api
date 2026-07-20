# 회원 본인 출석 이력 조회 — 설계 (짧은 형식)

## 이번 범위

attend 공백 4번: 회원이 본인의 이번 달 출석률과 날짜별 출석 이력을 조회하는 백엔드 API + 모바일 화면.

## 제외 범위

- 이번 달 외 기간(지난 달 등) 조회 — 나중에 필요하면 추가
- 관리자 관련 사항 (3번에서 이미 완료)

## 주요 계약

**`GET /api/v1/attend/history`** (기존 `AttendController`, 인증된 회원 기준)
- `{ attendanceRate: double, records: [{ scheduleDate, placeName, status }] }`
- 이번 달(1일~오늘) 고정, 기간 파라미터 없음.
- 기존 `AttendRepository.getAttendanceStatsByMemberId`(출석률 계산용 집계), `findByMemberIdAndDateRange`(날짜별 목록)를 그대로 재사용 — 새 리포지토리 쿼리 없음. `AttendService`에 `getHistory(Long memberId)` 메서드만 추가.
- 출석률 계산 로직은 기존 `AdminAttendanceService.getSummary()`와 동일한 정의: (PRESENT+LATE)/전체 * 100, 전체 0건이면 0%.

**모바일**: 체크인 화면에 "이번 달 출석 이력" 진입 버튼 추가 → 신규 화면에서 출석률 + 날짜별 목록(상태 텍스트) 렌더링.

## 데이터 흐름 / 에러 처리

```
1. 진입 버튼 탭 → GET /api/v1/attend/history
2. 200 → 출석률 + 목록 렌더링 (0건이면 "이번 달 출석 기록이 없습니다")
```

| 상황 | 응답 |
|---|---|
| 인증 없음 | 401 |
| 정상 | 200 |

## 검증 기준

- 출석률 계산이 관리자 대시보드와 동일한 정의로 나오는가
- 날짜별 목록이 본인 것만 나오는가 (다른 회원 기록 안 섞임)
- 기록 0건일 때도 정상 처리되는가 (0%, 빈 목록)
