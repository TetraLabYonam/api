package com.example.attempt.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 사업단 유형별 출석률 응답 DTO (관리자 대시보드)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSummaryResponse {
    private String unitType;
    private String label;
    private double attendanceRate;
}
