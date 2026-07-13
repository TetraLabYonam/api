package com.example.attempt.service;

import com.example.attempt.domain.Place;
import com.example.attempt.domain.UnitType;
import com.example.attempt.dto.place.PlaceSummaryDto;
import com.example.attempt.repository.JobKeywordSynonymRepository;
import com.example.attempt.repository.PlaceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 사업단 유형별 일자리(Place) 검색
 * 1단계: 이름/설명/동의어 LIKE 검색
 * 2단계: 1단계가 0건일 때만 LLM 폴백 (후보군이 작을 때만 유효)
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class PlaceSearchService {

    private final PlaceRepository placeRepository;
    private final JobKeywordSynonymRepository synonymRepository;
    private final Optional<LlmJobSearchClient> llmJobSearchClient;

    // 생성자가 2개라 Spring이 어떤 것으로 빈을 만들지 모호해지므로, 실제 빈 생성에 쓰일
    // 생성자를 명시적으로 @Autowired로 지정한다. 2-arg 생성자는 테스트에서 `new`로 직접
    // 호출할 때만 쓰인다 (Task 3의 PlaceSearchServiceTest).
    public PlaceSearchService(PlaceRepository placeRepository,
                               JobKeywordSynonymRepository synonymRepository) {
        this(placeRepository, synonymRepository, Optional.empty());
    }

    @Autowired
    public PlaceSearchService(PlaceRepository placeRepository,
                               JobKeywordSynonymRepository synonymRepository,
                               Optional<LlmJobSearchClient> llmJobSearchClient) {
        this.placeRepository = placeRepository;
        this.synonymRepository = synonymRepository;
        this.llmJobSearchClient = llmJobSearchClient;
    }

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

    public List<PlaceSummaryDto> searchWithFallback(UnitType unitType, String freeText) {
        List<PlaceSummaryDto> stage1 = search(unitType, freeText);
        if (!stage1.isEmpty()) {
            return stage1;
        }
        if (llmJobSearchClient.isEmpty()) {
            log.info("LLM 폴백 비활성화 상태 (llm.provider.api-key 미설정)");
            return List.of();
        }

        List<PlaceSummaryDto> candidates = listByUnitType(unitType);
        Set<Long> candidateIds = candidates.stream().map(PlaceSummaryDto::getId).collect(Collectors.toSet());

        return llmJobSearchClient.get().pickBestMatch(freeText, candidates)
                .filter(candidateIds::contains)
                .flatMap(placeRepository::findById)
                .map(this::toDto)
                .map(List::of)
                .orElse(List.of());
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
