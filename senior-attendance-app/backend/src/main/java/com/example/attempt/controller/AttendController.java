package com.example.attempt.controller;

import com.example.attempt.domain.Member;
import com.example.attempt.dto.attend.AttendCheckInApiRequest;
import com.example.attempt.dto.attend.AttendCheckInRequest;
import com.example.attempt.dto.attend.AttendCheckInResponse;
import com.example.attempt.dto.attend.AttendTodayResponse;
import com.example.attempt.service.AttendService;
import com.example.attempt.service.CurrentMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/attend")
@RequiredArgsConstructor
public class AttendController {

    private final AttendService attendService;
    private final CurrentMemberService currentMemberService;

    @PostMapping("/check-in")
    public AttendCheckInResponse checkIn(@Valid @RequestBody AttendCheckInApiRequest request) {
        Member member = currentMemberService.getCurrentMember();

        if (member.getLocationConsentAgreedAt() == null) {
            throw new IllegalStateException("위치정보 수집 동의가 필요합니다. 동의 후 출석 체크가 가능합니다.");
        }

        AttendCheckInRequest serviceRequest = AttendCheckInRequest.builder()
                .scheduleId(request.getScheduleId())
                .memberId(member.getId())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();

        return attendService.checkIn(serviceRequest);
    }

    @GetMapping("/today")
    public AttendTodayResponse today() {
        Member member = currentMemberService.getCurrentMember();
        return attendService.findTodayAttend(member.getId());
    }
}
