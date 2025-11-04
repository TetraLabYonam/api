package com.example.attempt.service;

import com.example.attempt.repository.MemberRepository;
import com.example.attempt.domain.Member;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional
    public Long join(Member member){
        memberRepository.save(member);
        return member.getId();
    }

    public List<Member> findMembers(){
        return memberRepository.findAll();
    }

    public Member findOne(Long id) {
        return memberRepository.find(id);
    }

    @Transactional
    public void update(Long id, String username, String phoneNumber){
        Member member = memberRepository.find(id);
        if (member != null) {
            if (username != null) {
                member.setUsername(username);
            }
            if (phoneNumber != null) {
                member.setPhoneNumber(phoneNumber);
            }
        }
    }

    @Transactional
    public void delete(Long id) {
        memberRepository.deleteById(id);
    }
}
