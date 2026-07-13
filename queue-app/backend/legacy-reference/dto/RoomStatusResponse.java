package com.example.attempt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 방 현황 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomStatusResponse {
    private String roomId;          // 방 ID
    private String roomName;        // 방 이름
    private Integer currentNumber;  // 현재 번호
    private Integer lastIssuedNumber; // 마지막 발급 번호
    private Long waitingCount;      // 대기 인원
    private Boolean isActive;       // 활성화 상태
}