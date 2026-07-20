package com.example.attempt.dto.attend;

import com.example.attempt.domain.AttendStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 결석 처리 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendDeclineResponse {

    /**
     * 출석 ID
     */
    private Long attendId;

    /**
     * 출석 상태
     */
    private AttendStatus status;

    /**
     * 응답 메시지
     */
    private String message;

    /**
     * 성공 여부
     */
    private boolean success;
}
