package com.example.attempt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 방 생성 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomCreateRequest {

    @NotBlank(message = "방 이름은 필수입니다.")
    @Size(max = 100, message = "방 이름은 100자를 초과할 수 없습니다.")
    private String roomName;

    private String roomUid; // 선택사항 (없으면 자동 생성)
}
