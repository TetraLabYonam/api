package com.example.attempt.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 출석 엔티티
 * - 회원(Member)의 일정(Schedule)에 대한 출석 정보를 관리
 * - 출석 상태(AttendStatus)를 통해 출석 예정/출석/결석 등을 구분
 */
@Entity
@Table(name = "ATTEND")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ATTEND_ID")
    private Long id;

    // 관계
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MEMBER_ID", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SCHEDULE_ID", nullable = false)
    private Schedule schedule;

    // 출석 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    @Builder.Default
    private AttendStatus status = AttendStatus.SCHEDULED;

    // 위치 정보 (실제 출석 시 기록)
    @Column(name = "LATITUDE")
    private Double latitude;

    @Column(name = "LONGITUDE")
    private Double longitude;

    // 출석 시간
    @Column(name = "ATTENDED_AT")
    private LocalDateTime attendedAt;

    // 비고 (결석 사유 등)
    @Column(name = "NOTE", length = 500)
    private String note;

    // 타임스탬프
    @CreationTimestamp
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    // 편의 생성자 (하위 호환성)
    public Attend(Member member, Schedule schedule, Double latitude, Double longitude) {
        this.member = member;
        this.schedule = schedule;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = AttendStatus.PRESENT;
        this.attendedAt = LocalDateTime.now();
    }

    // 비즈니스 로직 메서드

    /**
     * 출석 처리
     */
    public void markPresent(Double latitude, Double longitude) {
        this.status = AttendStatus.PRESENT;
        this.latitude = latitude;
        this.longitude = longitude;
        this.attendedAt = LocalDateTime.now();
    }

    /**
     * 결석 처리
     */
    public void markAbsent(String reason) {
        this.status = AttendStatus.ABSENT;
        this.note = reason;
    }

    /**
     * 지각 처리
     */
    public void markLate(Double latitude, Double longitude, String reason) {
        this.status = AttendStatus.LATE;
        this.latitude = latitude;
        this.longitude = longitude;
        this.attendedAt = LocalDateTime.now();
        this.note = reason;
    }

    /**
     * 사유 인정 결석 처리
     */
    public void markExcused(String reason) {
        this.status = AttendStatus.EXCUSED;
        this.note = reason;
    }

    /**
     * 출석 완료 여부 확인
     */
    public boolean isAttended() {
        return status == AttendStatus.PRESENT || status == AttendStatus.LATE;
    }

    /**
     * 결석 여부 확인
     */
    public boolean isAbsent() {
        return status == AttendStatus.ABSENT || status == AttendStatus.EXCUSED;
    }

    /**
     * 출석 예정 여부 확인
     */
    public boolean isScheduled() {
        return status == AttendStatus.SCHEDULED;
    }
}
