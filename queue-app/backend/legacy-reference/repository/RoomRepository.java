package com.example.attempt.repository;

import com.example.attempt.domain.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByRoomUid(String roomUid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.roomUid = :uid")
    Optional<Room> findByRoomUidForUpdate(@Param("uid") String roomUid);

    // 활성화된 방만 조회
    List<Room> findByIsActiveTrue();

    // 활성화된 방 전체 조회 (정렬)
    List<Room> findByIsActiveTrueOrderByCreatedAtDesc();
}
