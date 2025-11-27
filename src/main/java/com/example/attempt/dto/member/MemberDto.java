package com.example.attempt.dto.member;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MemberDto {
    private Long id;
    private String username;
    private String phoneNumber;
    private String unitName;
}

