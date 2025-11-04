package com.example.attempt.controller.ticket.socket;

import com.corundumstudio.socketio.SocketIOServer;
import com.example.attempt.controller.ticket.TicketService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class SocketHandlers {

    private final SocketIOServer server;
    private final TicketService ticketService;

    @PostConstruct
    public void bind() {
        server.addEventListener("join", JoinReq.class, (client, data, ack) -> {
            if (invalid(data.roomId()) || invalid(data.userKey())) return;
            client.joinRoom(data.roomId());
            client.sendEvent("state", ticketService.snapshot(data.roomId()));
        });

        server.addEventListener("issue", IssueReq.class, (client, data, ack) -> {
            if (invalid(data.roomId()) || invalid(data.userKey())) {
                if (ack.isAckRequested()) ack.sendAckData(Map.of("error","invalid_args"));
                return;
            }
            var r = ticketService.issue(data.roomId(), data.userKey());
            if (ack.isAckRequested()) ack.sendAckData(Map.of("number", r.number(), "duplicated", r.duplicated()));
            server.getRoomOperations(data.roomId()).sendEvent("issued", r.number());
            server.getRoomOperations(data.roomId()).sendEvent("state", Map.of("lastNumber", r.lastNumber(), "count", r.count()));
        });
    }

    private boolean invalid(String s){ return s == null || s.isBlank(); }
    public record JoinReq(String roomId, String userKey) {}
    public record IssueReq(String roomId, String userKey) {}
}
