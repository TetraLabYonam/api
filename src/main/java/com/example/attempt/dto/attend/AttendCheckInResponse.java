package com.example.attempt.dto.attend;

import com.example.attempt.domain.AttendStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 출석 체크인 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendCheckInResponse {

    /**
     * 출석 ID
     */
    private Long attendId;

    /**
     * 출석 상태
     */
    private AttendStatus status;

    /**
     * 출석 시간
     */
    private LocalDateTime attendedAt;

    /**
     * 응답 메시지
     */
    private String message;

    /**
     * 지각 여부
     */
    private boolean isLate;

    /**
     * 위치 확인 정보
     */
    private String locationInfo;

    /**
     * 거리 (미터)
     */
    private Double distance;

    /**
     * 성공 여부
     */
    private boolean success;
}
