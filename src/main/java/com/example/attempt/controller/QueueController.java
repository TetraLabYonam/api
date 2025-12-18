package com.example.attempt.controller;

import com.example.attempt.dto.*;
import com.example.attempt.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 대기열(Queue) 관련 REST API 컨트롤러
 * Flutter 앱과 통신하는 엔드포인트 제공
 */
@Slf4j
@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    /**
     * 활성화된 방 목록 조회
     * GET /api/queue/rooms
     *
     * @return 활성화된 방 목록
     */
    @GetMapping("/rooms")
    public ResponseEntity<List<QueueRoomDto>> getActiveRooms() {
        log.info("활성화된 방 목록 조회 요청");
        List<QueueRoomDto> rooms = queueService.getActiveRooms();
        return ResponseEntity.ok(rooms);
    }

    /**
     * 특정 방 정보 조회
     * GET /api/queue/room/{roomId}
     *
     * @param roomId 방 UID
     * @return 방 정보
     */
    @GetMapping("/room/{roomId}")
    public ResponseEntity<QueueRoomDto> getRoomInfo(@PathVariable String roomId) {
        log.info("방 정보 조회 요청 - roomId: {}", roomId);
        QueueRoomDto room = queueService.getRoomInfo(roomId);
        return ResponseEntity.ok(room);
    }

    /**
     * 번호표 발급
     * POST /api/queue/room/{roomId}/ticket
     *
     * @param roomId 방 UID
     * @param request 발급 요청 (userDeviceId 포함)
     * @return 발급된 번호표 정보
     */
    @PostMapping("/room/{roomId}/ticket")
    public ResponseEntity<TicketIssueResponse> issueTicket(
            @PathVariable String roomId,
            @RequestBody TicketIssueRequest request) {

        log.info("번호표 발급 요청 - roomId: {}, deviceId: {}", roomId, request.getUserDeviceId());

        TicketIssueResponse response = queueService.issueTicket(roomId, request.getUserDeviceId());

        return ResponseEntity
                .status(response.getDuplicated() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(response);
    }

    /**
     * 번호표 취소
     * DELETE /api/queue/ticket/{ticketId}
     *
     * @param ticketId 번호표 ID
     * @return 성공 메시지
     */
    @DeleteMapping("/ticket/{ticketId}")
    public ResponseEntity<String> cancelTicket(@PathVariable Long ticketId) {
        log.info("번호표 취소 요청 - ticketId: {}", ticketId);
        queueService.cancelTicket(ticketId);
        return ResponseEntity.ok("번호표가 취소되었습니다.");
    }

    /**
     * 방 현황 조회
     * GET /api/queue/room/{roomId}/status
     *
     * @param roomId 방 UID
     * @return 방 현황 정보
     */
    @GetMapping("/room/{roomId}/status")
    public ResponseEntity<RoomStatusResponse> getRoomStatus(@PathVariable String roomId) {
        log.info("방 현황 조회 요청 - roomId: {}", roomId);
        RoomStatusResponse status = queueService.getRoomStatus(roomId);
        return ResponseEntity.ok(status);
    }

    /**
     * 특정 방의 모든 번호표 조회
     * GET /api/queue/room/{roomId}/tickets
     *
     * @param roomId 방 UID
     * @return 번호표 목록
     */
    @GetMapping("/room/{roomId}/tickets")
    public ResponseEntity<List<QueueTicketDto>> getRoomTickets(@PathVariable String roomId) {
        log.info("방 번호표 목록 조회 요청 - roomId: {}", roomId);
        List<QueueTicketDto> tickets = queueService.getRoomTickets(roomId);
        return ResponseEntity.ok(tickets);
    }

    /**
     * 다음 번호 호출 (관리자용)
     * POST /api/queue/room/{roomId}/call
     *
     * @param roomId 방 UID
     * @return 호출된 번호
     */
    @PostMapping("/room/{roomId}/call")
    public ResponseEntity<Integer> callNextNumber(@PathVariable String roomId) {
        log.info("다음 번호 호출 요청 - roomId: {}", roomId);
        Integer calledNumber = queueService.callNextNumber(roomId);
        return ResponseEntity.ok(calledNumber);
    }
}