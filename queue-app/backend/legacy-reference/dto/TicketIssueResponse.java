package com.example.attempt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 번호표 발급 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketIssueResponse {
    private Integer number;        // 발급받은 번호
    private Boolean duplicated;    // 중복 발급 여부
    private Integer lastNumber;    // 현재 진행 중인 번호
    private Long count;            // 총 대기 인원
}