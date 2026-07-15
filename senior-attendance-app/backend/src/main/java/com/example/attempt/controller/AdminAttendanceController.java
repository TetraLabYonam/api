package com.example.attempt.controller;

import com.example.attempt.dto.admin.AttendanceSummaryResponse;
import com.example.attempt.service.AdminAttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAttendanceController {

    private final AdminAttendanceService adminAttendanceService;

    @GetMapping("/attendance/summary")
    public List<AttendanceSummaryResponse> getSummary(@RequestParam String period) {
        return adminAttendanceService.getSummary(period);
    }
}
