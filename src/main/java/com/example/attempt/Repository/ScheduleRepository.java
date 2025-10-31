package com.example.attempt.Repository;

import com.example.attempt.domain.Schedule;
import org.springframework.data.repository.CrudRepository;

public interface ScheduleRepository extends CrudRepository<Schedule, Long> {
}
