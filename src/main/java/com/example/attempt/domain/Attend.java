package com.example.attempt.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
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

    // 출근(true) / 퇴근(false)
    @Column(name = "IS_CHECK_IN")
    private Boolean checkIn;

    // 출퇴근 시간
    @Column(name = "ATTEND_TIME")
    private LocalDateTime attendTime;

    // 위도
    @Column(name = "PLACE_LATITUDE")
    private Double latitude;

    // 경도
    @Column(name = "PLACE_LONGITUDE")
    private Double longitude;

    public Attend(Member member, Schedule schedule,
                  Boolean checkIn, LocalDateTime attendTime,
                  Double latitude, Double longitude) {

        this.member = member;
        this.schedule = schedule;
        this.checkIn = checkIn;
        this.attendTime = attendTime;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
