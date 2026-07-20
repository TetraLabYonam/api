package com.example.attempt.controller;

import com.example.attempt.domain.Admin;
import com.example.attempt.dto.schedule.CreateScheduleRequest;
import com.example.attempt.dto.schedule.CreateScheduleResponse;
import com.example.attempt.service.CurrentAdminService;
import com.example.attempt.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final CurrentAdminService currentAdminService;

    @PostMapping
    public CreateScheduleResponse create(@Valid @RequestBody CreateScheduleRequest request) {
        Admin admin = currentAdminService.getCurrentAdmin();
        return scheduleService.createSchedules(request, admin);
    }
}
