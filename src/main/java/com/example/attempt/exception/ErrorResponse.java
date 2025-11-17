package com.example.attempt.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 에러 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private String message;          // 에러 메시지
    private LocalDateTime timestamp; // 에러 발생 시간
    private String path;             // 요청 경로

    public ErrorResponse(String message, LocalDateTime timestamp) {
        this.message = message;
        this.timestamp = timestamp;
    }
}