package com.example.attempt.dto.admin;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 관리자가 회원의 활성/비활성 상태를 변경할 때 사용하는 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMemberActiveRequest {
    @NotNull(message = "active 값은 필수입니다.")
    private Boolean active;
}
