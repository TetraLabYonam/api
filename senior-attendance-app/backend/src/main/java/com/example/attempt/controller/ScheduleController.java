package com.example.attempt.controller;

import com.example.attempt.domain.Admin;
import com.example.attempt.domain.Attend;
import com.example.attempt.domain.Schedule;
import com.example.attempt.dto.admin.AdminScheduleAttendanceResponse;
import com.example.attempt.dto.admin.AttendeeItem;
import com.example.attempt.dto.schedule.CreateScheduleRequest;
import com.example.attempt.dto.schedule.CreateScheduleResponse;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.ScheduleRepository;
import com.example.attempt.service.CurrentAdminService;
import com.example.attempt.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final CurrentAdminService currentAdminService;
    private final ScheduleRepository scheduleRepository;

    @PostMapping
    public CreateScheduleResponse create(@Valid @RequestBody CreateScheduleRequest request) {
        Admin admin = currentAdminService.getCurrentAdmin();
        return scheduleService.createSchedules(request, admin);
    }

    @GetMapping
    public AdminScheduleAttendanceResponse get(@RequestParam Long placeId, @RequestParam LocalDate date) {
        Schedule schedule = scheduleRepository.findByPlaceIdAndScheduleDate(placeId, date)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "장소·날짜에 해당하는 일정을 찾을 수 없습니다. placeId=" + placeId + ", date=" + date));

        List<AttendeeItem> attendees = schedule.getAttends().stream()
                .map(this::toAttendeeItem)
                .toList();

        return new AdminScheduleAttendanceResponse(
                schedule.getId(),
                schedule.getTitle(),
                schedule.getScheduleDate(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getPlace().getName(),
                attendees);
    }

    private AttendeeItem toAttendeeItem(Attend attend) {
        return new AttendeeItem(
                attend.getId(),
                attend.getMember().getId(),
                attend.getMember().getUsername(),
                attend.getStatus(),
                attend.getNote(),
                attend.getAttendedAt());
    }
}
