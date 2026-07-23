package com.example.attempt.dto.place;

import com.example.attempt.domain.UnitType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceSummaryDto {
    private Long id;
    private String name;
    private String address;
    private UnitType unitType;
    private String description;
    private Double latitude;
    private Double longitude;
    private boolean active;
}
