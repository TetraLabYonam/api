package com.example.attempt.controller;

import com.example.attempt.domain.Place;
import com.example.attempt.domain.UnitType;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.repository.PlaceRepository;
import com.example.attempt.service.SmsService;
import com.example.attempt.support.MemberAuthTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminPlaceControllerIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockBean
    SmsService smsService;

    @BeforeEach
    void setUp() {
        placeRepository.deleteAll();
    }

    private String obtainAdminAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> req = new HttpEntity<>(
                Map.of("username", "admin@example.com", "password", "1234"), headers);
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/auth/login", req, Map.class);
        return (String) resp.getBody().get("accessToken");
    }

    @Test
    void listPlaces_withoutAuth_returns401() {
        String url = "http://localhost:" + port + "/api/admin/places";
        ResponseEntity<Object> resp = restTemplate.getForEntity(url, Object.class);
        assertEquals(401, resp.getStatusCodeValue());
    }

    @Test
    void listPlaces_withMemberToken_returns403() {
        String accessToken = MemberAuthTestSupport.loginAsMember(
                restTemplate, port, memberRepository, passwordEncoder, "김할매", "01099997777");
        assertNotNull(accessToken, "Member accessToken should not be null");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        String url = "http://localhost:" + port + "/api/admin/places";
        ResponseEntity<Object> resp = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Object.class);

        assertEquals(403, resp.getStatusCodeValue());
    }

    @Test
    void listPlaces_withAdminToken_returnsAllPlacesRegardlessOfUnitType() {
        Place park = new Place("공원안전지킴이", "주소1", 35.3, 129.0);
        park.setUnitType(UnitType.PUBLIC_INTEREST);
        placeRepository.save(park);

        Place market = new Place("동네마당재활용", "주소2", 35.4, 129.1);
        market.setUnitType(UnitType.MARKET);
        placeRepository.save(market);

        String accessToken = obtainAdminAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        String url = "http://localhost:" + port + "/api/admin/places";
        ResponseEntity<Map[]> resp = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Map[].class);

        assertEquals(200, resp.getStatusCodeValue());
        Map[] body = resp.getBody();
        assertEquals(2, body.length);

        Map<String, Object> parkDto = findByName(body, "공원안전지킴이");
        Map<String, Object> marketDto = findByName(body, "동네마당재활용");

        assertNotNull(parkDto, "Response should contain the seeded PUBLIC_INTEREST place by name");
        assertNotNull(marketDto, "Response should contain the seeded MARKET place by name");

        assertEquals(UnitType.PUBLIC_INTEREST.name(), parkDto.get("unitType"));
        assertEquals(UnitType.MARKET.name(), marketDto.get("unitType"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findByName(Map[] body, String name) {
        for (Map<?, ?> entry : body) {
            if (name.equals(entry.get("name"))) {
                return (Map<String, Object>) entry;
            }
        }
        return null;
    }

    @Test
    void create_withValidRequest_persistsPlaceAsActive() {
        String accessToken = obtainAdminAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "name", "행복경로당",
                "address", "서울시 종로구",
                "unitType", "PUBLIC_INTEREST",
                "description", "청소 봉사",
                "latitude", 37.57,
                "longitude", 126.97);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/admin/places", req, Map.class);

        assertEquals(200, resp.getStatusCodeValue());
        Map<String, Object> respBody = resp.getBody();
        assertEquals("행복경로당", respBody.get("name"));
        assertEquals(true, respBody.get("active"));

        Number idNum = (Number) respBody.get("id");
        Place saved = placeRepository.findById(idNum.longValue()).orElse(null);
        assertNotNull(saved);
        assertTrue(saved.isActive());
        assertEquals(37.57, saved.getLatitude());
    }

    @Test
    void create_withMissingLatitude_returns400() {
        String accessToken = obtainAdminAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", "행복경로당");
        body.put("address", "서울시 종로구");
        body.put("unitType", "PUBLIC_INTEREST");
        body.put("longitude", 126.97);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/admin/places", req, Map.class);

        assertEquals(400, resp.getStatusCodeValue());
    }

    @Test
    void update_changesFieldsAndCanDeactivate() {
        Place place = new Place("수정전이름", "수정전주소", 35.0, 129.0);
        place.setUnitType(UnitType.PUBLIC_INTEREST);
        place = placeRepository.save(place);

        String accessToken = obtainAdminAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "name", "수정후이름",
                "address", "수정후주소",
                "unitType", "MARKET",
                "description", "수정된 설명",
                "latitude", 36.0,
                "longitude", 128.0,
                "active", false);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/admin/places/" + place.getId(),
                HttpMethod.PATCH, req, Map.class);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals("수정후이름", resp.getBody().get("name"));
        assertEquals(false, resp.getBody().get("active"));

        Place updated = placeRepository.findById(place.getId()).orElseThrow();
        assertEquals("수정후이름", updated.getName());
        assertEquals(UnitType.MARKET, updated.getUnitType());
        assertFalse(updated.isActive());
    }

    @Test
    void update_nonExistentId_returns404() {
        String accessToken = obtainAdminAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "name", "이름", "address", "주소", "unitType", "MARKET",
                "latitude", 36.0, "longitude", 128.0, "active", true);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/admin/places/999999",
                HttpMethod.PATCH, req, Map.class);

        assertEquals(404, resp.getStatusCodeValue());
    }
}
