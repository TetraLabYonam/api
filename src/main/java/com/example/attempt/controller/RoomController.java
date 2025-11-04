package com.example.attempt.controller;


import com.example.attempt.domain.Room;
import com.example.attempt.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {
    private final TicketService ticketService;

    @GetMapping
    public List<Room> getAllRooms() {
        return ticketService.getAllRooms();
    }

    @GetMapping("/details")
    public List<Map<String, Object>> getAllRoomsWithDetails() {
        return ticketService.getAllRoomsWithDetails();
    }

    @PostMapping
    public Map<String, String> create(@RequestParam(required = false) String title) {
        String uid = ticketService.createRoom(title);
        return Map.of("roomUid", uid, "joinUrl", "/rooms/" + uid);
    }

    @GetMapping("/{uid}/state")
    public Map<String, Object> state(@PathVariable String uid) {
        return ticketService.snapshot(uid);
    }

    @GetMapping("/{uid}/issuances")
    public List<Map<String, Object>> issuances(@PathVariable String uid) {
        return ticketService.getIssuances(uid);
    }

    @PostMapping("/{uid}/reset")
    public Map<String, String> reset(@PathVariable String uid) {
        ticketService.resetRoom(uid);
        return Map.of("success", "true", "message", "방이 초기화되었습니다.");
    }
}
