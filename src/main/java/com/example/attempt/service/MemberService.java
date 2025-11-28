package com.example.attempt.service;

import com.example.attempt.domain.Unit;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.domain.Member;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

import static com.example.attempt.service.ExcelService.*;

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
        return memberRepository.findById(id).orElse(null);
    }

    @Transactional
    public void update(Long id, String username, String phoneNumber){
        Member member = memberRepository.findById(id).orElse(null);
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

    @Transactional
    public int saveMembersFromExcel(List<memberExcelData> memberDataList) {
        int savedCount = 0;

        for (memberExcelData data : memberDataList) {
            try {
                // Unit 생성 (임베디드 타입)
                Unit unit = new Unit(data.getUnitName());

                // Member 생성 및 저장
                Member member = new Member(data.getMemberName(), data.getPhoneNumber());
                member.setUnit(unit);
                memberRepository.save(member);
                savedCount++;
            } catch (Exception e) {
                // 개별 회원 저장 실패 시 로그를 남기고 계속 진행
                System.err.println("회원 저장 실패: " + data.getMemberName() + " - " + e.getMessage());
            }
        }

        return savedCount;
    }
}
