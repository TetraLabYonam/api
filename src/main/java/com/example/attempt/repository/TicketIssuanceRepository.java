package com.example.attempt.repository;

import com.example.attempt.domain.TicketIssuance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TicketIssuanceRepository extends JpaRepository<TicketIssuance, Long> {
    Optional<TicketIssuance> findByRoomIdAndUserKey(Long roomId, String userKey);
    long countByRoomId(Long roomId);
    void deleteByRoomId(Long roomId);
    java.util.List<TicketIssuance> findByRoomIdOrderByNumberAsc(Long roomId);
}
