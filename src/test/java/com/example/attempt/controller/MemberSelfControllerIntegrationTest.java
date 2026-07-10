package com.example.attempt.controller;

import com.example.attempt.domain.Place;
import com.example.attempt.repository.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberSelfControllerIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void assignPlace_withoutAuth_returns401() {
        String url = "http://localhost:" + port + "/api/v1/members/me/assign-place";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(Map.of("placeId", 1), headers);

        ResponseEntity<Object> resp = restTemplate.postForEntity(url, req, Object.class);

        assertEquals(401, resp.getStatusCodeValue());
    }
}
