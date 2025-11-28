package com.example.attempt.service;

import com.example.attempt.domain.*;
import com.example.attempt.dto.schedule.*;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 일정 관리 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final AttendRepository attendRepository;
    private final MemberRepository memberRepository;
    private final PlaceRepository placeRepository;

    /**
     * 일정 생성 (여러 날짜, 여러 참석자)
     */
    public ScheduleCreateResponse createSchedules(ScheduleCreateRequest request) {
        // 1. 유효성 검증
        request.validate();
        log.info("일정 생성 요청: {}", request.getTitle());

        // 2. Place 조회
        Place place = placeRepository.findById(request.getPlaceId())
                .orElseThrow(() -> new ResourceNotFoundException("장소를 찾을 수 없습니다. ID: " + request.getPlaceId()));

        // 3. 참석 대상 회원 조회
        List<Member> targetMembers = getTargetMembers(request);

        if (targetMembers.isEmpty()) {
            throw new IllegalStateException("참석 대상 회원이 없습니다.");
        }

        log.info("참석 대상 회원 수: {}", targetMembers.size());

        // 4. 날짜별로 Schedule 생성
        List<Schedule> createdSchedules = new ArrayList<>();

        for (LocalDate date : request.getDates()) {
            Schedule schedule = Schedule.builder()
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .scheduleDate(date)
                    .startTime(request.getStartTime())
                    .endTime(request.getEndTime())
                    .place(place)
                    .isActive(true)
                    .build();

            // 5. 각 회원에 대해 Attend 생성 (상태: SCHEDULED)
            for (Member member : targetMembers) {
                Attend attend = Attend.builder()
                        .member(member)
                        .schedule(schedule)
                        .status(AttendStatus.SCHEDULED)
                        .build();
                schedule.addAttend(attend);
            }

            Schedule saved = scheduleRepository.save(schedule);
            createdSchedules.add(saved);

            log.info("일정 생성 완료: {} (날짜: {}, 참석자: {}명)",
                    saved.getTitle(), saved.getScheduleDate(), saved.getAttends().size());
        }

        // 6. Response 생성
        return buildCreateResponse(createdSchedules);
    }

    /**
     * 참석 대상 회원 조회
     */
    private List<Member> getTargetMembers(ScheduleCreateRequest request) {
        // 전체 회원
        if (request.isAllMembersSelected()) {
            log.info("전체 회원 선택");
            return memberRepository.findAll();
        }

        Set<Member> members = new HashSet<>();

        // 특정 회원 ID
        if (request.hasMemberIds()) {
            List<Member> foundMembers = memberRepository.findAllById(request.getMemberIds());
            members.addAll(foundMembers);
            log.info("특정 회원 {}명 선택", foundMembers.size());
        }

        // 특정 사업단
        if (request.hasUnitNames()) {
            for (String unitName : request.getUnitNames()) {
                List<Member> unitMembers = memberRepository.findByUnitName(unitName);
                members.addAll(unitMembers);
                log.info("사업단 '{}' 회원 {}명 선택", unitName, unitMembers.size());
            }
        }

        return new ArrayList<>(members);
    }

    /**
     * 생성 응답 빌드
     */
    private ScheduleCreateResponse buildCreateResponse(List<Schedule> schedules) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        List<ScheduleCreateResponse.ScheduleSummary> summaries = schedules.stream()
                .map(s -> ScheduleCreateResponse.ScheduleSummary.builder()
                        .scheduleId(s.getId())
                        .title(s.getTitle())
                        .scheduleDate(s.getScheduleDate())
                        .startTime(s.getStartTime())
                        .endTime(s.getEndTime())
                        .placeName(s.getPlace().getName())
                        .attendeeCount(s.getAttends().size())
                        .createdAt(s.getCreatedAt().format(formatter))
                        .build())
                .collect(Collectors.toList());

        return ScheduleCreateResponse.builder()
                .schedules(summaries)
                .totalSchedulesCreated(schedules.size())
                .totalAttendeesPerSchedule(schedules.isEmpty() ? 0 : schedules.get(0).getAttends().size())
                .message(schedules.size() + "개의 일정이 성공적으로 생성되었습니다.")
                .build();
    }

    /**
     * 일정 상세 조회
     */
    @Transactional(readOnly = true)
    public ScheduleDetailResponse getScheduleDetail(Long scheduleId) {
        Schedule schedule = scheduleRepository.findByIdWithAttends(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("일정을 찾을 수 없습니다. ID: " + scheduleId));

        return buildDetailResponse(schedule);
    }

    /**
     * 회원별 일정 조회
     */
    @Transactional(readOnly = true)
    public List<ScheduleDetailResponse> getMemberSchedules(Long memberId, LocalDate startDate, LocalDate endDate) {
        // 회원 존재 확인
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다. ID: " + memberId));

        // 회원의 일정 조회
        List<Schedule> schedules = scheduleRepository.findByMemberIdAndDateRange(memberId, startDate, endDate);

        return schedules.stream()
                .map(this::buildDetailResponse)
                .collect(Collectors.toList());
    }

    /**
     * 날짜별 일정 조회
     */
    @Transactional(readOnly = true)
    public List<ScheduleDetailResponse> getSchedulesByDate(LocalDate date) {
        List<Schedule> schedules = scheduleRepository.findByScheduleDate(date);

        return schedules.stream()
                .map(this::buildDetailResponse)
                .collect(Collectors.toList());
    }

    /**
     * 날짜 범위 일정 조회
     */
    @Transactional(readOnly = true)
    public List<ScheduleDetailResponse> getSchedulesByDateRange(LocalDate startDate, LocalDate endDate) {
        List<Schedule> schedules = scheduleRepository.findActiveSchedulesByDateRange(startDate, endDate);

        return schedules.stream()
                .map(this::buildDetailResponse)
                .collect(Collectors.toList());
    }

    /**
     * 상세 응답 빌드
     */
    private ScheduleDetailResponse buildDetailResponse(Schedule schedule) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // 장소 정보
        ScheduleDetailResponse.PlaceInfo placeInfo = ScheduleDetailResponse.PlaceInfo.builder()
                .placeId(schedule.getPlace().getId())
                .name(schedule.getPlace().getName())
                .address(schedule.getPlace().getAddress())
                .latitude(schedule.getPlace().getLatitude())
                .longitude(schedule.getPlace().getLongitude())
                .build();

        // 출석 통계
        ScheduleDetailResponse.AttendanceStats stats = ScheduleDetailResponse.AttendanceStats.builder()
                .totalAttendees(schedule.getTotalAttendees())
                .presentCount(schedule.getPresentCount())
                .absentCount(schedule.getAbsentCount())
                .lateCount(schedule.getLateCount())
                .scheduledCount(schedule.getScheduledCount())
                .excusedCount(schedule.getAttends().stream()
                        .filter(a -> a.getStatus() == AttendStatus.EXCUSED)
                        .count())
                .attendanceRate(schedule.getAttendanceRate())
                .build();

        // 참석자 목록
        List<ScheduleDetailResponse.AttendeeInfo> attendees = schedule.getAttends().stream()
                .map(attend -> ScheduleDetailResponse.AttendeeInfo.builder()
                        .memberId(attend.getMember().getId())
                        .memberName(attend.getMember().getUsername())
                        .phoneNumber(attend.getMember().getPhoneNumber())
                        .unitName(attend.getMember().getUnit() != null ?
                                attend.getMember().getUnit().getName() : null)
                        .status(attend.getStatus())
                        .attendedAt(attend.getAttendedAt() != null ?
                                attend.getAttendedAt().format(formatter) : null)
                        .note(attend.getNote())
                        .build())
                .collect(Collectors.toList());

        return ScheduleDetailResponse.builder()
                .scheduleId(schedule.getId())
                .title(schedule.getTitle())
                .description(schedule.getDescription())
                .scheduleDate(schedule.getScheduleDate())
                .startTime(schedule.getStartTime())
                .endTime(schedule.getEndTime())
                .place(placeInfo)
                .stats(stats)
                .attendees(attendees)
                .isActive(schedule.getIsActive())
                .createdAt(schedule.getCreatedAt().format(formatter))
                .updatedAt(schedule.getUpdatedAt().format(formatter))
                .build();
    }

    /**
     * 일정 비활성화
     */
    public void deactivateSchedule(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("일정을 찾을 수 없습니다. ID: " + scheduleId));

        schedule.setIsActive(false);
        scheduleRepository.save(schedule);

        log.info("일정 비활성화: {} (ID: {})", schedule.getTitle(), scheduleId);
    }

    /**
     * 일정 활성화
     */
    public void activateSchedule(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("일정을 찾을 수 없습니다. ID: " + scheduleId));

        schedule.setIsActive(true);
        scheduleRepository.save(schedule);

        log.info("일정 활성화: {} (ID: {})", schedule.getTitle(), scheduleId);
    }
}
