package com.example.attempt.dto.admin;

import com.example.attempt.domain.AttendStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 관리자가 개별 출석자의 상태+사유를 수정할 때 사용하는 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAttendStatusRequest {
    private AttendStatus status;
    private String note;
}
