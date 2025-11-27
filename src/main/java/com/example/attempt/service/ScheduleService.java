package com.example.attempt.service;

import com.example.attempt.domain.Schedule;
import com.example.attempt.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;

    public Long create(LocalDateTime attendDate) {
        Schedule schedule = new Schedule();
        schedule.setAttendDate(attendDate);   // 엔티티에 setAttendDate 있는 걸 가정

        scheduleRepository.save(schedule);
        return schedule.getId();
    }

    public Schedule findOne(Long id) {
        return scheduleRepository.find(id);
    }
}

