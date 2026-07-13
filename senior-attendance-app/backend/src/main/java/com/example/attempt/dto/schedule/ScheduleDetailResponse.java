package com.example.attempt.dto.schedule;

import com.example.attempt.domain.AttendStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 일정 상세 조회 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleDetailResponse {

    /**
     * 일정 ID
     */
    private Long scheduleId;

    /**
     * 일정 제목
     */
    private String title;

    /**
     * 일정 설명
     */
    private String description;

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
     * 장소 정보
     */
    private PlaceInfo place;

    /**
     * 출석 통계
     */
    private AttendanceStats stats;

    /**
     * 참석자 목록
     */
    private List<AttendeeInfo> attendees;

    /**
     * 활성 상태
     */
    private Boolean isActive;

    /**
     * 생성 일시
     */
    private String createdAt;

    /**
     * 수정 일시
     */
    private String updatedAt;

    /**
     * 장소 정보
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlaceInfo {
        private Long placeId;
        private String name;
        private String address;
        private Double latitude;
        private Double longitude;
    }

    /**
     * 출석 통계
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttendanceStats {
        private long totalAttendees;        // 전체 참석 대상 인원
        private long presentCount;          // 출석 인원
        private long absentCount;           // 결석 인원
        private long lateCount;             // 지각 인원
        private long scheduledCount;        // 출석 예정 인원
        private long excusedCount;          // 사유 인정 인원
        private double attendanceRate;      // 출석률 (%)
    }

    /**
     * 참석자 정보
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttendeeInfo {
        private Long memberId;
        private String memberName;
        private String phoneNumber;
        private String unitName;
        private AttendStatus status;
        private String attendedAt;
        private String note;
    }
}
