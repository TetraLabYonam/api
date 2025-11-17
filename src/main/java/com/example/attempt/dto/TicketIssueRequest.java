package com.example.attempt.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 번호표 발급 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketIssueRequest {
    private String userDeviceId;  // 사용자 디바이스 ID
}