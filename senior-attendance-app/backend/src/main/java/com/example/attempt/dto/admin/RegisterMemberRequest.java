package com.example.attempt.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 관리자가 신규 회원을 등록할 때 사용하는 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterMemberRequest {
    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    @NotBlank(message = "전화번호는 필수입니다.")
    private String phoneNumber;

    @NotNull(message = "일자리(placeId)는 필수입니다.")
    private Long placeId;
}
