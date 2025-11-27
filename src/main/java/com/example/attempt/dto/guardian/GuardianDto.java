package com.example.attempt.dto.guardian;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GuardianDto {
    private Long id;
    private String name;
    private String phone;
    private boolean receiveNotification;
}
