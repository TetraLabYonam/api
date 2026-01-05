package com.example.attempt.controller;

import com.example.attempt.repository.TicketIssuanceRepository;
import com.example.attempt.security.JwtTokenProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 디바이스 등록 API 컨트롤러
 * 클라이언트 userKey를 서버 발급 deviceToken으로 전환하는 마이그레이션 지원
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final JwtTokenProvider jwt;
    private final TicketIssuanceRepository tiRepo;

    public DeviceController(JwtTokenProvider jwt, TicketIssuanceRepository tiRepo) {
        this.jwt = jwt;
        this.tiRepo = tiRepo;
    }

    /**
     * 디바이스 등록
     * 기존 userKey를 제출하면 서버 발급 deviceToken으로 전환
     * userKey 보존 마이그레이션 지원
     *
     * @param body userKey (optional)
     * @return deviceToken, deviceId
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody(required = false) Map<String, String> body) {
        String clientUserKey = body != null ? body.get("userKey") : null;

        // 기존 userKey가 있으면 재사용, 없으면 새 deviceId 생성
        String deviceId = clientUserKey != null
                ? clientUserKey
                : "DEVICE-" + java.util.UUID.randomUUID().toString();

        // TODO: 기존 TicketIssuance.userKey와 deviceId 매핑 로직 추가
        // - 기존 티켓 레코드를 device record에 연결
        // - DB migration 필요 시 별도 테이블 추가

        // deviceToken 발급 (JWT with deviceId claim)
        String deviceToken = jwt.createAccessToken(deviceId, Map.of("deviceId", deviceId));

        return ResponseEntity.ok(Map.of(
                "deviceToken", deviceToken,
                "deviceId", deviceId
        ));
    }
}
