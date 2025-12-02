package com.example.attempt.controller;

import com.example.attempt.domain.Attend;
import com.example.attempt.domain.Member;
import com.example.attempt.service.AttendService;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/sms")
public class SmsController {

    private final AttendService attendService;
    private final DefaultMessageService messageService;
    private final String senderNumber;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public SmsController(
            AttendService attendService,
            @Value("${coolsms.api.key}") String apiKey,
            @Value("${coolsms.api.secret}") String apiSecret,
            @Value("${coolsms.api.number}") String senderNumber
    ) {
        this.attendService = attendService;
        this.messageService = NurigoApp.INSTANCE.initialize(apiKey, apiSecret, "https://api.coolsms.co.kr");
        this.senderNumber = senderNumber;
    }

    //출근 메세지 전송
    @PostMapping("/clock-in")
    public Map<String, Object> clockIn(
            @RequestParam Long memberId,
            @RequestParam Long scheduleId,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude
    ) {
        Attend attend = attendService.clockIn(memberId, scheduleId, latitude, longitude);
        sendGuardianMessage(attend.getMember(),
                "[출근 알림] " + attend.getMember().getUsername() + " 님이 "
                        + TIME_FORMAT.format(attend.getClockInTime()) + "에 출근했습니다.");
        return Map.of(
                "memberId", memberId,
                "scheduleId", scheduleId,
                "clockInTime", TIME_FORMAT.format(attend.getClockInTime())
        );
    }

    // 퇴근 메세지 전송
    @PostMapping("/clock-out")
    public Map<String, Object> clockOut(
            @RequestParam Long memberId,
            @RequestParam Long scheduleId
    ) {
        Attend attend = attendService.clockOut(memberId, scheduleId);
        sendGuardianMessage(attend.getMember(),
                "[퇴근 알림] " + attend.getMember().getUsername() + " 님이 "
                        + TIME_FORMAT.format(attend.getClockOutTime()) + "에 퇴근했습니다.");
        return Map.of(
                "memberId", memberId,
                "scheduleId", scheduleId,
                "clockOutTime", TIME_FORMAT.format(attend.getClockOutTime())
        );
    }

    private void sendGuardianMessage(Member member, String text) {
        if (member == null || member.getGuardianPhone() == null || member.getGuardianPhone().isBlank()) {
            return;
        }

        Message message = new Message();
        message.setFrom(senderNumber);
        message.setTo(member.getGuardianPhone());
        message.setText(text);

        messageService.sendOne(new SingleMessageSendingRequest(message));
    }
}
