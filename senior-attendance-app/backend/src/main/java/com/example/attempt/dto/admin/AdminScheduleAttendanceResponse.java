package com.example.attempt.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 장소+날짜의 일정 1건과 출석자 목록 응답 DTO (관리자용)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminScheduleAttendanceResponse {
    private Long scheduleId;
    private String title;
    private LocalDate scheduleDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String placeName;
    private List<AttendeeItem> attendees;
}
