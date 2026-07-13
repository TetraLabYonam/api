package com.example.attempt.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name="queue_ticket",
        uniqueConstraints = {
                @UniqueConstraint(name="ux_room_device", columnNames={"room_id","user_device_id"}),
                @UniqueConstraint(name="ux_room_number", columnNames={"room_id","ticket_number"})
        })
@Getter
@Setter
@NoArgsConstructor
public class TicketIssuance {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional=false)
    @JoinColumn(name="room_id", foreignKey = @ForeignKey(name="fk_ticket_room"))
    private Room room;

    @Column(name="ticket_number", nullable=false)
    private Integer number;

    @Column(name="user_device_id", length=255)
    private String userKey;

    @Enumerated(EnumType.STRING)
    @Column(length=20, nullable=false)
    private TicketStatus status = TicketStatus.WAITING;

    @Column(name="issued_at", nullable=false, updatable=false)
    private LocalDateTime issuedAt = LocalDateTime.now();

    @Column(name="called_at")
    private LocalDateTime calledAt;

    public TicketIssuance(Room room, String userKey, Integer number){
        this.room = room;
        this.userKey = userKey;
        this.number = number;
        this.status = TicketStatus.WAITING;
    }

    public enum TicketStatus {
        WAITING,    // 대기 중
        CALLED,     // 호출됨
        COMPLETED,  // 완료
        CANCELLED   // 취소됨
    }
}
