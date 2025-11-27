package com.example.attempt.dto.guardian;

import lombok.Data;

@Data
public class CreateGuardianRequest {
    private Long memberId;
    private String name;
    private String phone;
    private Boolean receiveNotification;
}
