package com.example.attempt.service;

import com.example.attempt.domain.Attend;
import com.example.attempt.domain.Member;
import com.example.attempt.domain.Schedule;
import com.example.attempt.repository.AttendRepository;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.repository.ScheduleRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class AttendService {

    private final AttendRepository attendRepository;
    private final MemberRepository memberRepository;
    private final ScheduleRepository scheduleRepository;

    /** 출근 */
    public Attend checkIn(Long memberId, Long scheduleId, Double lat, Double lon) {

        Member member = memberRepository.find(memberId);
        if (member == null) throw new IllegalArgumentException("Member not found");

        Schedule schedule = scheduleRepository.find(scheduleId);
        if (schedule == null) throw new IllegalArgumentException("Schedule not found");

        // 이미 출근 기록이 있는지 확인
        Attend existing = attendRepository.findCheckIn(memberId, scheduleId);
        if (existing != null) {
            throw new IllegalStateException("Already checked in");
        }

        // 출근 기록 생성
        Attend attend = new Attend(
                member,
                schedule,
                true,                   // 출근
                LocalDateTime.now(),
                lat,
                lon
        );

        attendRepository.save(attend);
        return attend;
    }

    /** 퇴근 */
    public Attend checkOut(Long memberId, Long scheduleId, Double lat, Double lon) {

        Member member = memberRepository.find(memberId);
        if (member == null) throw new IllegalArgumentException("Member not found");

        Schedule schedule = scheduleRepository.find(scheduleId);
        if (schedule == null) throw new IllegalArgumentException("Schedule not found");

        // 최근 출근 기록 조회
        Attend latest = attendRepository.findLatest(memberId, scheduleId);
        if (latest == null || !latest.getCheckIn()) {
            throw new IllegalStateException("No check-in record found");
        }

        // 새로운 퇴근 기록 생성
        Attend checkout = new Attend(
                member,
                schedule,
                false,                  // 퇴근
                LocalDateTime.now(),
                lat,
                lon
        );

        attendRepository.save(checkout);
        return checkout;
    }
}
