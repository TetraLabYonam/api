package com.example.attempt.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 일정 엔티티
 * - 특정 날짜의 장소에서 진행되는 일정 정보를 관리
 * - 일정에 참석하는 회원들의 출석 정보(Attend)와 연결
 */
@Entity
@Table(name = "SCHEDULE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SCHEDULE_ID")
    private Long id;

    // 일정 기본 정보
    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    @Column(name = "SCHEDULE_DATE", nullable = false)
    private LocalDate scheduleDate;

    @Column(name = "START_TIME")
    private LocalTime startTime;

    @Column(name = "END_TIME")
    private LocalTime endTime;

    // 관계
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PLACE_ID", nullable = false)
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CREATED_BY")
    private Admin createdBy;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Attend> attends = new ArrayList<>();

    // 상태 관리
    @Column(name = "IS_ACTIVE", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // 타임스탬프
    @CreationTimestamp
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    // 편의 메서드

    /**
     * 출석 정보 추가
     */
    public void addAttend(Attend attend) {
        attends.add(attend);
        attend.setSchedule(this);
    }

    /**
     * 출석 정보 제거
     */
    public void removeAttend(Attend attend) {
        attends.remove(attend);
        attend.setSchedule(null);
    }

    // 비즈니스 로직

    /**
     * 출석 완료한 인원 수
     */
    public long getPresentCount() {
        return attends.stream()
                .filter(a -> a.getStatus() == AttendStatus.PRESENT)
                .count();
    }

    /**
     * 결석한 인원 수
     */
    public long getAbsentCount() {
        return attends.stream()
                .filter(a -> a.getStatus() == AttendStatus.ABSENT)
                .count();
    }

    /**
     * 지각한 인원 수
     */
    public long getLateCount() {
        return attends.stream()
                .filter(a -> a.getStatus() == AttendStatus.LATE)
                .count();
    }

    /**
     * 출석 예정 인원 수
     */
    public long getScheduledCount() {
        return attends.stream()
                .filter(a -> a.getStatus() == AttendStatus.SCHEDULED)
                .count();
    }

    /**
     * 전체 참석 대상 인원 수
     */
    public long getTotalAttendees() {
        return attends.size();
    }

    /**
     * 출석률 계산 (%)
     */
    public double getAttendanceRate() {
        long total = getTotalAttendees();
        if (total == 0) {
            return 0.0;
        }
        long attended = getPresentCount() + getLateCount();
        return (attended * 100.0) / total;
    }

    /**
     * 일정이 진행 중인지 확인
     */
    public boolean isOngoing() {
        if (scheduleDate == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        return scheduleDate.isEqual(today);
    }

    /**
     * 일정이 종료되었는지 확인
     */
    public boolean isPast() {
        if (scheduleDate == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        return scheduleDate.isBefore(today);
    }

    /**
     * 일정이 예정되어 있는지 확인
     */
    public boolean isUpcoming() {
        if (scheduleDate == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        return scheduleDate.isAfter(today);
    }
}
