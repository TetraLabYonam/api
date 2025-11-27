package com.example.attempt.controller;

import com.example.attempt.dto.schedule.CreateScheduleRequest;
import com.example.attempt.dto.schedule.CreateScheduleResponse;
import com.example.attempt.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @PostMapping
    public ResponseEntity<CreateScheduleResponse> create(@RequestBody CreateScheduleRequest request) {

        Long id = scheduleService.create(request.getAttendDate());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateScheduleResponse(id));
    }
}


