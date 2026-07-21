package com.example.attempt.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 회원 등록 결과 응답 DTO.
 * qrPayload는 "{employeeId}:{phoneNumber}"(평문) 조합으로, 이 응답에서만 노출되고 서버에는 저장되지 않는다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterMemberResponse {
    private Long employeeId;
    private String name;
    private Long placeId;
    private String qrPayload;
}
