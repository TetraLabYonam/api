package com.example.attempt.dto;

import com.example.attempt.domain.TicketIssuance;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Queue Ticket 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueTicketDto {

    private Long id;
    private Long roomId;
    private String roomUid;
    private Integer ticketNumber;
    private String userDeviceId;
    private String status;
    private LocalDateTime issuedAt;
    private LocalDateTime calledAt;

    /**
     * Entity to DTO 변환
     */
    public static QueueTicketDto fromEntity(TicketIssuance ticket) {
        return QueueTicketDto.builder()
                .id(ticket.getId())
                .roomId(ticket.getRoom().getId())
                .roomUid(ticket.getRoom().getRoomUid())
                .ticketNumber(ticket.getNumber())
                .userDeviceId(ticket.getUserKey())
                .status(ticket.getStatus().name())
                .issuedAt(ticket.getIssuedAt())
                .calledAt(ticket.getCalledAt())
                .build();
    }
}
