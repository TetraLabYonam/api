package com.example.attempt.controller;

import com.example.attempt.domain.Place;
import com.example.attempt.dto.place.PlaceSummaryDto;
import com.example.attempt.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/places")
@RequiredArgsConstructor
public class AdminPlaceController {

    private final PlaceRepository placeRepository;

    @GetMapping
    public List<PlaceSummaryDto> list() {
        return placeRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    private PlaceSummaryDto toDto(Place place) {
        return PlaceSummaryDto.builder()
                .id(place.getId())
                .name(place.getName())
                .address(place.getAddress())
                .unitType(place.getUnitType())
                .description(place.getDescription())
                .latitude(place.getLatitude())
                .longitude(place.getLongitude())
                .build();
    }
}
