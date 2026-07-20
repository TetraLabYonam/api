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
}
