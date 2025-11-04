package com.example.attempt.repository;

import com.example.attempt.domain.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByRoomUid(String roomUid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.roomUid = :uid")
    Optional<Room> findByRoomUidForUpdate(@Param("uid") String roomUid);

}
