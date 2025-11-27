package com.example.attempt.dto.member;

import lombok.Data;

@Data
public class UpdateMemberRequest {
    private String username;
    private String phoneNumber;
}
