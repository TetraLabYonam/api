package com.example.attempt.repository;

import com.example.attempt.domain.TicketIssuance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketIssuanceRepository extends JpaRepository<TicketIssuance, Long> {
    Optional<TicketIssuance> findByRoomIdAndUserKey(Long roomId, String userKey);
    Optional<TicketIssuance> findByRoomIdAndNumber(Long roomId, Integer number);
    long countByRoomId(Long roomId);
    void deleteByRoomId(Long roomId);
    List<TicketIssuance> findByRoomIdOrderByNumberAsc(Long roomId);

    // Status 관련 메소드
    List<TicketIssuance> findByRoomIdAndStatus(Long roomId, TicketIssuance.TicketStatus status);
    long countByRoomIdAndStatus(Long roomId, TicketIssuance.TicketStatus status);

    // 대기 중인 티켓만 조회
    List<TicketIssuance> findByRoomIdAndStatusOrderByNumberAsc(Long roomId, TicketIssuance.TicketStatus status);
}
