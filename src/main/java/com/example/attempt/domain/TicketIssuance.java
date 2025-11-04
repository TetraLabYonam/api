package com.example.attempt.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name="ticket_issuances",
        uniqueConstraints = {
                @UniqueConstraint(name="ux_room_user", columnNames={"room_id","user_key"}),
                @UniqueConstraint(name="ux_room_number", columnNames={"room_id","number"})
        })
@Getter
@Setter
@NoArgsConstructor
public class TicketIssuance {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional=false)
    @JoinColumn(name="room_id", foreignKey = @ForeignKey(name="fk_ti_room"))
    private Room room;

    @Column(name="user_key", length=128, nullable=false)
    private String userKey;

    @Column(nullable=false) private Integer number;

    @Column(name="issued_at", nullable=false, updatable=false)
    private LocalDateTime issuedAt = LocalDateTime.now();

    public TicketIssuance(Room room, String userKey, Integer number){
        this.room = room; this.userKey = userKey; this.number = number;
    }

}
