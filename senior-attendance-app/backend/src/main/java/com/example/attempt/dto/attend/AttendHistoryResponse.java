package com.example.attempt.dto.attend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 로그인한 회원의 이번 달 출석률 + 날짜별 출석 이력 조회 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendHistoryResponse {
    private double attendanceRate;
    private List<AttendHistoryItem> records;
}
