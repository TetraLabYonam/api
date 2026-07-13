package com.example.attempt.repository;

import com.example.attempt.domain.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Schedule 엔티티 Repository
 */
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    /**
     * 날짜 범위로 일정 조회
     */
    List<Schedule> findByScheduleDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * 특정 날짜의 일정 조회
     */
    List<Schedule> findByScheduleDate(LocalDate date);

    /**
     * 특정 장소의 일정 조회
     */
    List<Schedule> findByPlaceId(Long placeId);

    /**
     * 활성 상태인 일정 조회
     */
    List<Schedule> findByIsActiveTrue();

    /**
     * 일정과 출석 정보를 함께 조회 (N+1 문제 해결)
     */
    @Query("SELECT s FROM Schedule s LEFT JOIN FETCH s.attends WHERE s.id = :id")
    Optional<Schedule> findByIdWithAttends(@Param("id") Long id);

    /**
     * 특정 날짜 범위의 활성 일정 조회
     */
    @Query("SELECT s FROM Schedule s WHERE s.scheduleDate BETWEEN :startDate AND :endDate AND s.isActive = true ORDER BY s.scheduleDate")
    List<Schedule> findActiveSchedulesByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * 특정 회원이 참석하는 일정 조회
     */
    @Query("SELECT DISTINCT s FROM Schedule s JOIN s.attends a WHERE a.member.id = :memberId AND s.scheduleDate BETWEEN :startDate AND :endDate")
    List<Schedule> findByMemberIdAndDateRange(@Param("memberId") Long memberId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
