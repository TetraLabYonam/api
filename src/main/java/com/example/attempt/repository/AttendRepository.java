package com.example.attempt.repository;

import com.example.attempt.domain.Attend;
import com.example.attempt.domain.AttendStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Attend 엔티티 Repository
 */
public interface AttendRepository extends JpaRepository<Attend, Long> {

    /**
     * 특정 일정과 회원으로 출석 정보 조회
     */
    Optional<Attend> findByScheduleIdAndMemberId(Long scheduleId, Long memberId);

    /**
     * 특정 회원의 모든 출석 정보 조회
     */
    List<Attend> findByMemberId(Long memberId);

    /**
     * 특정 일정의 모든 출석 정보 조회
     */
    List<Attend> findByScheduleId(Long scheduleId);

    /**
     * 특정 상태의 출석 정보 조회
     */
    List<Attend> findByStatus(AttendStatus status);

    /**
     * 특정 일정의 특정 상태 출석 정보 조회
     */
    List<Attend> findByScheduleIdAndStatus(Long scheduleId, AttendStatus status);

    /**
     * 특정 회원의 날짜 범위 내 출석 정보 조회
     */
    @Query("SELECT a FROM Attend a WHERE a.member.id = :memberId " +
           "AND a.schedule.scheduleDate BETWEEN :startDate AND :endDate")
    List<Attend> findByMemberIdAndDateRange(
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 특정 회원의 출석률 계산을 위한 통계 조회
     */
    @Query("SELECT a.status, COUNT(a) FROM Attend a " +
           "WHERE a.member.id = :memberId " +
           "AND a.schedule.scheduleDate BETWEEN :startDate AND :endDate " +
           "GROUP BY a.status")
    List<Object[]> getAttendanceStatsByMemberId(
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 특정 일정의 출석 통계 조회
     */
    @Query("SELECT a.status, COUNT(a) FROM Attend a " +
           "WHERE a.schedule.id = :scheduleId " +
           "GROUP BY a.status")
    List<Object[]> getAttendanceStatsByScheduleId(@Param("scheduleId") Long scheduleId);
}
