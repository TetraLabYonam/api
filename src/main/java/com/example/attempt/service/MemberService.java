package com.example.attempt.service;

import com.example.attempt.domain.Member;
import com.example.attempt.domain.Unit;
import com.example.attempt.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional
    public Long create(String username, String phoneNumber, String unitName) {
        Unit unit = new Unit(unitName);
        Member member = new Member(username, phoneNumber);
        member.setUnit(unit);

        memberRepository.save(member);
        return member.getId();
    }

    public List<Member> findAll() {
        return memberRepository.findAll();
    }

    public Member findOne(Long id) {
        return memberRepository.find(id);
    }

    @Transactional
    public void update(Long id, String username, String phoneNumber) {
        Member member = memberRepository.find(id);
        if (member == null) throw new IllegalArgumentException("존재하지 않는 회원입니다.");

        if (username != null) member.setUsername(username);
        if (phoneNumber != null) member.setPhoneNumber(phoneNumber);
    }

    @Transactional
    public void delete(Long id) {
        memberRepository.deleteById(id);
    }

    @Transactional
    public int saveMembersFromExcel(List<ExcelService.memberExcelData> memberDataList) {
        int savedCount = 0;

        for (ExcelService.memberExcelData data : memberDataList) {
            try {
                Unit unit = new Unit(data.getUnitName());

                Member member = new Member(data.getMemberName(), data.getPhoneNumber());
                member.setUnit(unit);

                memberRepository.save(member);
                savedCount++;
            } catch (Exception e) {
                System.err.println("회원 저장 실패: " + data.getMemberName() + " - " + e.getMessage());
            }
        }
        return savedCount;
    }

}
