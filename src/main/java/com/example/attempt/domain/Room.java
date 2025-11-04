package com.example.attempt.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity @Table(name = "rooms")
@Getter @Setter @NoArgsConstructor
public class Room {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="room_uid", length=16, nullable=false, unique=true)
    private String roomUid;

    @Column(length=100) private String title;

    @Column(name="current_number", nullable=false)
    private Integer currentNumber = 0;

    @Column(name="created_at", nullable=false, updatable=false)
    private LocalDateTime createdAt = LocalDateTime.now();

}
