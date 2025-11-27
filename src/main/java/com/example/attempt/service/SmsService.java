package com.example.attempt.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SmsService {

    private DefaultMessageService messageService;

    @Value("${coolsms.api-key}")
    private String apiKey;

    @Value("${coolsms.api-secret}")
    private String apiSecret;

    @Value("${coolsms.from}")
    private String fromNumber;

    @PostConstruct
    public void init() {
        this.messageService = NurigoApp.INSTANCE.initialize(apiKey, apiSecret, "https://api.coolsms.co.kr");
        log.info("CoolSMS 초기화 완료");
    }

    public void send(String to, String text) {
        try {
            Message message = new Message();
            message.setFrom(fromNumber);
            message.setTo(to);
            message.setText(text);

            this.messageService.sendOne(new SingleMessageSendingRequest(message));
            log.info("문자 발송 성공: to={}, text={}", to, text);
        } catch (Exception e) {
            log.error("문자 발송 실패", e);
        }
    }
}
