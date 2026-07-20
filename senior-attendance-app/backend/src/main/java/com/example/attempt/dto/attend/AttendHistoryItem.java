package com.example.attempt.dto.attend;

import com.example.attempt.domain.AttendStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 회원 자기-서비스 출석 이력 1건에 대한 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendHistoryItem {
    private LocalDate scheduleDate;
    private String placeName;
    private AttendStatus status;
}
