package com.example.attempt.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 방 수정 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomUpdateRequest {

    @Size(max = 100, message = "방 이름은 100자를 초과할 수 없습니다.")
    private String roomName;

    private Boolean isActive;

    private Integer currentNumber;
}
