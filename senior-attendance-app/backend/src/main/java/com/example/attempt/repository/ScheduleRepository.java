package com.example.attempt.repository;

import com.example.attempt.domain.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    /**
     * 같은 장소·같은 날짜에 이미 일정이 있는지 확인 (일정 생성 시 중복 판단용)
     */
    boolean existsByPlaceIdAndScheduleDate(Long placeId, LocalDate scheduleDate);

    /**
     * 장소+날짜로 일정 1건 조회 (관리자 출석 조회용)
     * place, attends, attends.member를 JOIN FETCH로 한 번에 로딩하여 N+1을 방지한다.
     */
    @Query("SELECT DISTINCT s FROM Schedule s " +
           "JOIN FETCH s.place " +
           "JOIN FETCH s.attends a " +
           "JOIN FETCH a.member " +
           "WHERE s.place.id = :placeId AND s.scheduleDate = :scheduleDate")
    Optional<Schedule> findByPlaceIdAndScheduleDate(
            @Param("placeId") Long placeId,
            @Param("scheduleDate") LocalDate scheduleDate);
}
