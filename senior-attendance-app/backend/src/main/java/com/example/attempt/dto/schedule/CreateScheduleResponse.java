package com.example.attempt.dto.schedule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateScheduleResponse {
    private List<LocalDate> createdDates;
    private List<LocalDate> skippedDates;
    private int attendCreatedCount;
}
