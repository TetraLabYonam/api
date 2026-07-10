package com.example.attempt.controller;

import com.example.attempt.domain.Member;
import com.example.attempt.dto.memberauth.AssignPlaceRequest;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.repository.PlaceRepository;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.service.CurrentMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/members/me")
@RequiredArgsConstructor
public class MemberSelfController {

    private final CurrentMemberService currentMemberService;
    private final MemberRepository memberRepository;
    private final PlaceRepository placeRepository;

    @GetMapping
    public Map<String, Object> me() {
        Member member = currentMemberService.getCurrentMember();
        return Map.of(
                "id", member.getId(),
                "username", member.getUsername(),
                "phoneNumber", member.getPhoneNumber(),
                "assignedPlaceId", member.getAssignedPlaceId() == null ? "" : member.getAssignedPlaceId(),
                "locationConsentAgreed", member.getLocationConsentAgreedAt() != null
        );
    }

    @PostMapping("/consent")
    public ResponseEntityBody consent() {
        Member member = currentMemberService.getCurrentMember();
        member.setLocationConsentAgreedAt(LocalDateTime.now());
        memberRepository.save(member);
        return new ResponseEntityBody("위치정보 수집에 동의했습니다.");
    }

    @PostMapping("/assign-place")
    public ResponseEntityBody assignPlace(@Valid @RequestBody AssignPlaceRequest request) {
        placeRepository.findById(request.getPlaceId())
                .orElseThrow(() -> new ResourceNotFoundException("장소를 찾을 수 없습니다. ID: " + request.getPlaceId()));

        Member member = currentMemberService.getCurrentMember();
        member.setAssignedPlaceId(request.getPlaceId());
        memberRepository.save(member);
        return new ResponseEntityBody("본인 일자리로 등록되었습니다.");
    }

    private record ResponseEntityBody(String message) {}
}
