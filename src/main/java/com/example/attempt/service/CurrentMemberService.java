package com.example.attempt.service;

import com.example.attempt.domain.Member;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * JWT 인증 주체(휴대폰번호)로부터 현재 로그인한 Member를 조회한다.
 * 컨트롤러가 클라이언트 요청 바디의 memberId를 신뢰하지 않도록 하기 위한 유일한 경로다.
 */
@Service
@RequiredArgsConstructor
public class CurrentMemberService {

    private final MemberRepository memberRepository;

    public Member getCurrentMember() {
        String phoneNumber = SecurityContextHolder.getContext().getAuthentication().getName();
        return memberRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("인증된 회원을 찾을 수 없습니다. phoneNumber=" + phoneNumber));
    }
}
