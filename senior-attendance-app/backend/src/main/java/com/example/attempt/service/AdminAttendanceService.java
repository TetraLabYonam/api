package com.example.attempt.service;

import com.example.attempt.domain.AttendStatus;
import com.example.attempt.domain.UnitType;
import com.example.attempt.dto.admin.AttendanceSummaryResponse;
import com.example.attempt.repository.AttendRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자 대시보드용 사업단 유형별 출석률 집계 서비스
 */
@Service
public class AdminAttendanceService {

    private final AttendRepository attendRepository;

    public AdminAttendanceService(AttendRepository attendRepository) {
        this.attendRepository = attendRepository;
    }

    public List<AttendanceSummaryResponse> getSummary(String period) {
        LocalDate today = LocalDate.now();
        LocalDate start = resolveStartDate(period, today);

        List<Object[]> rows = attendRepository.getAttendanceStatsByUnitTypeAndDateRange(start, today);

        // [0] = 출석(PRESENT+LATE) 건수, [1] = 전체 건수
        Map<UnitType, long[]> counts = new EnumMap<>(UnitType.class);
        for (UnitType type : UnitType.values()) {
            counts.put(type, new long[2]);
        }

        for (Object[] row : rows) {
            UnitType unitType = (UnitType) row[0];
            AttendStatus status = (AttendStatus) row[1];
            long count = (Long) row[2];

            long[] bucket = counts.get(unitType);
            bucket[1] += count;
            if (status == AttendStatus.PRESENT || status == AttendStatus.LATE) {
                bucket[0] += count;
            }
        }

        List<AttendanceSummaryResponse> result = new ArrayList<>();
        for (UnitType type : UnitType.values()) {
            long[] bucket = counts.get(type);
            double rate = bucket[1] == 0 ? 0.0 : (bucket[0] * 100.0) / bucket[1];
            result.add(new AttendanceSummaryResponse(type.name(), type.getDescription(), rate));
        }
        return result;
    }

    static LocalDate resolveStartDate(String period, LocalDate today) {
        return switch (period) {
            case "today" -> today;
            case "week" -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "month" -> today.withDayOfMonth(1);
            default -> throw new IllegalArgumentException("알 수 없는 period 값입니다: " + period);
        };
    }
}
