package com.example.attempt.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity @Table(name = "queue_room")
@Getter @Setter @NoArgsConstructor
public class Room {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="room_uid", length=16, nullable=false, unique=true)
    private String roomUid;

    @Column(name="room_name", length=100)
    private String title;

    @Column(name="is_active", nullable=false)
    private Boolean isActive = true;

    @Column(name="current_number", nullable=false)
    private Integer currentNumber = 0;

    @Column(name="last_issued_number", nullable=false)
    private Integer lastIssuedNumber = 0;

    @Column(name="created_at", nullable=false, updatable=false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name="updated_at", nullable=false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
