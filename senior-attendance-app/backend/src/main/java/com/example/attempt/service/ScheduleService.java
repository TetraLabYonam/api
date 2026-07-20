package com.example.attempt.service;

import com.example.attempt.domain.Admin;
import com.example.attempt.domain.Attend;
import com.example.attempt.domain.AttendStatus;
import com.example.attempt.domain.Member;
import com.example.attempt.domain.Place;
import com.example.attempt.domain.Schedule;
import com.example.attempt.dto.schedule.CreateScheduleRequest;
import com.example.attempt.dto.schedule.CreateScheduleResponse;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.AttendRepository;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.repository.PlaceRepository;
import com.example.attempt.repository.ScheduleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 일정(Schedule) 생성 + 배정 회원 전원에 대한 Attend(SCHEDULED) 자동 생성 서비스
 */
@Service
@Transactional
@Slf4j
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final PlaceRepository placeRepository;
    private final MemberRepository memberRepository;
    private final AttendRepository attendRepository;
    private final long maxRangeDays;

    public ScheduleService(ScheduleRepository scheduleRepository,
                            PlaceRepository placeRepository,
                            MemberRepository memberRepository,
                            AttendRepository attendRepository,
                            @Value("${schedule.max-range-days:180}") long maxRangeDays) {
        this.scheduleRepository = scheduleRepository;
        this.placeRepository = placeRepository;
        this.memberRepository = memberRepository;
        this.attendRepository = attendRepository;
        this.maxRangeDays = maxRangeDays;
    }

    public CreateScheduleResponse createSchedules(CreateScheduleRequest request, Admin createdBy) {
        Place place = placeRepository.findById(request.getPlaceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "장소를 찾을 수 없습니다. ID: " + request.getPlaceId()));

        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate() != null ? request.getEndDate() : startDate;

        validate(request, startDate, endDate);

        List<LocalDate> targetDates = resolveTargetDates(startDate, endDate, request.getDaysOfWeek());
        List<Member> members = memberRepository.findByAssignedPlaceId(place.getId());

        List<LocalDate> createdDates = new ArrayList<>();
        List<LocalDate> skippedDates = new ArrayList<>();
        int attendCreatedCount = 0;

        for (LocalDate date : targetDates) {
            if (scheduleRepository.existsByPlaceIdAndScheduleDate(place.getId(), date)) {
                skippedDates.add(date);
                continue;
            }

            Schedule schedule = scheduleRepository.save(Schedule.builder()
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .scheduleDate(date)
                    .startTime(request.getStartTime())
                    .endTime(request.getEndTime())
                    .place(place)
                    .createdBy(createdBy)
                    .build());

            for (Member member : members) {
                attendRepository.save(Attend.builder()
                        .member(member)
                        .schedule(schedule)
                        .status(AttendStatus.SCHEDULED)
                        .build());
            }

            createdDates.add(date);
            attendCreatedCount += members.size();
        }

        log.info("일정 생성 완료: placeId={}, 생성={}건, 스킵={}건, Attend={}건",
                place.getId(), createdDates.size(), skippedDates.size(), attendCreatedCount);

        return CreateScheduleResponse.builder()
                .createdDates(createdDates)
                .skippedDates(skippedDates)
                .attendCreatedCount(attendCreatedCount)
                .build();
    }

    private void validate(CreateScheduleRequest request, LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작 날짜는 종료 날짜보다 이후일 수 없습니다.");
        }
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new IllegalArgumentException("시작 시간은 종료 시간보다 이전이어야 합니다.");
        }
        boolean isMultiDay = !startDate.isEqual(endDate);
        if (isMultiDay && (request.getDaysOfWeek() == null || request.getDaysOfWeek().isEmpty())) {
            throw new IllegalArgumentException("시작일과 종료일이 다르면 daysOfWeek를 지정해야 합니다.");
        }
        long rangeDays = ChronoUnit.DAYS.between(startDate, endDate);
        if (rangeDays > maxRangeDays) {
            throw new IllegalArgumentException(
                    "생성 기간이 최대 허용치(" + maxRangeDays + "일)를 초과했습니다.");
        }
    }

    private List<LocalDate> resolveTargetDates(LocalDate startDate, LocalDate endDate, Set<DayOfWeek> daysOfWeek) {
        if (startDate.isEqual(endDate)) {
            return List.of(startDate);
        }

        List<LocalDate> dates = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            if (daysOfWeek.contains(current.getDayOfWeek())) {
                dates.add(current);
            }
            current = current.plusDays(1);
        }
        return dates;
    }
}
