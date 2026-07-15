package com.example.attempt.service;

import com.example.attempt.domain.AttendStatus;
import com.example.attempt.domain.UnitType;
import com.example.attempt.dto.admin.AttendanceSummaryResponse;
import com.example.attempt.repository.AttendRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminAttendanceServiceTest {

    private AttendRepository attendRepository;
    private AdminAttendanceService service;

    @BeforeEach
    void setup() {
        attendRepository = mock(AttendRepository.class);
        service = new AdminAttendanceService(attendRepository);
    }

    @Test
    void resolveStartDate_today_returnsSameDate() {
        LocalDate today = LocalDate.of(2026, 7, 15); // 수요일
        assertEquals(today, AdminAttendanceService.resolveStartDate("today", today));
    }

    @Test
    void resolveStartDate_week_returnsMondayOfThatWeek() {
        LocalDate wednesday = LocalDate.of(2026, 7, 15);
        LocalDate expectedMonday = LocalDate.of(2026, 7, 13);
        assertEquals(expectedMonday, AdminAttendanceService.resolveStartDate("week", wednesday));
    }

    @Test
    void resolveStartDate_week_whenTodayIsMonday_returnsSameDate() {
        LocalDate monday = LocalDate.of(2026, 7, 13);
        assertEquals(monday, AdminAttendanceService.resolveStartDate("week", monday));
    }

    @Test
    void resolveStartDate_month_returnsFirstDayOfMonth() {
        LocalDate today = LocalDate.of(2026, 7, 15);
        LocalDate expectedFirst = LocalDate.of(2026, 7, 1);
        assertEquals(expectedFirst, AdminAttendanceService.resolveStartDate("month", today));
    }

    @Test
    void resolveStartDate_unknownPeriod_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> AdminAttendanceService.resolveStartDate("year", LocalDate.now()));
    }

    @Test
    void getSummary_calculatesRatePerUnitType_andIncludesAllThreeTypesEvenWithNoData() {
        when(attendRepository.getAttendanceStatsByUnitTypeAndDateRange(any(), any())).thenReturn(List.of(
                new Object[]{UnitType.PUBLIC_INTEREST, AttendStatus.PRESENT, 3L},
                new Object[]{UnitType.PUBLIC_INTEREST, AttendStatus.ABSENT, 1L},
                new Object[]{UnitType.MARKET, AttendStatus.LATE, 2L},
                new Object[]{UnitType.MARKET, AttendStatus.PRESENT, 8L}
        ));

        List<AttendanceSummaryResponse> result = service.getSummary("today");

        assertEquals(3, result.size());

        AttendanceSummaryResponse publicInterest = result.stream()
                .filter(r -> r.getUnitType().equals("PUBLIC_INTEREST")).findFirst().orElseThrow();
        assertEquals("공익형", publicInterest.getLabel());
        assertEquals(75.0, publicInterest.getAttendanceRate(), 0.001); // 3 / (3+1) * 100

        AttendanceSummaryResponse market = result.stream()
                .filter(r -> r.getUnitType().equals("MARKET")).findFirst().orElseThrow();
        assertEquals(100.0, market.getAttendanceRate(), 0.001); // (2+8) / 10 * 100

        AttendanceSummaryResponse socialService = result.stream()
                .filter(r -> r.getUnitType().equals("SOCIAL_SERVICE")).findFirst().orElseThrow();
        assertEquals(0.0, socialService.getAttendanceRate(), 0.001); // 데이터 없음
    }
}
