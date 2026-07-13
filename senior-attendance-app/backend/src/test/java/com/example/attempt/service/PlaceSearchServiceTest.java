package com.example.attempt.service;

import com.example.attempt.domain.JobKeywordSynonym;
import com.example.attempt.domain.Place;
import com.example.attempt.domain.UnitType;
import com.example.attempt.dto.place.PlaceSummaryDto;
import com.example.attempt.repository.JobKeywordSynonymRepository;
import com.example.attempt.repository.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaceSearchServiceTest {

    private PlaceRepository placeRepository;
    private PlaceSearchService service;

    @BeforeEach
    void setup() {
        placeRepository = mock(PlaceRepository.class);
        JobKeywordSynonymRepository synonymRepository = mock(JobKeywordSynonymRepository.class);
        service = new PlaceSearchService(placeRepository, synonymRepository);
    }

    @Test
    void search_delegatesToRepositoryWithUnitTypeAndKeyword() {
        Place place = new Place("공원안전지킴이", "주소", 35.3, 129.0);
        place.setUnitType(UnitType.PUBLIC_INTEREST);
        when(placeRepository.searchByUnitTypeAndKeyword(UnitType.PUBLIC_INTEREST, "청소"))
                .thenReturn(List.of(place));

        List<PlaceSummaryDto> result = service.search(UnitType.PUBLIC_INTEREST, "청소");

        assertEquals(1, result.size());
        assertEquals("공원안전지킴이", result.get(0).getName());
        assertEquals(UnitType.PUBLIC_INTEREST, result.get(0).getUnitType());
    }

    @Test
    void search_returnsEmptyList_whenNoMatch() {
        when(placeRepository.searchByUnitTypeAndKeyword(UnitType.MARKET, "존재안함"))
                .thenReturn(List.of());

        List<PlaceSummaryDto> result = service.search(UnitType.MARKET, "존재안함");

        assertTrue(result.isEmpty());
    }
}
