package com.example.attempt.controller;

import com.example.attempt.dto.QueueRoomDto;
import com.example.attempt.dto.RoomCreateRequest;
import com.example.attempt.dto.RoomUpdateRequest;
import com.example.attempt.service.RoomService;
import com.example.attempt.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Room API 컨트롤러
 * 번호표 방 관리 API
 */
@Tag(name = "Room", description = "번호표 방 관리 API")
@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
@Slf4j
public class RoomController {

    private final RoomService roomService;
    private final TicketService ticketService;

    /**
     * 모든 활성화된 방 조회
     */
    @Operation(summary = "활성화된 방 목록 조회", description = "활성화된 모든 방의 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<List<QueueRoomDto>> getAllActiveRooms() {
        log.info("GET /api/v1/rooms - 활성화된 방 목록 조회");
        List<QueueRoomDto> rooms = roomService.getAllActiveRooms();
        return ResponseEntity.ok(rooms);
    }

    /**
     * 모든 방 조회 (비활성화 포함)
     */
    @Operation(summary = "전체 방 목록 조회", description = "비활성화된 방을 포함한 모든 방의 목록을 조회합니다.")
    @GetMapping("/all")
    public ResponseEntity<List<QueueRoomDto>> getAllRooms() {
        log.info("GET /api/v1/rooms/all - 전체 방 목록 조회");
        List<QueueRoomDto> rooms = roomService.getAllRooms();
        return ResponseEntity.ok(rooms);
    }

    /**
     * 방 상세 조회 (TicketService 호환)
     */
    @Operation(summary = "방 상세 조회 with Details", description = "모든 방의 상세 정보를 조회합니다 (TicketService 기반).")
    @GetMapping("/details")
    public ResponseEntity<List<Map<String, Object>>> getAllRoomsWithDetails() {
        log.info("GET /api/v1/rooms/details - 방 상세 목록 조회");
        List<Map<String, Object>> rooms = ticketService.getAllRoomsWithDetails();
        return ResponseEntity.ok(rooms);
    }

    /**
     * 방 ID로 조회
     */
    @Operation(summary = "방 상세 조회 (ID)", description = "방 ID로 특정 방의 상세 정보를 조회합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<QueueRoomDto> getRoomById(
            @Parameter(description = "방 ID", required = true)
            @PathVariable Long id) {
        log.info("GET /api/v1/rooms/{} - 방 조회", id);
        QueueRoomDto room = roomService.getRoomById(id);
        return ResponseEntity.ok(room);
    }

    /**
     * 방 UID로 조회
     */
    @Operation(summary = "방 상세 조회 (UID)", description = "방 UID로 특정 방의 상세 정보를 조회합니다.")
    @GetMapping("/uid/{roomUid}")
    public ResponseEntity<QueueRoomDto> getRoomByUid(
            @Parameter(description = "방 UID", required = true)
            @PathVariable String roomUid) {
        log.info("GET /api/v1/rooms/uid/{} - 방 조회", roomUid);
        QueueRoomDto room = roomService.getRoomByUid(roomUid);
        return ResponseEntity.ok(room);
    }

    /**
     * 방 상태 조회 (TicketService 기반)
     */
    @Operation(summary = "방 상태 조회", description = "방의 현재 상태를 조회합니다 (TicketService 기반).")
    @GetMapping("/{uid}/state")
    public ResponseEntity<Map<String, Object>> getRoomState(
            @Parameter(description = "방 UID", required = true)
            @PathVariable String uid) {
        log.info("GET /api/v1/rooms/{}/state - 방 상태 조회", uid);
        Map<String, Object> state = ticketService.snapshot(uid);
        return ResponseEntity.ok(state);
    }

    /**
     * 방 발급 내역 조회
     */
    @Operation(summary = "방 발급 내역 조회", description = "방의 번호표 발급 내역을 조회합니다.")
    @GetMapping("/{uid}/issuances")
    public ResponseEntity<List<Map<String, Object>>> getIssuances(
            @Parameter(description = "방 UID", required = true)
            @PathVariable String uid) {
        log.info("GET /api/v1/rooms/{}/issuances - 발급 내역 조회", uid);
        List<Map<String, Object>> issuances = ticketService.getIssuances(uid);
        return ResponseEntity.ok(issuances);
    }

