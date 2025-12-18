package com.example.attempt.service;

import com.example.attempt.domain.Room;
import com.example.attempt.domain.TicketIssuance;
import com.example.attempt.dto.QueueRoomDto;
import com.example.attempt.dto.QueueTicketDto;
import com.example.attempt.dto.RoomStatusResponse;
import com.example.attempt.dto.TicketIssueResponse;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.RoomRepository;
import com.example.attempt.repository.TicketIssuanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 대기열(Queue) 관련 비즈니스 로직 처리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private final RoomRepository roomRepository;
    private final TicketIssuanceRepository ticketRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 활성화된 방 목록 조회
     * @return 활성화된 방 DTO 목록
     */
    @Transactional(readOnly = true)
    public List<QueueRoomDto> getActiveRooms() {
        List<Room> activeRooms = roomRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        return activeRooms.stream()
                .map(this::convertToRoomDto)
                .collect(Collectors.toList());
    }

    /**
     * 특정 방 정보 조회
     * @param roomId 방 UID
     * @return 방 DTO
     */
    @Transactional(readOnly = true)
    public QueueRoomDto getRoomInfo(String roomId) {
        Room room = findRoomByUid(roomId);
        return convertToRoomDto(room);
    }

    /**
     * 번호표 발급
     * @param roomId 방 UID
     * @param userDeviceId 사용자 디바이스 ID
     * @return 발급 응답 DTO
     */
    @Transactional
    public TicketIssueResponse issueTicket(String roomId, String userDeviceId) {
        // 방 조회 및 잠금
        Room room = roomRepository.findByRoomUidForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("방을 찾을 수 없습니다: " + roomId));

        // 이미 발급된 번호표 확인
        var existingTicket = ticketRepository.findByRoomIdAndUserKey(room.getId(), userDeviceId);
        if (existingTicket.isPresent()) {
            TicketIssuance ticket = existingTicket.get();
            long waitingCount = ticketRepository.countByRoomIdAndStatus(
                    room.getId(),
                    TicketIssuance.TicketStatus.WAITING
            );

            log.info("중복 발급 - 방: {}, 디바이스: {}, 번호: {}",
                    roomId, userDeviceId, ticket.getNumber());

            return TicketIssueResponse.builder()
                    .number(ticket.getNumber())
                    .duplicated(true)
                    .lastNumber(room.getCurrentNumber())
                    .count(waitingCount)
                    .build();
        }

        // 새 번호표 발급
        int newNumber = room.getLastIssuedNumber() + 1;
        room.setLastIssuedNumber(newNumber);

        try {
            TicketIssuance newTicket = new TicketIssuance(room, userDeviceId, newNumber);
            ticketRepository.save(newTicket);

            long waitingCount = ticketRepository.countByRoomIdAndStatus(
                    room.getId(),
                    TicketIssuance.TicketStatus.WAITING
            );

            log.info("번호표 발급 - 방: {}, 디바이스: {}, 번호: {}",
                    roomId, userDeviceId, newNumber);

            // WebSocket으로 실시간 업데이트 전송
            sendRoomUpdate(roomId, room);

            return TicketIssueResponse.builder()
                    .number(newNumber)
                    .duplicated(false)
                    .lastNumber(room.getCurrentNumber())
                    .count(waitingCount)
                    .build();

        } catch (DataIntegrityViolationException e) {
            // 동시성 문제로 인한 중복 발급 시도
            var ticket = ticketRepository.findByRoomIdAndUserKey(room.getId(), userDeviceId)
                    .orElseThrow(() -> e);

            long waitingCount = ticketRepository.countByRoomIdAndStatus(
                    room.getId(),
                    TicketIssuance.TicketStatus.WAITING
            );

            return TicketIssueResponse.builder()
                    .number(ticket.getNumber())
                    .duplicated(true)
                    .lastNumber(room.getCurrentNumber())
                    .count(waitingCount)
                    .build();
        }
    }

    /**
     * 번호표 취소
     * @param ticketId 번호표 ID
     */
    @Transactional
    public void cancelTicket(Long ticketId) {
        TicketIssuance ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("번호표를 찾을 수 없습니다: " + ticketId));

        ticket.setStatus(TicketIssuance.TicketStatus.CANCELLED);
        ticketRepository.save(ticket);

        log.info("번호표 취소 - ID: {}, 번호: {}", ticketId, ticket.getNumber());

        // WebSocket으로 실시간 업데이트 전송
        sendRoomUpdate(ticket.getRoom().getRoomUid(), ticket.getRoom());
    }

    /**
     * 방 현황 조회
     * @param roomId 방 UID
     * @return 방 현황 응답 DTO
     */
    @Transactional(readOnly = true)
    public RoomStatusResponse getRoomStatus(String roomId) {
        Room room = findRoomByUid(roomId);

        long waitingCount = ticketRepository.countByRoomIdAndStatus(
                room.getId(),
                TicketIssuance.TicketStatus.WAITING
        );

        return RoomStatusResponse.builder()
                .roomId(room.getRoomUid())
                .roomName(room.getTitle())
                .currentNumber(room.getCurrentNumber())
                .lastIssuedNumber(room.getLastIssuedNumber())
                .waitingCount(waitingCount)
                .isActive(room.getIsActive())
                .build();
    }

    /**
     * 특정 방의 모든 번호표 조회
     * @param roomId 방 UID
     * @return 번호표 DTO 목록
     */
    @Transactional(readOnly = true)
    public List<QueueTicketDto> getRoomTickets(String roomId) {
        Room room = findRoomByUid(roomId);

        List<TicketIssuance> tickets = ticketRepository.findByRoomIdOrderByNumberAsc(room.getId());

        return tickets.stream()
                .map(QueueTicketDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 다음 번호 호출
     * @param roomId 방 UID
     * @return 호출된 번호
     */
    @Transactional
    public Integer callNextNumber(String roomId) {
        Room room = roomRepository.findByRoomUidForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("방을 찾을 수 없습니다: " + roomId));

        int nextNumber = room.getCurrentNumber() + 1;
        room.setCurrentNumber(nextNumber);
        roomRepository.save(room);

        // 해당 번호의 티켓 상태를 CALLED로 변경
        var ticketOpt = ticketRepository.findByRoomIdAndNumber(room.getId(), nextNumber);
        ticketOpt.ifPresent(ticket -> {
            ticket.setStatus(TicketIssuance.TicketStatus.CALLED);
            ticket.setCalledAt(java.time.LocalDateTime.now());
            ticketRepository.save(ticket);
        });

        log.info("번호 호출 - 방: {}, 번호: {}", roomId, nextNumber);

        // WebSocket으로 실시간 업데이트 전송
        sendRoomUpdate(roomId, room);

        return nextNumber;
    }

    /**
     * Room 엔티티를 DTO로 변환
     */
    private QueueRoomDto convertToRoomDto(Room room) {
        long waitingCount = ticketRepository.countByRoomIdAndStatus(
                room.getId(),
                TicketIssuance.TicketStatus.WAITING
        );

        return QueueRoomDto.builder()
                .id(room.getId())
                .roomUid(room.getRoomUid())
                .roomName(room.getTitle())
                .isActive(room.getIsActive())
                .currentNumber(room.getCurrentNumber())
                .lastIssuedNumber(room.getLastIssuedNumber())
                .waitingCount((int) waitingCount)
                .createdAt(room.getCreatedAt())
                .updatedAt(room.getUpdatedAt())
                .build();
    }

    /**
     * UID로 방 조회
     */
    private Room findRoomByUid(String roomUid) {
        return roomRepository.findByRoomUid(roomUid)
                .orElseThrow(() -> new ResourceNotFoundException("방을 찾을 수 없습니다: " + roomUid));
    }

    /**
     * WebSocket을 통한 실시간 업데이트 전송
     */
    private void sendRoomUpdate(String roomId, Room room) {
        try {
            RoomStatusResponse status = RoomStatusResponse.builder()
                    .roomId(room.getRoomUid())
                    .roomName(room.getTitle())
                    .currentNumber(room.getCurrentNumber())
                    .lastIssuedNumber(room.getLastIssuedNumber())
                    .waitingCount(ticketRepository.countByRoomIdAndStatus(
                            room.getId(),
                            TicketIssuance.TicketStatus.WAITING
                    ))
                    .isActive(room.getIsActive())
                    .build();

            messagingTemplate.convertAndSend("/topic/room/" + roomId, status);
        } catch (Exception e) {
            log.error("WebSocket 메시지 전송 실패: {}", e.getMessage());
        }
    }
}