package com.example.attempt.service;

import com.example.attempt.domain.Attend;
import com.example.attempt.domain.Member;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.format.DateTimeFormatter;

/**
 * SMS 전송 서비스
 * - CoolSMS API를 사용하여 SMS 메시지 전송
 * - 출석, 결석 등의 알림 메시지 전송
 */
@Service
@ConditionalOnProperty(prefix = "coolsms", name = "api.key")
@Slf4j
public class SmsService {

    private final DefaultMessageService messageService;
    private final String senderNumber;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public SmsService(
            @Value("${coolsms.api.key}") String apiKey,
            @Value("${coolsms.api.secret}") String apiSecret,
            @Value("${coolsms.api.number}") String senderNumber
    ) {
        this.messageService = NurigoApp.INSTANCE.initialize(apiKey, apiSecret, "https://api.coolsms.co.kr");
        this.senderNumber = senderNumber;
        log.info("SmsService initialized with sender number: {}", senderNumber);
    }

    /**
     * 보호자에게 SMS 메시지 전송
     */
    public void sendGuardianMessage(Member member, String text) {
        if (member == null || member.getGuardianPhone() == null || member.getGuardianPhone().isBlank()) {
            log.warn("보호자 전화번호가 없어 SMS를 전송할 수 없습니다. memberId={}",
                    member != null ? member.getId() : "null");
            return;
        }

        try {
            Message message = new Message();
            message.setFrom(senderNumber);
            message.setTo(member.getGuardianPhone());
            message.setText(text);

            messageService.sendOne(new SingleMessageSendingRequest(message));
            log.info("SMS 전송 성공: to={}, message={}", member.getGuardianPhone(), text);
        } catch (Exception e) {
            log.error("SMS 전송 실패: to={}, message={}", member.getGuardianPhone(), text, e);
            // SMS 전송 실패는 비즈니스 로직에 영향을 주지 않도록 예외를 던지지 않음
        }
    }

    /**
     * 출석 알림 SMS 전송
     */
    public void sendAttendanceNotification(Attend attend) {
        if (attend == null || attend.getAttendedAt() == null) {
            log.warn("출석 정보가 없어 SMS를 전송할 수 없습니다.");
            return;
        }

        String statusMessage = switch (attend.getStatus()) {
            case PRESENT -> "출석";
            case LATE -> "지각";
            case ABSENT -> "결석";
            case EXCUSED -> "사유 인정 결석";
            default -> "출석 처리";
        };

        String message = String.format("[%s 알림] %s 님이 %s에 %s 처리되었습니다.",
                statusMessage,
                attend.getMember().getUsername(),
                TIME_FORMAT.format(attend.getAttendedAt()),
                statusMessage);

        sendGuardianMessage(attend.getMember(), message);
    }

    /**
     * 결석 알림 SMS 전송
     */
    public void sendAbsenceNotification(Attend attend, String reason) {
        if (attend == null) {
            log.warn("출석 정보가 없어 SMS를 전송할 수 없습니다.");
            return;
        }

        String message = String.format("[결석 알림] %s 님이 결석 처리되었습니다.%s",
                attend.getMember().getUsername(),
                reason != null && !reason.isBlank() ? "\n사유: " + reason : "");

        sendGuardianMessage(attend.getMember(), message);
    }

    /**
     * 커스텀 메시지 전송
     */
    public void sendCustomMessage(String phoneNumber, String text) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.warn("전화번호가 없어 SMS를 전송할 수 없습니다.");
            return;
        }

        try {
            Message message = new Message();
            message.setFrom(senderNumber);
            message.setTo(phoneNumber);
            message.setText(text);

            messageService.sendOne(new SingleMessageSendingRequest(message));
            log.info("커스텀 SMS 전송 성공: to={}, message={}", phoneNumber, text);
        } catch (Exception e) {
            log.error("커스텀 SMS 전송 실패: to={}, message={}", phoneNumber, text, e);
        }
    }
}