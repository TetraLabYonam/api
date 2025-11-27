package com.example.attempt.controller;

import com.example.attempt.domain.Member;
import com.example.attempt.repository.GuardianRepository;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.service.AttendService;
import com.example.attempt.service.SmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/attend")
@RequiredArgsConstructor
public class AttendController {

    private final AttendService attendService;
    private final GuardianRepository guardianRepository;
    private final MemberRepository memberRepository;
    private final SmsService smsService;

    @PostMapping("/check-in")
    public String checkIn(
            @RequestParam Long memberId,
            @RequestParam Long scheduleId,
            @RequestParam Double lat,
            @RequestParam Double lon
    ) {
        attendService.checkIn(memberId, scheduleId, lat, lon);

        Member member = memberRepository.find(memberId);
        if (member == null) throw new IllegalArgumentException("Member not found");

        guardianRepository.findByMemberId(memberId)
                .forEach(g -> {
                    if (Boolean.TRUE.equals(g.getReceiveNotification())) {
                        smsService.send(g.getPhone(),
                                "[출근 알림] " + member.getUsername() + "님이 출근하셨습니다.");
                    }
                });

        return "OK";
    }

    @PostMapping("/check-out")
    public String checkOut(
            @RequestParam Long memberId,
            @RequestParam Long scheduleId,
            @RequestParam Double lat,
            @RequestParam Double lon
    ) {
        attendService.checkOut(memberId, scheduleId, lat, lon);

        Member member = memberRepository.find(memberId);
        if (member == null) throw new IllegalArgumentException("Member not found");

        guardianRepository.findByMemberId(memberId)
                .forEach(g -> {
                    if (Boolean.TRUE.equals(g.getReceiveNotification())) {
                        smsService.send(g.getPhone(),
                                "[퇴근 알림] " + member.getUsername() + "님이 퇴근하셨습니다.");
                    }
                });

        return "OK";
    }
}
