package com.example.attempt.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 관리자 회원 목록 조회용 요약 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberSummaryResponse {
    private Long employeeId;
    private String name;
    private Long placeId;
    private String placeName;
    private boolean active;
}
