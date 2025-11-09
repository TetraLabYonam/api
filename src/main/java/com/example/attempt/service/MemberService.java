package com.example.attempt.service;

import com.example.attempt.domain.Unit;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.domain.Member;
import com.example.attempt.repository.UnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

import static com.example.attempt.service.ExcelService.*;

@Service
@Transactional
public class MemberService {

    private final MemberRepository memberRepository;
    private final UnitRepository unitRepository;

    public MemberService(MemberRepository memberRepository, UnitRepository unitRepository) {
        this.memberRepository = memberRepository;
        this.unitRepository = unitRepository;
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

    @Transactional
    public int saveMembersFromExcel(List<memberExcelData> memberDataList) {
        int savedCount = 0;

        for (memberExcelData data : memberDataList) {
            try {
                // Unit 찾기 또는 생성
                Unit unit = unitRepository.findByName(data.getUnitName())
                        .orElseGet(() -> {
                            Unit newUnit = new Unit(data.getUnitName());
                            return unitRepository.save(newUnit);
                        });

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
