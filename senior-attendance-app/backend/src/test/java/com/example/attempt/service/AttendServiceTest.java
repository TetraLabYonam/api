package com.example.attempt.service;

import com.example.attempt.domain.Attend;
import com.example.attempt.domain.AttendStatus;
import com.example.attempt.domain.Place;
import com.example.attempt.domain.Schedule;
import com.example.attempt.dto.attend.AttendCheckInRequest;
import com.example.attempt.dto.attend.AttendCheckInResponse;
import com.example.attempt.dto.attend.AttendDeclineResponse;
import com.example.attempt.dto.attend.AttendHistoryResponse;
import com.example.attempt.dto.attend.AttendTodayResponse;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.AttendRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    void decline_scheduled_marksAbsentAndReturnsSuccess() {
        Attend attend = Attend.builder().id(10L).status(AttendStatus.SCHEDULED).build();
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.of(attend));

        AttendDeclineResponse response = service.decline(1L, 100L);

        assertTrue(response.isSuccess());
        assertEquals("결석 처리되었습니다.", response.getMessage());
        assertEquals(AttendStatus.ABSENT, response.getStatus());
        assertEquals(AttendStatus.ABSENT, attend.getStatus());
        verify(attendRepository).save(attend);
        verify(smsService).sendAbsenceNotification(eq(attend), any());
    }

    @Test
    void decline_alreadyAttended_returnsUnsuccessfulWithoutChangingState() {
        Attend attend = Attend.builder().id(10L).status(AttendStatus.PRESENT).build();
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.of(attend));

        AttendDeclineResponse response = service.decline(1L, 100L);

        assertFalse(response.isSuccess());
        assertEquals("이미 출석 처리되었습니다.", response.getMessage());
        assertEquals(AttendStatus.PRESENT, response.getStatus());
        verify(attendRepository, never()).save(any());
        verify(smsService, never()).sendAbsenceNotification(any(), any());
    }

    @Test
    void decline_alreadyAbsent_idempotentNoDuplicateSms() {
        Attend attend = Attend.builder().id(10L).status(AttendStatus.ABSENT).build();
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.of(attend));

        AttendDeclineResponse response = service.decline(1L, 100L);

        assertTrue(response.isSuccess());
        assertEquals("이미 결석 처리되었습니다.", response.getMessage());
        assertEquals(AttendStatus.ABSENT, response.getStatus());
        verify(attendRepository, never()).save(any());
        verify(smsService, never()).sendAbsenceNotification(any(), any());
    }

    @Test
    void decline_alreadyExcused_idempotentNoStateChange() {
        Attend attend = Attend.builder().id(10L).status(AttendStatus.EXCUSED).build();
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.of(attend));

        AttendDeclineResponse response = service.decline(1L, 100L);

        assertTrue(response.isSuccess());
        assertEquals(AttendStatus.EXCUSED, response.getStatus());
        verify(attendRepository, never()).save(any());
    }

    @Test
    void decline_noAttendRecord_throwsResourceNotFound() {
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.decline(1L, 100L));
    }

    @Test
    void decline_smsSendFails_stillReturnsSuccess() {
        Attend attend = Attend.builder().id(10L).status(AttendStatus.SCHEDULED).build();
        when(attendRepository.findByScheduleIdAndMemberId(1L, 100L)).thenReturn(Optional.of(attend));
        doThrow(new RuntimeException("SMS 전송 실패")).when(smsService).sendAbsenceNotification(any(), any());

        AttendDeclineResponse response = service.decline(1L, 100L);

        assertTrue(response.isSuccess());
    }

    @Test
    void findTodayAttend_withScheduleToday_returnsIt() {
        Schedule schedule = scheduleStartingAt(LocalTime.now(), placeAt(35.30, 129.00));
        Attend attend = scheduledAttend(schedule);
        when(attendRepository.findByMemberIdAndDateRange(eq(100L), any(), any()))
                .thenReturn(List.of(attend));

        AttendTodayResponse response = service.findTodayAttend(100L);

        assertTrue(response.isHasSchedule());
        assertEquals(schedule.getId(), response.getScheduleId());
        assertEquals("공원안전지킴이", response.getPlaceName());
    }

    @Test
    void findTodayAttend_noScheduleToday_returnsEmpty() {
        when(attendRepository.findByMemberIdAndDateRange(eq(100L), any(), any()))
                .thenReturn(List.of());

        AttendTodayResponse response = service.findTodayAttend(100L);

        assertFalse(response.isHasSchedule());
    }

    @Test
    void getHistory_presentAndLateIncludedAbsentExcluded_calculatesRateCorrectly() {
        List<Object[]> stats = List.of(
                new Object[]{AttendStatus.PRESENT, 3L},
                new Object[]{AttendStatus.LATE, 1L},
                new Object[]{AttendStatus.ABSENT, 4L}
        );
        when(attendRepository.getAttendanceStatsByMemberId(eq(100L), any(), any()))
                .thenReturn(stats);
        when(attendRepository.findByMemberIdAndDateRange(eq(100L), any(), any()))
                .thenReturn(List.of());

        AttendHistoryResponse response = service.getHistory(100L);

        // (PRESENT 3 + LATE 1) / 전체 8 * 100 = 50.0
        assertEquals(50.0, response.getAttendanceRate(), 0.0001);
    }

    @Test
    void getHistory_noRecords_returnsZeroRateAndEmptyList() {
        when(attendRepository.getAttendanceStatsByMemberId(eq(100L), any(), any()))
                .thenReturn(List.of());
        when(attendRepository.findByMemberIdAndDateRange(eq(100L), any(), any()))
                .thenReturn(List.of());

        AttendHistoryResponse response = service.getHistory(100L);

        assertEquals(0.0, response.getAttendanceRate(), 0.0001);
        assertTrue(response.getRecords().isEmpty());
    }

    @Test
    void getHistory_mapsRecordsToScheduleDatePlaceNameAndStatus() {
        Place place = placeAt(35.30, 129.00);
        place.setName("중앙공원");
        Schedule schedule = Schedule.builder()
                .id(1L)
                .title("오전 근무")
                .scheduleDate(LocalDate.of(2026, 7, 15))
                .startTime(LocalTime.of(9, 0))
                .place(place)
                .build();
        Attend attend = Attend.builder()
                .id(10L)
                .schedule(schedule)
                .status(AttendStatus.PRESENT)
                .build();

        when(attendRepository.getAttendanceStatsByMemberId(eq(100L), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{AttendStatus.PRESENT, 1L}));
        when(attendRepository.findByMemberIdAndDateRange(eq(100L), any(), any()))
                .thenReturn(List.of(attend));

        AttendHistoryResponse response = service.getHistory(100L);

        assertEquals(1, response.getRecords().size());
        var item = response.getRecords().get(0);
        assertEquals(LocalDate.of(2026, 7, 15), item.getScheduleDate());
        assertEquals("중앙공원", item.getPlaceName());
        assertEquals(AttendStatus.PRESENT, item.getStatus());
    }
}
