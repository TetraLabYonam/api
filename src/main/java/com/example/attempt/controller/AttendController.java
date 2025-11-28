package com.example.attempt.controller;

import com.example.attempt.domain.AttendStatus;
import com.example.attempt.dto.attend.AttendCheckInRequest;
import com.example.attempt.dto.attend.AttendCheckInResponse;
import com.example.attempt.service.AttendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 출석 관리 REST Controller
 */
@RestController
@RequestMapping("/api/v1/attend")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Attend", description = "출석 관리 API")
public class AttendController {

    private final AttendService attendService;

    /**
     * 출석 체크인
     */
    @PostMapping("/check-in")
    @Operation(summary = "출석 체크인", description = "회원의 위치 정보를 기반으로 출석 처리합니다.")
    public ResponseEntity<AttendCheckInResponse> checkIn(
            @Valid @RequestBody AttendCheckInRequest request) {
        log.info("출석 체크인 API 호출: scheduleId={}, memberId={}",
                request.getScheduleId(), request.getMemberId());

        AttendCheckInResponse response = attendService.checkIn(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 결석 처리
     */
    @PutMapping("/{attendId}/absent")
    @Operation(summary = "결석 처리", description = "특정 출석 정보를 결석으로 처리합니다.")
    public ResponseEntity<Void> markAbsent(
            @Parameter(description = "출석 ID") @PathVariable Long attendId,
            @Parameter(description = "결석 사유") @RequestParam(required = false) String reason) {
        log.info("결석 처리 API 호출: attendId={}, reason={}", attendId, reason);

        attendService.markAbsent(attendId, reason);
        return ResponseEntity.ok().build();
    }

    /**
     * 사유 인정 결석 처리
     */
    @PutMapping("/{attendId}/excused")
    @Operation(summary = "사유 인정 결석 처리", description = "특정 출석 정보를 사유 인정 결석으로 처리합니다.")
    public ResponseEntity<Void> markExcused(
            @Parameter(description = "출석 ID") @PathVariable Long attendId,
            @Parameter(description = "사유") @RequestParam String reason) {
        log.info("사유 인정 결석 처리 API 호출: attendId={}, reason={}", attendId, reason);

        attendService.markExcused(attendId, reason);
        return ResponseEntity.ok().build();
    }

    /**
     * 출석 상태 변경
     */
    @PutMapping("/{attendId}/status")
    @Operation(summary = "출석 상태 변경", description = "출석 정보의 상태를 직접 변경합니다. (관리자용)")
    public ResponseEntity<Void> updateAttendStatus(
            @Parameter(description = "출석 ID") @PathVariable Long attendId,
            @Parameter(description = "변경할 상태") @RequestParam AttendStatus status,
            @Parameter(description = "비고") @RequestParam(required = false) String note) {
        log.info("출석 상태 변경 API 호출: attendId={}, status={}, note={}",
                attendId, status, note);

        attendService.updateAttendStatus(attendId, status, note);
        return ResponseEntity.ok().build();
    }
}
