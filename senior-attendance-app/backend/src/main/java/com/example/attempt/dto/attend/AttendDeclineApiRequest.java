package com.example.attempt.dto.attend;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 클라이언트가 보내는 결석 처리 요청 — memberId는 포함하지 않는다 (JWT에서 서버가 조회)
 */
@Data
public class AttendDeclineApiRequest {
    @NotNull(message = "일정 ID는 필수입니다.")
    private Long scheduleId;
}
