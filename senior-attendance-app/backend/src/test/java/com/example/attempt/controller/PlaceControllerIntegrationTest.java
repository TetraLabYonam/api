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
class PlaceControllerIntegrationTest {

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

    // 이 테스트는 인증 없이 401이 반환되는 것만 검증한다. OTP 코드는 SMS로만 발송되고
    // 해시로 저장되므로 통합 테스트에서 원본 코드를 알아낼 방법이 없다 — 로그인 성공
    // 이후의 인가 동작(200 응답)은 아래 인증된 테스트들이 커버한다.
    @Test
    void listByUnitType_withoutAuth_returns401() {
        Place park = new Place("공원안전지킴이", "주소1", 35.3, 129.0);
        park.setUnitType(UnitType.PUBLIC_INTEREST);
        placeRepository.save(park);

        String url = "http://localhost:" + port + "/api/v1/places?unitType=PUBLIC_INTEREST";
        ResponseEntity<Object> resp = restTemplate.getForEntity(url, Object.class);

        assertEquals(401, resp.getStatusCodeValue());
    }

    @Test
    void listByUnitType_withAuth_returnsOnlyMatchingUnitType() {
        Place park = new Place("공원안전지킴이", "주소1", 35.3, 129.0);
        park.setUnitType(UnitType.PUBLIC_INTEREST);
        placeRepository.save(park);

        Place market = new Place("동네마당재활용", "주소2", 35.4, 129.1);
        market.setUnitType(UnitType.MARKET);
        placeRepository.save(market);

        String accessToken = MemberAuthTestSupport.loginAsMember(
                restTemplate, port, memberRepository, passwordEncoder, "김할매", "01012340001");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        String url = "http://localhost:" + port + "/api/v1/places?unitType=PUBLIC_INTEREST";
        ResponseEntity<Object[]> resp = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Object[].class);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(1, resp.getBody().length);
    }

    @Test
    void searchByKeyword_withAuth_returnsOnlyMatchingResults() {
        Place park = new Place("공원안전지킴이", "주소1", 35.3, 129.0);
        park.setUnitType(UnitType.PUBLIC_INTEREST);
        park.setDescription("청소 및 화단 관리");
        placeRepository.save(park);

        Place other = new Place("스쿨존실버봉사단", "주소2", 35.5, 129.2);
        other.setUnitType(UnitType.PUBLIC_INTEREST);
        other.setDescription("등하교 안전 지도");
        placeRepository.save(other);

        String accessToken = MemberAuthTestSupport.loginAsMember(
                restTemplate, port, memberRepository, passwordEncoder, "김할매", "01012340002");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        String url = "http://localhost:" + port + "/api/v1/places?unitType=PUBLIC_INTEREST&q=청소";
        ResponseEntity<Object[]> resp = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Object[].class);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(1, resp.getBody().length);
    }
}
