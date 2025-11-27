package com.example.attempt.service;

import com.example.attempt.domain.Guardian;
import com.example.attempt.domain.Member;
import com.example.attempt.repository.GuardianRepository;
import com.example.attempt.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class GuardianService {

    private final GuardianRepository guardianRepository;
    private final MemberRepository memberRepository;

    /** 보호자 등록 */
    public Long create(Long memberId, String name, String phone, boolean receiveNotification) {

        Member member = memberRepository.find(memberId);
        if (member == null) {
            throw new IllegalArgumentException("해당 memberId의 회원이 존재하지 않습니다.");
        }

        Guardian guardian = new Guardian(name, phone, receiveNotification, member);
        guardianRepository.save(guardian);

        return guardian.getId();
    }

    /** 회원 기준 보호자 전체 조회 */
    @Transactional(readOnly = true)
    public List<Guardian> findByMember(Long memberId) {
        return guardianRepository.findByMemberId(memberId);
    }

    /** 단일 보호자 조회 */
    @Transactional(readOnly = true)
    public Guardian findOne(Long id) {
        return guardianRepository.find(id);
    }
}
