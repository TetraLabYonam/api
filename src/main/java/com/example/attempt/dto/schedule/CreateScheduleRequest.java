package com.example.attempt.dto.schedule;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CreateScheduleRequest {

    private LocalDateTime attendDate;

}
