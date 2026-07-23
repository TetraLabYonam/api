package com.example.attempt.dto.place;

import com.example.attempt.domain.UnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 관리자가 장소 정보를 수정할 때 사용하는 요청 DTO. 전체 필드를 갱신한다(부분 수정 아님).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePlaceRequest {
    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    @NotBlank(message = "주소는 필수입니다.")
    private String address;

    @NotNull(message = "장소 유형(unitType)은 필수입니다.")
    private UnitType unitType;

    private String description;

    @NotNull(message = "위도는 필수입니다.")
    private Double latitude;

    @NotNull(message = "경도는 필수입니다.")
    private Double longitude;

    @NotNull(message = "active 값은 필수입니다.")
    private Boolean active;
}
