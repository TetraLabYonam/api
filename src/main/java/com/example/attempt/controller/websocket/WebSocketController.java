package com.example.attempt.controller.websocket;

import com.example.attempt.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.NoSuchElementException;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketController {

    private final TicketService ticketService;
    private final SimpMessagingTemplate messagingTemplate;

    // DTO 클래스들
    public record JoinRequest(String roomUid, String userKey) {}
    public record IssueRequest(String roomUid, String userKey) {}
    public record NotifyRequest(String roomUid, Integer number, String message) {}

    /**
     * 방 입장
     * 클라이언트: /app/room/join
     * 응답: /topic/room/{roomUid}/state
     */
    @MessageMapping("/room/join")
    public void joinRoom(@Payload JoinRequest request, SimpMessageHeaderAccessor headerAccessor) {
        if (request.roomUid() == null || request.userKey() == null) {
            log.error("Invalid join request: {}", request);
            return;
        }

        try {
            // 세션에 사용자 정보 저장
            headerAccessor.getSessionAttributes().put("userKey", request.userKey());
            headerAccessor.getSessionAttributes().put("roomUid", request.roomUid());

            // 방 상태 조회
            Map<String, Object> snapshot = ticketService.snapshot(request.roomUid());

            // 해당 방 구독자들에게 상태 전송
            messagingTemplate.convertAndSend(
                "/topic/room/" + request.roomUid() + "/state",
                snapshot
            );

            log.info("User {} joined room {}", request.userKey(), request.roomUid());
        } catch (NoSuchElementException e) {
            log.error("Room not found: {}", request.roomUid());
            // 에러 전송
            messagingTemplate.convertAndSendToUser(
                headerAccessor.getSessionId(),
                "/queue/errors",
                Map.of("error", "room_not_found")
            );
        }
    }

    /**
     * 번호표 발급/확인
     * 클라이언트: /app/room/issue
     * 응답: /user/queue/ticket (개인), /topic/room/{roomUid}/state (전체)
     */
    @MessageMapping("/room/issue")
    public void issueTicket(@Payload IssueRequest request, SimpMessageHeaderAccessor headerAccessor) {
        if (request.roomUid() == null || request.userKey() == null) {
            log.error("Invalid issue request: {}", request);
            return;
        }

        try {
            // 번호표 발급
            Map<String, Object> result = ticketService.issue(request.roomUid(), request.userKey());

            // 발급받은 사용자에게 개인 메시지 전송
            messagingTemplate.convertAndSendToUser(
                headerAccessor.getSessionId(),
                "/queue/ticket",
                result
            );

            // 방 전체에 상태 업데이트 브로드캐스트
            messagingTemplate.convertAndSend(
                "/topic/room/" + request.roomUid() + "/state",
                Map.of("lastNumber", result.get("lastNumber"), "count", result.get("count"))
            );

            log.info("Ticket issued for user {} in room {}: {}", request.userKey(), request.roomUid(), result.get("number"));
        } catch (NoSuchElementException e) {
            log.error("Room not found: {}", request.roomUid());
            messagingTemplate.convertAndSendToUser(
                headerAccessor.getSessionId(),
                "/queue/errors",
                Map.of("error", "room_not_found")
            );
        }
    }

    /**
     * 특정 번호에 알림 전송 (관리자)
     * 클라이언트: /app/room/notify
     */
    @MessageMapping("/room/notify")
    public void sendNotification(@Payload NotifyRequest request, SimpMessageHeaderAccessor headerAccessor) {
        if (request.roomUid() == null || request.number() == null || request.message() == null) {
            log.error("Invalid notify request: {}", request);
            return;
        }

        try {
            // 해당 번호의 userKey 찾기
            String targetUserKey = ticketService.getUserKeyByNumber(request.roomUid(), request.number());

            if (targetUserKey == null) {
                log.error("Number not found: {} in room {}", request.number(), request.roomUid());
                messagingTemplate.convertAndSendToUser(
                    headerAccessor.getSessionId(),
                    "/queue/errors",
                    Map.of("error", "number_not_found")
                );
                return;
            }

            // 해당 userKey를 가진 사용자에게 알림 전송
            // 주의: 실제로는 세션 ID를 추적해야 하지만, 간단히 topic으로 브로드캐스트
            messagingTemplate.convertAndSend(
                "/topic/room/" + request.roomUid() + "/notification/" + targetUserKey,
                Map.of("number", request.number(), "message", request.message())
            );

            log.info("Notification sent to number {} in room {}", request.number(), request.roomUid());
        } catch (Exception e) {
            log.error("Failed to send notification", e);
            messagingTemplate.convertAndSendToUser(
                headerAccessor.getSessionId(),
                "/queue/errors",
                Map.of("error", e.getMessage())
            );
        }
    }
}
