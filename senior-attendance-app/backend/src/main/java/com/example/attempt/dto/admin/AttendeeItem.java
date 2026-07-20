package com.example.attempt.dto.admin;

import com.example.attempt.domain.AttendStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 일정 출석자 1명에 대한 응답 DTO (관리자 일정 상세 조회용)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendeeItem {
    private Long attendId;
    private Long memberId;
    private String memberName;
    private AttendStatus status;
    private String note;
    private LocalDateTime attendedAt;
}
