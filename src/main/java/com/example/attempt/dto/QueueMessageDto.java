package com.example.attempt.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * WebSocket 메시지 전송용 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueMessageDto {

    private MessageType type;
    private Long roomId;
    private Integer currentNumber;
    private Integer calledNumber;
    private Integer waitingCount;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    public enum MessageType {
        CALL,    // 번호 호출
        UPDATE,  // 상태 업데이트
        CANCEL   // 취소
    }
}