package com.example.attempt.dto.schedule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 일정 생성 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleCreateResponse {

    /**
     * 생성된 일정 목록
     */
    private List<ScheduleSummary> schedules;

    /**
     * 생성된 일정 개수
     */
    private int totalSchedulesCreated;

    /**
     * 일정당 참석 대상 인원 수
     */
    private int totalAttendeesPerSchedule;

    /**
     * 응답 메시지
     */
    private String message;

    /**
     * 일정 요약 정보
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleSummary {

        /**
         * 일정 ID
         */
        private Long scheduleId;

        /**
         * 일정 제목
         */
        private String title;

        /**
         * 일정 날짜
         */
        private LocalDate scheduleDate;

        /**
         * 시작 시간
         */
        private LocalTime startTime;

        /**
         * 종료 시간
         */
        private LocalTime endTime;

        /**
         * 장소명
         */
        private String placeName;

        /**
         * 참석 대상 인원 수
         */
        private int attendeeCount;

        /**
         * 생성 일시
         */
        private String createdAt;
    }
}
