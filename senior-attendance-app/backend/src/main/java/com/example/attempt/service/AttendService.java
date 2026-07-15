package com.example.attempt.service;

import com.example.attempt.domain.Attend;
import com.example.attempt.domain.AttendStatus;
import com.example.attempt.domain.Place;
import com.example.attempt.domain.Schedule;
import com.example.attempt.dto.attend.AttendCheckInRequest;
import com.example.attempt.dto.attend.AttendCheckInResponse;
import com.example.attempt.dto.attend.AttendTodayResponse;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.AttendRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * 출석 관리 서비스
 */
@Service
@Transactional
@Slf4j
public class AttendService {

    private final AttendRepository attendRepository;
    private final SmsService smsService;
    private final int locationRadius;
    private final int lateGraceMinutes;

    public AttendService(AttendRepository attendRepository,
                          SmsService smsService,
                          @Value("${attendance.location.radius:100}") int locationRadius,
                          @Value("${attendance.late.grace-minutes:10}") int lateGraceMinutes) {
        this.attendRepository = attendRepository;
        this.smsService = smsService;
        this.locationRadius = locationRadius;
        this.lateGraceMinutes = lateGraceMinutes;
    }

    /**
     * 출석 체크인
     */
    public AttendCheckInResponse checkIn(AttendCheckInRequest request) {
        log.info("출석 체크인 시도: scheduleId={}, memberId={}", request.getScheduleId(), request.getMemberId());

        // 1. Attend 조회
        Attend attend = attendRepository.findByScheduleIdAndMemberId(
                        request.getScheduleId(), request.getMemberId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "해당 일정의 출석 정보를 찾을 수 없습니다. scheduleId=" + request.getScheduleId() +
                        ", memberId=" + request.getMemberId()));

        // 2. 이미 출석 완료한 경우
        if (attend.isAttended()) {
            log.warn("이미 출석 처리된 기록: attendId={}, status={}", attend.getId(), attend.getStatus());
            return AttendCheckInResponse.builder()
                    .attendId(attend.getId())
                    .status(attend.getStatus())
                    .attendedAt(attend.getAttendedAt())
                    .message("이미 출석 처리되었습니다.")
                    .isLate(attend.getStatus() == AttendStatus.LATE)
                    .success(false)
                    .build();
        }

        // 3. 위치 검증
        Schedule schedule = attend.getSchedule();
        Place place = schedule.getPlace();

        double distance = calculateDistance(
                request.getLatitude(), request.getLongitude(),
                place.getLatitude(), place.getLongitude()
        );

        log.info("출석 위치 거리: {}m (허용 반경: {}m)", distance, locationRadius);

        if (!isValidLocation(distance)) {
            log.warn("출석 가능한 위치가 아님: 거리={}m, 허용반경={}m", distance, locationRadius);
            throw new IllegalStateException(
                    String.format("출석 가능한 위치가 아닙니다. (거리: %.1fm, 허용 반경: %dm)",
                            distance, locationRadius));
        }

        // 4. 지각 여부 판단
        boolean isLate = isLate(schedule);

        // 5. 출석 처리
        if (isLate) {
            attend.markLate(request.getLatitude(), request.getLongitude(), "지각");
            log.info("지각 처리 완료: attendId={}", attend.getId());
        } else {
            attend.markPresent(request.getLatitude(), request.getLongitude());
            log.info("출석 처리 완료: attendId={}", attend.getId());
        }

        attendRepository.save(attend);

        // 6. SMS 알림 전송
        try {
            smsService.sendAttendanceNotification(attend);
        } catch (Exception e) {
            log.error("SMS 전송 실패: attendId={}", attend.getId(), e);
            // SMS 전송 실패는 출석 처리에 영향을 주지 않음
        }

        return AttendCheckInResponse.builder()
                .attendId(attend.getId())
                .status(attend.getStatus())
                .attendedAt(attend.getAttendedAt())
                .message(isLate ? "출석 처리되었습니다 (지각)" : "출석 처리되었습니다.")
                .isLate(isLate)
                .locationInfo(String.format("위치 확인 완료 (거리: %.1fm)", distance))
                .distance(distance)
                .success(true)
                .build();
    }

    /**
     * 로그인한 회원의 오늘 Attend를 조회한다. 하루 여러 건이면 첫 건만 사용한다(통상 1건).
     * 트랜잭션이 열려 있는 동안 지연 로딩되는 schedule/place까지 매핑하여 반환한다.
     */
    public AttendTodayResponse findTodayAttend(Long memberId) {
        LocalDate today = LocalDate.now();
        List<Attend> attends = attendRepository.findByMemberIdAndDateRange(memberId, today, today);
        Optional<Attend> attend = attends.stream().findFirst();
        return attend.map(AttendTodayResponse::of).orElseGet(AttendTodayResponse::none);
    }

    /**
     * 결석 처리
     */
    public void markAbsent(Long attendId, String reason) {
        Attend attend = attendRepository.findById(attendId)
                .orElseThrow(() -> new ResourceNotFoundException("출석 정보를 찾을 수 없습니다. ID: " + attendId));

        attend.markAbsent(reason);
        attendRepository.save(attend);

        // SMS 알림 전송
        try {
            smsService.sendAbsenceNotification(attend, reason);
        } catch (Exception e) {
            log.error("결석 SMS 전송 실패: attendId={}", attendId, e);
        }

        log.info("결석 처리 완료: attendId={}, reason={}", attendId, reason);
    }

    /**
     * 사유 인정 결석 처리
     */
    public void markExcused(Long attendId, String reason) {
        Attend attend = attendRepository.findById(attendId)
                .orElseThrow(() -> new ResourceNotFoundException("출석 정보를 찾을 수 없습니다. ID: " + attendId));

        attend.markExcused(reason);
        attendRepository.save(attend);

        // SMS 알림 전송
        try {
            smsService.sendAbsenceNotification(attend, reason);
        } catch (Exception e) {
            log.error("사유 인정 결석 SMS 전송 실패: attendId={}", attendId, e);
        }

        log.info("사유 인정 결석 처리 완료: attendId={}, reason={}", attendId, reason);
    }

    /**
     * 위치 검증 (거리 기반)
     */
    private boolean isValidLocation(double distance) {
        return distance <= locationRadius;
    }

    /**
     * 두 지점 간 거리 계산 (Haversine 공식)
     * @return 거리 (미터)
     */
    private double calculateDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            throw new IllegalArgumentException("위도/경도 정보가 올바르지 않습니다.");
        }

        final int EARTH_RADIUS = 6371000; // 지구 반지름 (미터)

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c; // 미터 단위 거리
    }

    /**
     * 지각 여부 판단 — 시작 시간 + 유예시간(lateGraceMinutes)까지는 정시로 인정한다
     */
    private boolean isLate(Schedule schedule) {
        if (schedule.getStartTime() == null) {
            return false; // 시작 시간이 없으면 지각 아님
        }

        LocalTime now = LocalTime.now();
        LocalTime lateThreshold = schedule.getStartTime().plusMinutes(lateGraceMinutes);

        return now.isAfter(lateThreshold);
    }

    /**
     * 출석 상태 변경
     */
    public void updateAttendStatus(Long attendId, AttendStatus status, String note) {
        Attend attend = attendRepository.findById(attendId)
                .orElseThrow(() -> new ResourceNotFoundException("출석 정보를 찾을 수 없습니다. ID: " + attendId));

        attend.setStatus(status);
        if (note != null && !note.isBlank()) {
            attend.setNote(note);
        }

        attendRepository.save(attend);

        log.info("출석 상태 변경 완료: attendId={}, status={}", attendId, status);
    }
}
