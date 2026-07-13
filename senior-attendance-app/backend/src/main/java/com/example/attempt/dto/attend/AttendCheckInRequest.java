package com.example.attempt.dto.attend;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 출석 체크인 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendCheckInRequest {

    /**
     * 일정 ID (필수)
     */
    @NotNull(message = "일정 ID는 필수입니다.")
    private Long scheduleId;

    /**
     * 회원 ID (필수)
     */
    @NotNull(message = "회원 ID는 필수입니다.")
    private Long memberId;

    /**
     * 현재 위도 (필수)
     */
    @NotNull(message = "위도는 필수입니다.")
    private Double latitude;

    /**
     * 현재 경도 (필수)
     */
    @NotNull(message = "경도는 필수입니다.")
    private Double longitude;
}
