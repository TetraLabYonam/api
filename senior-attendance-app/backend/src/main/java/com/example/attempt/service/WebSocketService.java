package com.example.attempt.service;

import com.example.attempt.domain.Room;
import com.example.attempt.dto.QueueMessageDto;
import com.example.attempt.dto.QueueMessageDto.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * WebSocket 메시지 전송 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 방 업데이트 전송
     * @param room 업데이트할 방
     */
    public void sendUpdate(Room room) {
        QueueMessageDto message = QueueMessageDto.builder()
                .type(MessageType.UPDATE)
                .roomId(room.getId())
                .currentNumber(room.getCurrentNumber())
                .waitingCount(Math.max(0, room.getLastIssuedNumber() - room.getCurrentNumber()))
                .build();

        String destination = "/topic/queue/" + room.getId();
        messagingTemplate.convertAndSend(destination, message);

        log.debug("WebSocket 업데이트 전송: {} - 현재번호: {}, 대기: {}",
                destination, room.getCurrentNumber(), message.getWaitingCount());
    }

    /**
     * 번호 호출 알림
     * @param room 방
     * @param calledNumber 호출된 번호
     */
    public void sendCallNotification(Room room, int calledNumber) {
        QueueMessageDto message = QueueMessageDto.builder()
                .type(MessageType.CALL)
                .roomId(room.getId())
                .currentNumber(room.getCurrentNumber())
                .calledNumber(calledNumber)
                .waitingCount(Math.max(0, room.getLastIssuedNumber() - room.getCurrentNumber()))
                .build();

        String destination = "/topic/queue/" + room.getId();
        messagingTemplate.convertAndSend(destination, message);

        log.info("번호 호출 알림 전송: {} - 호출번호: {}", destination, calledNumber);
    }

    /**
     * 번호표 취소 알림
     * @param room 방
     * @param cancelledNumber 취소된 번호
     */
    public void sendCancelNotification(Room room, int cancelledNumber) {
        QueueMessageDto message = QueueMessageDto.builder()
                .type(MessageType.CANCEL)
                .roomId(room.getId())
                .currentNumber(room.getCurrentNumber())
                .calledNumber(cancelledNumber)
                .waitingCount(Math.max(0, room.getLastIssuedNumber() - room.getCurrentNumber()))
                .build();

        String destination = "/topic/queue/" + room.getId();
        messagingTemplate.convertAndSend(destination, message);

        log.info("번호표 취소 알림 전송: {} - 취소번호: {}", destination, cancelledNumber);
    }
}