    /**
     * 새 방 생성
     */
    @Operation(summary = "방 생성", description = "새로운 번호표 방을 생성합니다.")
    @PostMapping
    public ResponseEntity<QueueRoomDto> createRoom(
            @Valid @RequestBody RoomCreateRequest request) {
        log.info("POST /api/v1/rooms - 방 생성: {}", request.getRoomName());
        QueueRoomDto room = roomService.createRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(room);
    }

    /**
     * 여러 방 일괄 생성
     */
    @Operation(summary = "방 일괄 생성", description = "여러 개의 방을 한 번에 생성합니다.")
    @PostMapping("/batch")
    public ResponseEntity<List<QueueRoomDto>> createRooms(
            @RequestBody Map<String, List<String>> requestBody) {
        List<String> roomNames = requestBody.get("roomNames");
        if (roomNames == null || roomNames.isEmpty()) {
            throw new IllegalArgumentException("roomNames는 필수입니다.");
        }
        log.info("POST /api/v1/rooms/batch - 방 일괄 생성: {} 개", roomNames.size());
        List<QueueRoomDto> rooms = roomService.createRooms(roomNames);
        return ResponseEntity.status(HttpStatus.CREATED).body(rooms);
    }

    /**
     * 방 정보 수정
     */
    @Operation(summary = "방 정보 수정", description = "방의 정보를 수정합니다.")
    @PutMapping("/{id}")
    public ResponseEntity<QueueRoomDto> updateRoom(
            @Parameter(description = "방 ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody RoomUpdateRequest request) {
        log.info("PUT /api/v1/rooms/{} - 방 수정", id);
        QueueRoomDto room = roomService.updateRoom(id, request);
        return ResponseEntity.ok(room);
    }

    /**
     * 방 번호 초기화
     */
    @Operation(summary = "방 번호 초기화", description = "방의 현재 번호와 마지막 발급 번호를 0으로 초기화합니다.")
    @PostMapping("/{uid}/reset")
    public ResponseEntity<Map<String, String>> resetRoom(
            @Parameter(description = "방 UID", required = true)
            @PathVariable String uid) {
        log.info("POST /api/v1/rooms/{}/reset - 방 번호 초기화", uid);
        ticketService.resetRoom(uid);
        return ResponseEntity.ok(Map.of("success", "true", "message", "방이 초기화되었습니다."));
    }

    /**
     * 방 비활성화
     */
    @Operation(summary = "방 비활성화", description = "방을 비활성화합니다.")
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<Map<String, String>> deactivateRoom(
            @Parameter(description = "방 ID", required = true)
            @PathVariable Long id) {
        log.info("PUT /api/v1/rooms/{}/deactivate - 방 비활성화", id);
        roomService.deactivateRoom(id);
        return ResponseEntity.ok(Map.of("message", "방이 비활성화되었습니다."));
    }

    /**
     * 방 활성화
     */
    @Operation(summary = "방 활성화", description = "방을 활성화합니다.")
    @PutMapping("/{id}/activate")
    public ResponseEntity<Map<String, String>> activateRoom(
            @Parameter(description = "방 ID", required = true)
            @PathVariable Long id) {
        log.info("PUT /api/v1/rooms/{}/activate - 방 활성화", id);
        roomService.activateRoom(id);
        return ResponseEntity.ok(Map.of("message", "방이 활성화되었습니다."));
    }

    /**
     * 방 삭제
     */
    @Operation(summary = "방 삭제", description = "방을 삭제합니다. (물리적 삭제)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteRoom(
            @Parameter(description = "방 ID", required = true)
            @PathVariable Long id) {
        log.info("DELETE /api/v1/rooms/{} - 방 삭제", id);
        roomService.deleteRoom(id);
        return ResponseEntity.ok(Map.of("message", "방이 삭제되었습니다."));
    }
}
