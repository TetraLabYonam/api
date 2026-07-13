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

    @Test
    void searchWithFallback_returnsEmpty_whenLlmPicksNothing() {
        when(placeRepository.searchByUnitTypeAndKeyword(UnitType.PUBLIC_INTEREST, "알 수 없는 일"))
                .thenReturn(List.of());
        when(placeRepository.findByUnitType(UnitType.PUBLIC_INTEREST)).thenReturn(List.of());
        when(llmClient.pickBestMatch(eq("알 수 없는 일"), anyList())).thenReturn(Optional.empty());

        List<PlaceSummaryDto> result = service.searchWithFallback(UnitType.PUBLIC_INTEREST, "알 수 없는 일");

        assertTrue(result.isEmpty());
    }

    @Test
    void searchWithFallback_ignoresLlmResult_whenIdIsOutsideCandidateSet() {
        // LLM이 후보 목록에 없는 id(다른 사업단 소속이거나 환각)를 반환하면 무시해야 한다 —
        // 그렇지 않으면 unitType 범위를 벗어난 장소가 응답에 섞여 나갈 수 있다.
        when(placeRepository.searchByUnitTypeAndKeyword(UnitType.PUBLIC_INTEREST, "학교 앞에서 깃발"))
                .thenReturn(List.of());
        com.example.attempt.domain.Place candidate = new com.example.attempt.domain.Place("스쿨존실버봉사단1", "주소", 35.3, 129.0);
        candidate.setUnitType(UnitType.PUBLIC_INTEREST);
        candidate.setId(7L);
        when(placeRepository.findByUnitType(UnitType.PUBLIC_INTEREST)).thenReturn(List.of(candidate));
        // LLM이 후보 목록(id=7)에 없는 id=999를 반환 (환각 또는 다른 unitType 소속)
        when(llmClient.pickBestMatch(eq("학교 앞에서 깃발"), anyList())).thenReturn(Optional.of(999L));

        List<PlaceSummaryDto> result = service.searchWithFallback(UnitType.PUBLIC_INTEREST, "학교 앞에서 깃발");

        assertTrue(result.isEmpty());
        verify(placeRepository, never()).findById(999L);
    }
}
