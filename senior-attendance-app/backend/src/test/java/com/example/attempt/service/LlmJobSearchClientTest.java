package com.example.attempt.service;

import com.example.attempt.domain.UnitType;
import com.example.attempt.dto.place.PlaceSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LlmJobSearchClientTest {

    private RestTemplate restTemplate;
    private LlmJobSearchClient client;

    @BeforeEach
    void setup() {
        restTemplate = mock(RestTemplate.class);
        client = new LlmJobSearchClient("test-api-key", "claude-haiku-4-5-20251001", restTemplate);
    }

    private PlaceSummaryDto candidate(long id, String name) {
        return PlaceSummaryDto.builder().id(id).name(name).unitType(UnitType.PUBLIC_INTEREST).build();
    }

    private void mockRestTemplateResponse(String responseBody) {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
    }

    @Test
    void pickBestMatch_withEmptyCandidates_returnsEmptyWithoutCallingRestTemplate() {
        Optional<Long> result = client.pickBestMatch("아무 말이나", List.of());

        assertTrue(result.isEmpty());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void pickBestMatch_parsesPlaceIdFromValidResponse() {
        mockRestTemplateResponse("{\"content\":[{\"text\":\"7\"}]}");

        Optional<Long> result = client.pickBestMatch("학교 앞에서 깃발", List.of(candidate(7L, "스쿨존실버봉사단1")));

        assertEquals(Optional.of(7L), result);
    }

    @Test
    void pickBestMatch_whenLlmRespondsZero_returnsEmpty() {
        mockRestTemplateResponse("{\"content\":[{\"text\":\"0\"}]}");

        Optional<Long> result = client.pickBestMatch("알 수 없는 일", List.of(candidate(7L, "스쿨존실버봉사단1")));

        assertTrue(result.isEmpty());
    }

    @Test
    void pickBestMatch_whenHttpCallThrows_returnsEmpty() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("연결 실패"));

        Optional<Long> result = client.pickBestMatch("학교 앞에서 깃발", List.of(candidate(7L, "스쿨존실버봉사단1")));

        assertTrue(result.isEmpty());
    }

    @Test
    void pickBestMatch_whenResponseIsMalformed_returnsEmpty() {
        mockRestTemplateResponse("이건 JSON이 아닙니다");

        Optional<Long> result = client.pickBestMatch("학교 앞에서 깃발", List.of(candidate(7L, "스쿨존실버봉사단1")));

        assertTrue(result.isEmpty());
    }
}
