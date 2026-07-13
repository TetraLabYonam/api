package com.example.attempt.dto.memberauth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OtpRequestRequest {
    @NotBlank(message = "휴대폰번호는 필수입니다.")
    private String phoneNumber;
}
