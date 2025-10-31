package com.example.attempt.domain;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;

import java.util.Date;

@Entity
@NoArgsConstructor
public class Schedule {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PLACE_ID")
    private Place place;

    private Date AttendDate;


}
