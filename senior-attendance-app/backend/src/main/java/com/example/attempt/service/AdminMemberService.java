package com.example.attempt.service;

import com.example.attempt.domain.Member;
import com.example.attempt.domain.Place;
import com.example.attempt.dto.admin.MemberSummaryResponse;
import com.example.attempt.dto.admin.RegisterMemberRequest;
import com.example.attempt.dto.admin.RegisterMemberResponse;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 관리자 회원 등록/목록/활성화 토글 서비스.
 * 회원 등록 시 직번(employeeId)을 자동 채번하고 전화번호는 해시로만 저장한다.
 * 평문 전화번호는 QR 페이로드 응답에만 담기고 어디에도 저장되지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AdminMemberService {

    private final MemberRepository memberRepository;
    private final PlaceRepository placeRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public RegisterMemberResponse register(RegisterMemberRequest request) {
        Place place = placeRepository.findById(request.getPlaceId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 일자리입니다. placeId: " + request.getPlaceId()));

        Member member = Member.withPhoneNumberHash(
                request.getName(), passwordEncoder.encode(request.getPhoneNumber()));
        member.setEmployeeId(memberRepository.findMaxEmployeeIdOrDefault() + 1);
        member.setAssignedPlaceId(place.getId());
        member = memberRepository.save(member);

        String qrPayload = member.getEmployeeId() + ":" + request.getPhoneNumber();

        return new RegisterMemberResponse(
                member.getEmployeeId(), member.getUsername(), place.getId(), qrPayload);
    }

    @Transactional(readOnly = true)
    public List<MemberSummaryResponse> list() {
        List<Member> members = memberRepository.findAll();
        Map<Long, String> placeNamesById = placeRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Place::getId, Place::getName));

        return members.stream()
                .map(member -> new MemberSummaryResponse(
                        member.getEmployeeId(),
                        member.getUsername(),
                        member.getAssignedPlaceId(),
                        placeNamesById.get(member.getAssignedPlaceId()),
                        member.isActive()))
                .toList();
    }

    @Transactional
    public void updateActive(Long employeeId, boolean active) {
        Member member = memberRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "회원을 찾을 수 없습니다. employeeId: " + employeeId));
        member.setActive(active);
    }
}
