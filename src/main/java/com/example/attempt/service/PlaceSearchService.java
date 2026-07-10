package com.example.attempt.service;

import com.example.attempt.domain.Place;
import com.example.attempt.domain.UnitType;
import com.example.attempt.dto.place.PlaceSummaryDto;
import com.example.attempt.repository.JobKeywordSynonymRepository;
import com.example.attempt.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사업단 유형별 일자리(Place) 검색
 * 1단계: 이름/설명/동의어 LIKE 검색
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceSearchService {

    private final PlaceRepository placeRepository;
    private final JobKeywordSynonymRepository synonymRepository;

    public List<PlaceSummaryDto> listByUnitType(UnitType unitType) {
        return placeRepository.findByUnitType(unitType).stream()
                .map(this::toDto)
                .toList();
    }

    public List<PlaceSummaryDto> search(UnitType unitType, String keyword) {
        return placeRepository.searchByUnitTypeAndKeyword(unitType, keyword).stream()
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
