package com.example.attempt.domain;

import jakarta.persistence.*;

@Entity
public class Attend {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MEMBER_ID")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SCHEDULE_ID")
    private Schedule schedule;

    // 회원의 현재 위도
    @Column(name = "PLACE_LATITUDE")
    private Double latitude;

    // 회원의 현재 경도
    @Column(name = "PLACE_LONGITUDE")
    private Double longitude;

    public Attend(Member member, Schedule schedule, Double latitude, Double longitude) {
        this.member = member;
        this.schedule = schedule;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Attend() {

    }
}
