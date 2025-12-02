package com.example.attempt.service;

import com.example.attempt.domain.Attend;
import com.example.attempt.domain.Member;
import com.example.attempt.domain.Schedule;
import com.example.attempt.repository.AttendRepository;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.repository.ScheduleRepository;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Transactional
public class AttendService {

    private final AttendRepository attendRepository;
    private final MemberRepository memberRepository;
    private final ScheduleRepository scheduleRepository;

    public AttendService(AttendRepository attendRepository,
                         MemberRepository memberRepository,
                         ScheduleRepository scheduleRepository) {
        this.attendRepository = attendRepository;
        this.memberRepository = memberRepository;
        this.scheduleRepository = scheduleRepository;
    }

    // 회원 출근 시간 저장
    public Attend clockIn(Long memberId, Long scheduleId, Double latitude, Double longitude) {
        if (attendRepository.existsByMemberIdAndScheduleId(memberId, scheduleId)) {
            throw new IllegalStateException("이미 출근 처리된 회원입니다.");
        }

        Member member = Optional.ofNullable(memberRepository.find(memberId))
                .orElseThrow(() -> new NoSuchElementException("member not found: " + memberId));
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new NoSuchElementException("schedule not found: " + scheduleId));

        Attend attend = new Attend(member, schedule, latitude, longitude);
        attend.setClockInTime(LocalDateTime.now());
        attendRepository.save(attend);
        return attend;
    }

    //회원 퇴근 시간 저장
    public Attend clockOut(Long memberId, Long scheduleId) {
        Attend attend = attendRepository.findByMemberIdAndScheduleId(memberId, scheduleId)
                .orElseThrow(() -> new NoSuchElementException("출근 기록을 찾을 수 없습니다."));

        Hibernate.initialize(attend.getMember());

        if (attend.getClockOutTime() == null) {
            attend.setClockOutTime(LocalDateTime.now());
            attendRepository.save(attend);
        }
        return attend;
    }

    @Transactional(readOnly = true)
    public boolean hasClockedIn(Long memberId, Long scheduleId) {
        return attendRepository.existsByMemberIdAndScheduleId(memberId, scheduleId);
    }
}
