package com.example.attempt.controller;

import com.example.attempt.domain.Place;
import com.example.attempt.domain.UnitType;
import com.example.attempt.repository.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlaceControllerIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    PlaceRepository placeRepository;

    // 이 테스트는 인증 없이 401이 반환되는 것만 검증한다. OTP 코드는 SMS로만 발송되고
    // 해시로 저장되므로 통합 테스트에서 원본 코드를 알아낼 방법이 없다 — 로그인 성공
    // 이후의 인가 동작(200 응답)은 Task 6의 MemberOtpServiceTest가 해시/검증 로직을
    // 단위 테스트로 이미 커버한다.
    @Test
    void listByUnitType_withoutAuth_returns401() {
        Place park = new Place("공원안전지킴이", "주소1", 35.3, 129.0);
        park.setUnitType(UnitType.PUBLIC_INTEREST);
        placeRepository.save(park);

        String url = "http://localhost:" + port + "/api/v1/places?unitType=PUBLIC_INTEREST";
        ResponseEntity<Object> resp = restTemplate.getForEntity(url, Object.class);

        assertEquals(401, resp.getStatusCodeValue());
    }
}
