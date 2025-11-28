package com.example.attempt.dto.schedule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 일정 생성 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleCreateRequest {

    /**
     * 일정 제목 (필수)
     */
    @NotBlank(message = "일정 제목은 필수입니다.")
    private String title;

    /**
     * 일정 설명
     */
    private String description;

    /**
     * 일정 날짜 목록 (필수)
     */
    @NotEmpty(message = "최소 하나의 날짜를 선택해야 합니다.")
    private List<LocalDate> dates;

    /**
     * 장소 ID (필수)
     */
    @NotNull(message = "장소는 필수입니다.")
    private Long placeId;

    /**
     * 시작 시간
     */
    private LocalTime startTime;

    /**
     * 종료 시간
     */
    private LocalTime endTime;

    /**
     * 특정 회원 ID 목록
     */
    private List<Long> memberIds;

    /**
     * 특정 사업단명 목록
     */
    private List<String> unitNames;

    /**
     * 전체 회원 여부
     */
    private Boolean allMembers;

    /**
     * 유효성 검증
     */
    public void validate() {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("일정 제목은 필수입니다.");
        }
        if (dates == null || dates.isEmpty()) {
            throw new IllegalArgumentException("최소 하나의 날짜를 선택해야 합니다.");
        }
        if (placeId == null) {
            throw new IllegalArgumentException("장소는 필수입니다.");
        }
        if (!hasAttendeeSelection()) {
            throw new IllegalArgumentException("참석자를 선택해야 합니다. (특정 회원, 사업단, 또는 전체 회원 중 선택)");
        }
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("종료 시간은 시작 시간보다 늦어야 합니다.");
        }
    }

    /**
     * 참석자 선택 여부 확인
     */
    private boolean hasAttendeeSelection() {
        return (allMembers != null && allMembers) ||
               (memberIds != null && !memberIds.isEmpty()) ||
               (unitNames != null && !unitNames.isEmpty());
    }

    /**
     * 전체 회원 선택 여부
     */
    public boolean isAllMembersSelected() {
        return Boolean.TRUE.equals(allMembers);
    }

    /**
     * 특정 회원 선택 여부
     */
    public boolean hasMemberIds() {
        return memberIds != null && !memberIds.isEmpty();
    }

    /**
     * 사업단 선택 여부
     */
    public boolean hasUnitNames() {
        return unitNames != null && !unitNames.isEmpty();
    }
}
