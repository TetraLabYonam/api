package com.example.attempt.controller;

import com.example.attempt.controller.ticket.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.Map;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {
    private final TicketService ticketService;
    private final SecureRandom rnd = new SecureRandom();
    private static final String ABC = "123456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    @PostMapping
    public Map<String, String> createRoom() {
        String roomId = randomId(8);
        ticketService.initRoom(roomId);
        return Map.of("roomId", roomId, "joinUrl", "/rooms/" + roomId);
    }

    private String randomId(int n) {
        var sb = new StringBuilder(n);
        for(int i=0;i<n;i++) sb.append(ABC.charAt(rnd.nextInt(ABC.length())));
        return sb.toString();
    }
}
