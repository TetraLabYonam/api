package com.example.attempt.dto.memberauth;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MemberLoginRequest {
    @NotNull(message = "직번은 필수입니다.")
    private Long employeeId;

    @NotNull(message = "전화번호는 필수입니다.")
    private String phoneNumber;
}
