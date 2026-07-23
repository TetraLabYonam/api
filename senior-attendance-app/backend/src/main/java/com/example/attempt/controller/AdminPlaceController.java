package com.example.attempt.controller;

import com.example.attempt.domain.Place;
import com.example.attempt.dto.place.CreatePlaceRequest;
import com.example.attempt.dto.place.PlaceSummaryDto;
import com.example.attempt.dto.place.UpdatePlaceRequest;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.PlaceRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping
    public PlaceSummaryDto create(@Valid @RequestBody CreatePlaceRequest request) {
        Place place = new Place(request.getName(), request.getAddress(), request.getLatitude(), request.getLongitude());
        place.setUnitType(request.getUnitType());
        place.setDescription(request.getDescription());
        place = placeRepository.save(place);
        return toDto(place);
    }

    @PatchMapping("/{id}")
    public PlaceSummaryDto update(@PathVariable Long id, @Valid @RequestBody UpdatePlaceRequest request) {
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("장소를 찾을 수 없습니다. ID: " + id));

        place.setName(request.getName());
        place.setAddress(request.getAddress());
        place.setUnitType(request.getUnitType());
        place.setDescription(request.getDescription());
        place.setLatitude(request.getLatitude());
        place.setLongitude(request.getLongitude());
        place.setActive(request.getActive());
        place = placeRepository.save(place);
        return toDto(place);
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
                .active(place.isActive())
                .build();
    }
}
