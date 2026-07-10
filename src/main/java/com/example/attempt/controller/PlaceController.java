package com.example.attempt.controller;

import com.example.attempt.domain.UnitType;
import com.example.attempt.dto.place.PlaceSummaryDto;
import com.example.attempt.service.PlaceSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceSearchService placeSearchService;

    @GetMapping
    public List<PlaceSummaryDto> list(@RequestParam UnitType unitType,
                                       @RequestParam(required = false) String q) {
        if (q == null || q.isBlank()) {
            return placeSearchService.listByUnitType(unitType);
        }
        return placeSearchService.search(unitType, q);
    }
}
