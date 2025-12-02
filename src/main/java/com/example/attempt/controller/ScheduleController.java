package com.example.attempt.controller;

import com.example.attempt.domain.Place;
import com.example.attempt.domain.Schedule;
import com.example.attempt.repository.PlaceRepository;
import com.example.attempt.repository.ScheduleRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/schedules")
public class ScheduleController {

    private final ScheduleRepository scheduleRepository;
    private final PlaceRepository placeRepository;

    public ScheduleController(ScheduleRepository scheduleRepository, PlaceRepository placeRepository) {
        this.scheduleRepository = scheduleRepository;
        this.placeRepository = placeRepository;
    }

    /**
     * 테스트용 스케줄 생성: 필요한 최소 필드만 사용.
     * attendDateISO: ISO-8601 문자열(예: 2025-01-01T09:00:00Z), 미입력 시 현재 시각.
     * placeId: 선택값. 제공 시 존재 여부 검증 후 매핑.
     */
    @PostMapping
    public ScheduleResponse createSchedule(@RequestParam(required = false) Long placeId,
                                           @RequestParam(required = false, name = "attendDateISO") String attendDateISO) {
        Schedule schedule = new Schedule();

        Date attendDate = attendDateISO != null
                ? Date.from(Instant.parse(attendDateISO))
                : new Date();
        schedule.setAttendDate(attendDate);

        if (placeId != null) {
            Place place = placeRepository.findById(placeId)
                    .orElseThrow(() -> new NoSuchElementException("place not found: " + placeId));
            schedule.setPlace(place);
        }

        Schedule saved = scheduleRepository.save(schedule);
        return toResponse(saved);
    }

    @GetMapping
    public List<ScheduleResponse> listSchedules() {
        Iterable<Schedule> all = scheduleRepository.findAll();
        List<ScheduleResponse> responses = new ArrayList<>();
        for (Schedule schedule : all) {
            responses.add(toResponse(schedule));
        }
        return responses;
    }

    private ScheduleResponse toResponse(Schedule schedule) {
        Long placeId = schedule.getPlace() != null ? schedule.getPlace().getId() : null;
        return new ScheduleResponse(schedule.getId(), placeId, schedule.getAttendDate());
    }

    public record ScheduleResponse(Long id, Long placeId, Date attendDate) {}
}
