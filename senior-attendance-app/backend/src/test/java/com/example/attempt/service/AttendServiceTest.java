package com.example.attempt.service;

import com.example.attempt.domain.Attend;
import com.example.attempt.domain.AttendStatus;
import com.example.attempt.domain.Place;
import com.example.attempt.domain.Schedule;
import com.example.attempt.dto.attend.AttendCheckInRequest;
import com.example.attempt.dto.attend.AttendCheckInResponse;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.AttendRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AttendServiceTest {

    private static final int RADIUS_METERS = 500;
    private static final int GRACE_MINUTES = 10;

    private AttendRepository attendRepository;
    private SmsService smsService;
    private AttendService service;

    @BeforeEach
    void setup() {
        attendRepository = mock(AttendRepository.class);
        smsService = mock(SmsService.class);
        service = new AttendService(attendRepository, smsService, RADIUS_METERS, GRACE_MINUTES);
    }

    private Place placeAt(double lat, double lon) {
        return new Place("공원안전지킴이", "주소", lat, lon);
    }

    private Schedule scheduleStartingAt(LocalTime startTime, Place place) {
        return Schedule.builder()
                .id(1L)
                .title("오전 근무")
                .startTime(startTime)
                .place(place)
                .build();
    }

    private Attend scheduledAttend(Schedule schedule) {
        return Attend.builder()
                .id(10L)
                .schedule(schedule)
                .status(AttendStatus.SCHEDULED)
                .build();
    }

    private AttendCheckInRequest requestAt(double lat, double lon) {
        return AttendCheckInRequest.builder()
                .scheduleId(1L)
                .memberId(100L)
                .latitude(lat)
                .longitude(lon)
                .build();
    }

    @Test
    void checkIn_onTimeWithinRadius_marksPresent() {
        Schedule schedule = scheduleStartingAt(LocalTime.now().plusHours(1), placeAt(35.30, 129.00));
        Attend attend = scheduledAttend(schedule);
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.of(attend));

        AttendCheckInResponse response = service.checkIn(requestAt(35.3001, 129.0001));

        assertEquals(AttendStatus.PRESENT, response.getStatus());
        assertFalse(response.isLate());
        assertTrue(response.isSuccess());
        assertNotNull(response.getAttendedAt());
        verify(attendRepository).save(attend);
    }

    @Test
    void checkIn_withinGracePeriodAfterStart_stillMarksPresent() {
        Schedule schedule = scheduleStartingAt(LocalTime.now().minusMinutes(5), placeAt(35.30, 129.00));
        Attend attend = scheduledAttend(schedule);
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.of(attend));

        AttendCheckInResponse response = service.checkIn(requestAt(35.3001, 129.0001));

        assertEquals(AttendStatus.PRESENT, response.getStatus());
        assertFalse(response.isLate());
    }

    @Test
    void checkIn_pastGracePeriodAfterStart_marksLate() {
        Schedule schedule = scheduleStartingAt(LocalTime.now().minusMinutes(15), placeAt(35.30, 129.00));
        Attend attend = scheduledAttend(schedule);
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.of(attend));

        AttendCheckInResponse response = service.checkIn(requestAt(35.3001, 129.0001));

        assertEquals(AttendStatus.LATE, response.getStatus());
        assertTrue(response.isLate());
    }

    @Test
    void checkIn_outsideRadius_throwsIllegalStateAndDoesNotSave() {
        Schedule schedule = scheduleStartingAt(LocalTime.now().plusHours(1), placeAt(35.30, 129.00));
        Attend attend = scheduledAttend(schedule);
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.of(attend));

        assertThrows(IllegalStateException.class, () -> service.checkIn(requestAt(35.31, 129.00)));
        verify(attendRepository, never()).save(any());
    }

    @Test
    void checkIn_alreadyAttended_returnsUnsuccessfulWithoutChangingState() {
        Attend attend = Attend.builder()
                .id(10L)
                .status(AttendStatus.PRESENT)
                .attendedAt(LocalDateTime.now().minusHours(1))
                .build();
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.of(attend));

        AttendCheckInResponse response = service.checkIn(requestAt(35.3001, 129.0001));

        assertFalse(response.isSuccess());
        assertEquals("이미 출석 처리되었습니다.", response.getMessage());
        assertEquals(AttendStatus.PRESENT, response.getStatus());
        verify(attendRepository, never()).save(any());
    }

    @Test
    void checkIn_noAttendRecord_throwsResourceNotFound() {
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.checkIn(requestAt(35.3, 129.0)));
    }

    @Test
    void checkIn_smsSendFails_stillReturnsSuccess() {
        Schedule schedule = scheduleStartingAt(LocalTime.now().plusHours(1), placeAt(35.30, 129.00));
        Attend attend = scheduledAttend(schedule);
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.of(attend));
        doThrow(new RuntimeException("SMS 전송 실패")).when(smsService).sendAttendanceNotification(any());

        AttendCheckInResponse response = service.checkIn(requestAt(35.3001, 129.0001));

        assertTrue(response.isSuccess());
    }
}
