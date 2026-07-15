package com.example.attempt.dto.attend;

import com.example.attempt.domain.Attend;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.format.DateTimeFormatter;

/**
 * 로그인한 회원의 "오늘 일정" 조회 응답 DTO.
 * 오늘 배정된 Attend가 없는 것은 정상 상태이므로 404가 아니라 hasSchedule=false로 표현한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendTodayResponse {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private boolean hasSchedule;
    private Long scheduleId;
    private String placeName;
    private String startTime;
    private String endTime;

    public static AttendTodayResponse none() {
        return AttendTodayResponse.builder().hasSchedule(false).build();
    }

    public static AttendTodayResponse of(Attend attend) {
        var schedule = attend.getSchedule();
        return AttendTodayResponse.builder()
                .hasSchedule(true)
                .scheduleId(schedule.getId())
                .placeName(schedule.getPlace().getName())
                .startTime(schedule.getStartTime() != null ? schedule.getStartTime().format(TIME_FORMAT) : null)
                .endTime(schedule.getEndTime() != null ? schedule.getEndTime().format(TIME_FORMAT) : null)
                .build();
    }
}
