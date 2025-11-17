package com.example.attempt.dto;

import com.example.attempt.domain.Room;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Queue Room 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueRoomDto {

    private Long id;
    private String roomUid;
    private String roomName;
    private Boolean isActive;
    private Integer currentNumber;
    private Integer lastIssuedNumber;
    private Integer waitingCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Entity to DTO 변환
     */
    public static QueueRoomDto fromEntity(Room room) {
        return QueueRoomDto.builder()
                .id(room.getId())
                .roomUid(room.getRoomUid())
                .roomName(room.getTitle())
                .isActive(room.getIsActive())
                .currentNumber(room.getCurrentNumber())
                .lastIssuedNumber(room.getLastIssuedNumber())
                .waitingCount(Math.max(0, room.getLastIssuedNumber() - room.getCurrentNumber()))
                .createdAt(room.getCreatedAt())
                .updatedAt(room.getUpdatedAt())
                .build();
    }
}
