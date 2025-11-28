package com.example.attempt.controller;

import com.example.attempt.dto.schedule.ScheduleCreateRequest;
import com.example.attempt.dto.schedule.ScheduleCreateResponse;
import com.example.attempt.dto.schedule.ScheduleDetailResponse;
import com.example.attempt.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 일정 관리 REST Controller
 */
@RestController
@RequestMapping("/api/v1/schedule")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Schedule", description = "일정 관리 API")
public class ScheduleController {

    private final ScheduleService scheduleService;

    /**
     * 일정 생성
     */
    @PostMapping("/create")
    @Operation(summary = "일정 생성", description = "여러 날짜에 대한 일정을 생성하고 참석자를 지정합니다.")
    public ResponseEntity<ScheduleCreateResponse> createSchedules(
            @Valid @RequestBody ScheduleCreateRequest request) {
        log.info("일정 생성 API 호출: {}", request.getTitle());

        ScheduleCreateResponse response = scheduleService.createSchedules(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 일정 상세 조회
     */
    @GetMapping("/{scheduleId}")
    @Operation(summary = "일정 상세 조회", description = "특정 일정의 상세 정보와 참석자 목록을 조회합니다.")
    public ResponseEntity<ScheduleDetailResponse> getScheduleDetail(
            @Parameter(description = "일정 ID") @PathVariable Long scheduleId) {
        log.info("일정 상세 조회 API 호출: scheduleId={}", scheduleId);

        ScheduleDetailResponse response = scheduleService.getScheduleDetail(scheduleId);
        return ResponseEntity.ok(response);
    }

    /**
     * 회원별 일정 조회
     */
    @GetMapping("/member/{memberId}")
    @Operation(summary = "회원별 일정 조회", description = "특정 회원의 일정을 날짜 범위로 조회합니다.")
    public ResponseEntity<List<ScheduleDetailResponse>> getMemberSchedules(
            @Parameter(description = "회원 ID") @PathVariable Long memberId,
            @Parameter(description = "시작 날짜") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "종료 날짜") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("회원별 일정 조회 API 호출: memberId={}, startDate={}, endDate={}", memberId, startDate, endDate);

        List<ScheduleDetailResponse> schedules = scheduleService.getMemberSchedules(memberId, startDate, endDate);
        return ResponseEntity.ok(schedules);
    }

    /**
     * 날짜별 일정 조회
     */
    @GetMapping("/date")
    @Operation(summary = "날짜별 일정 조회", description = "특정 날짜의 모든 일정을 조회합니다.")
    public ResponseEntity<List<ScheduleDetailResponse>> getSchedulesByDate(
            @Parameter(description = "조회할 날짜") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("날짜별 일정 조회 API 호출: date={}", date);

        List<ScheduleDetailResponse> schedules = scheduleService.getSchedulesByDate(date);
        return ResponseEntity.ok(schedules);
    }

    /**
     * 날짜 범위로 일정 조회
     */
    @GetMapping("/range")
    @Operation(summary = "날짜 범위 일정 조회", description = "날짜 범위 내의 모든 활성 일정을 조회합니다.")
    public ResponseEntity<List<ScheduleDetailResponse>> getSchedulesByDateRange(
            @Parameter(description = "시작 날짜") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "종료 날짜") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("날짜 범위 일정 조회 API 호출: startDate={}, endDate={}", startDate, endDate);

        List<ScheduleDetailResponse> schedules = scheduleService.getSchedulesByDateRange(startDate, endDate);
        return ResponseEntity.ok(schedules);
    }

    /**
     * 일정 비활성화
     */
    @PutMapping("/{scheduleId}/deactivate")
    @Operation(summary = "일정 비활성화", description = "일정을 비활성화합니다.")
    public ResponseEntity<Void> deactivateSchedule(
            @Parameter(description = "일정 ID") @PathVariable Long scheduleId) {
        log.info("일정 비활성화 API 호출: scheduleId={}", scheduleId);

        scheduleService.deactivateSchedule(scheduleId);
        return ResponseEntity.ok().build();
    }

    /**
     * 일정 활성화
     */
    @PutMapping("/{scheduleId}/activate")
    @Operation(summary = "일정 활성화", description = "일정을 활성화합니다.")
    public ResponseEntity<Void> activateSchedule(
            @Parameter(description = "일정 ID") @PathVariable Long scheduleId) {
        log.info("일정 활성화 API 호출: scheduleId={}", scheduleId);

        scheduleService.activateSchedule(scheduleId);
        return ResponseEntity.ok().build();
    }
}
