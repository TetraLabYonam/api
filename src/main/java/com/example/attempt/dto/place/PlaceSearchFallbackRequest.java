package com.example.attempt.dto.place;

import com.example.attempt.domain.UnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlaceSearchFallbackRequest {
    @NotNull(message = "사업단 유형은 필수입니다.")
    private UnitType unitType;

    @NotBlank(message = "검색어는 필수입니다.")
    private String q;
}
