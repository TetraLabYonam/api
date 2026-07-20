package com.example.attempt.dto.schedule;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * 일정 생성 요청 DTO — 단건(startDate==endDate) 또는 반복(daysOfWeek 지정) 생성을 모두 표현한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateScheduleRequest {

    @NotNull(message = "장소 ID는 필수입니다.")
    private Long placeId;

    @NotNull(message = "제목은 필수입니다.")
    private String title;

    private String description;

    @NotNull(message = "시작 날짜는 필수입니다.")
    private LocalDate startDate;

    private LocalDate endDate;

    private Set<DayOfWeek> daysOfWeek;

    @NotNull(message = "시작 시간은 필수입니다.")
    private LocalTime startTime;

    @NotNull(message = "종료 시간은 필수입니다.")
    private LocalTime endTime;
}
