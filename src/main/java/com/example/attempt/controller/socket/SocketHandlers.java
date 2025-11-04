package com.example.attempt.controller.socket;

import com.corundumstudio.socketio.SocketIOServer;
import com.example.attempt.service.TicketService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.NoSuchElementException;

@Component
@RequiredArgsConstructor
public class SocketHandlers {
    private final SocketIOServer server;
    private final TicketService service;

    public record JoinReq(String roomUid, String userKey) {}
    public record IssueReq(String roomUid, String userKey) {}

    @PostConstruct
    public void bind() {
        server.addEventListener("join", JoinReq.class, (c, d, ack) -> {
            if (d.roomUid()==null || d.userKey()==null) {
                if (ack.isAckRequested()) ack.sendAckData(Map.of("error","invalid_args"));
                return;
            }
            // 존재 확인 겸 스냅샷
            try {
                c.joinRoom(d.roomUid());
                var snapshot = service.snapshot(d.roomUid());
                c.sendEvent("state", snapshot);
                if (ack.isAckRequested()) ack.sendAckData(Map.of("success", true, "roomUid", d.roomUid()));
            } catch (NoSuchElementException e) {
                if (ack.isAckRequested()) ack.sendAckData(Map.of("error","room_not_found"));
            }
        });

        server.addEventListener("issue", IssueReq.class, (c, d, ack) -> {
            if (d.roomUid()==null || d.userKey()==null) {
                if (ack.isAckRequested()) ack.sendAckData(Map.of("error","invalid_args"));
                return;
            }
            try {
                var r = service.issue(d.roomUid(), d.userKey());
                int number = (Integer) r.get("number");
                boolean duplicated = (Boolean) r.get("duplicated");

                // ACK 응답
                if (ack.isAckRequested()) ack.sendAckData(Map.of("number", number, "duplicated", duplicated));

                // 발급받은 클라이언트에게 issued 이벤트 전송
                c.sendEvent("issued", number);

                // 방 전체에 state 업데이트 브로드캐스트
                server.getRoomOperations(d.roomUid()).sendEvent("state",
                        Map.of("lastNumber", r.get("lastNumber"), "count", r.get("count")));
            } catch (NoSuchElementException e) {
                if (ack.isAckRequested()) ack.sendAckData(Map.of("error","room_not_found"));
            }
        });
    }
}
