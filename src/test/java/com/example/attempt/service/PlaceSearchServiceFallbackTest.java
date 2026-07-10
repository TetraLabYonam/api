package com.example.attempt.service;

import com.example.attempt.domain.UnitType;
import com.example.attempt.dto.place.PlaceSummaryDto;
import com.example.attempt.repository.JobKeywordSynonymRepository;
import com.example.attempt.repository.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaceSearchServiceFallbackTest {

    private PlaceRepository placeRepository;
    private JobKeywordSynonymRepository synonymRepository;
    private LlmJobSearchClient llmClient;
    private PlaceSearchService service;

    @BeforeEach
    void setup() {
        placeRepository = mock(PlaceRepository.class);
        synonymRepository = mock(JobKeywordSynonymRepository.class);
        llmClient = mock(LlmJobSearchClient.class);
        service = new PlaceSearchService(placeRepository, synonymRepository, Optional.of(llmClient));
    }

    @Test
    void searchWithFallback_usesStage1Result_whenPresent() {
        com.example.attempt.domain.Place place = new com.example.attempt.domain.Place("공원안전지킴이", "주소", 35.3, 129.0);
        place.setUnitType(UnitType.PUBLIC_INTEREST);
        when(placeRepository.searchByUnitTypeAndKeyword(UnitType.PUBLIC_INTEREST, "청소"))
                .thenReturn(List.of(place));

        List<PlaceSummaryDto> result = service.searchWithFallback(UnitType.PUBLIC_INTEREST, "청소");

        assertEquals(1, result.size());
        verifyNoInteractions(llmClient);
    }

    @Test
    void searchWithFallback_callsLlm_whenStage1Empty() {
        when(placeRepository.searchByUnitTypeAndKeyword(UnitType.PUBLIC_INTEREST, "학교 앞에서 깃발"))
                .thenReturn(List.of());
        com.example.attempt.domain.Place candidate = new com.example.attempt.domain.Place("스쿨존실버봉사단1", "주소", 35.3, 129.0);
        candidate.setUnitType(UnitType.PUBLIC_INTEREST);
        candidate.setId(7L);
        when(placeRepository.findByUnitType(UnitType.PUBLIC_INTEREST)).thenReturn(List.of(candidate));
        when(llmClient.pickBestMatch(eq("학교 앞에서 깃발"), anyList())).thenReturn(Optional.of(7L));
        when(placeRepository.findById(7L)).thenReturn(Optional.of(candidate));

        List<PlaceSummaryDto> result = service.searchWithFallback(UnitType.PUBLIC_INTEREST, "학교 앞에서 깃발");

        assertEquals(1, result.size());
        assertEquals("스쿨존실버봉사단1", result.get(0).getName());
    }
}
