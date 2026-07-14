package com.example.attempt.service;

import com.example.attempt.dto.place.PlaceSummaryDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 후보군이 작을 때(사업단당 수십 개 이내) LLM에게 자유서술 입력과 가장 가까운 Place를 고르게 한다.
 * 벡터DB/임베딩 인프라 없이 프롬프트 하나로 처리한다.
 *
 * llm.provider.api-key가 비어있으면(기본값) 이 빈은 생성되지 않는다.
 * application.yml은 항상 llm.provider.api-key 프로퍼티 키를 정의하므로(빈 문자열 기본값 포함),
 * @ConditionalOnProperty(name = "api-key")처럼 값이 있는지만 보는 조건은 항상 참이 되어버린다
 * (Spring은 "false" 리터럴이 아니면 프로퍼티가 "존재"한다고 판단하기 때문).
 * 따라서 값이 실제로 비어있지 않은지 표현식으로 직접 검사한다.
 */
@Service
@ConditionalOnExpression("'${llm.provider.api-key:}' != ''")
@Slf4j
public class LlmJobSearchClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String model;

    @Autowired
    public LlmJobSearchClient(@Value("${llm.provider.api-key}") String apiKey,
                               @Value("${llm.provider.model:claude-haiku-4-5-20251001}") String model) {
        this(apiKey, model, new RestTemplate());
    }

    // 테스트에서 RestTemplate을 mock으로 교체하기 위한 생성자.
    // PlaceSearchService와 동일한 이유로 생성자가 2개라, Spring이 실제로 쓸 생성자를
    // @Autowired로 명시한다.
    LlmJobSearchClient(String apiKey, String model, RestTemplate restTemplate) {
        this.apiKey = apiKey;
        this.model = model;
        this.restTemplate = restTemplate;
    }

    public Optional<Long> pickBestMatch(String freeText, List<PlaceSummaryDto> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        StringBuilder candidateList = new StringBuilder();
        for (PlaceSummaryDto c : candidates) {
            candidateList.append(c.getId()).append(": ").append(c.getName());
            if (c.getDescription() != null && !c.getDescription().isBlank()) {
                candidateList.append(" (").append(c.getDescription()).append(")");
            }
            candidateList.append("\n");
        }

        String prompt = "참여자가 본인이 일하는 일자리를 이렇게 설명했습니다: \"" + freeText + "\"\n\n" +
                "아래 후보 목록 중 가장 가까운 것 하나의 번호만 답하세요. 확신이 없으면 0을 답하세요.\n\n" +
                candidateList;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");

            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", 16,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            var response = restTemplate.postForEntity(
                    "https://api.anthropic.com/v1/messages",
                    new HttpEntity<>(body, headers),
                    String.class);

            JsonNode json = objectMapper.readTree(response.getBody());
            String text = json.at("/content/0/text").asText("");
            long placeId = Long.parseLong(text.replaceAll("[^0-9]", ""));
            return placeId == 0 ? Optional.empty() : Optional.of(placeId);
        } catch (Exception e) {
            log.error("LLM 검색 폴백 호출 실패: freeText={}", freeText, e);
            return Optional.empty();
        }
    }
}
