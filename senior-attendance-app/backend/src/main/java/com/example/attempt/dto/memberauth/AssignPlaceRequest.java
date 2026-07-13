package com.example.attempt.dto.memberauth;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignPlaceRequest {
    @NotNull(message = "장소 ID는 필수입니다.")
    private Long placeId;
}
