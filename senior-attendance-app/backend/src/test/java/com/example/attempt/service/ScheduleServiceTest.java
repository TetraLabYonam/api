package com.example.attempt.service;

import com.example.attempt.domain.Admin;
import com.example.attempt.domain.Attend;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ScheduleServiceTest {

    private ScheduleRepository scheduleRepository;
    private PlaceRepository placeRepository;
    private MemberRepository memberRepository;
    private AttendRepository attendRepository;
    private ScheduleService service;

    private final Place place = new Place("중앙공원", "주소", 35.3, 129.0);
    private final Admin admin = new Admin("admin@example.com", "hashed");

    @BeforeEach
    void setup() {
        scheduleRepository = mock(ScheduleRepository.class);
        placeRepository = mock(PlaceRepository.class);
        memberRepository = mock(MemberRepository.class);
        attendRepository = mock(AttendRepository.class);
        service = new ScheduleService(scheduleRepository, placeRepository, memberRepository, attendRepository, 180);

        place.setId(1L);
        when(placeRepository.findById(1L)).thenReturn(Optional.of(place));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private CreateScheduleRequest.CreateScheduleRequestBuilder baseRequest() {
        return CreateScheduleRequest.builder()
                .placeId(1L)
                .title("오전 근무")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(13, 0));
    }

    @Test
    void singleDay_ignoresDaysOfWeek_createsOneScheduleAndAttendsForAssignedMembers() {
        Member m1 = new Member("김할매", "01070001111");
        Member m2 = new Member("이할배", "01070001112");
        when(memberRepository.findByAssignedPlaceId(1L)).thenReturn(List.of(m1, m2));
        when(scheduleRepository.existsByPlaceIdAndScheduleDate(eq(1L), any())).thenReturn(false);

        CreateScheduleRequest request = baseRequest()
                .startDate(LocalDate.of(2026, 7, 13))
                .daysOfWeek(Set.of(DayOfWeek.FRIDAY)) // 무시되어야 함 (단건)
                .build();

        CreateScheduleResponse response = service.createSchedules(request, admin);

        assertEquals(List.of(LocalDate.of(2026, 7, 13)), response.getCreatedDates());
        assertEquals(List.of(), response.getSkippedDates());
        assertEquals(2, response.getAttendCreatedCount());
        verify(attendRepository, times(2)).save(any(Attend.class));
    }

    @Test
    void recurringRange_filtersByDaysOfWeek() {
        when(memberRepository.findByAssignedPlaceId(1L)).thenReturn(List.of(new Member("김할매", "01070001111")));
        when(scheduleRepository.existsByPlaceIdAndScheduleDate(eq(1L), any())).thenReturn(false);

        // 2026-07-06(월) ~ 2026-07-19(일), [월,수] -> 07-06,07-08,07-13,07-15
        CreateScheduleRequest request = baseRequest()
                .startDate(LocalDate.of(2026, 7, 6))
                .endDate(LocalDate.of(2026, 7, 19))
                .daysOfWeek(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
                .build();

        CreateScheduleResponse response = service.createSchedules(request, admin);

        assertEquals(List.of(
                LocalDate.of(2026, 7, 6),
                LocalDate.of(2026, 7, 8),
                LocalDate.of(2026, 7, 13),
                LocalDate.of(2026, 7, 15)
        ), response.getCreatedDates());
        assertEquals(4, response.getAttendCreatedCount());
    }

    @Test
    void duplicateDate_isSkipped_noAttendCreated() {
        when(memberRepository.findByAssignedPlaceId(1L)).thenReturn(List.of(new Member("김할매", "01070001111")));
        when(scheduleRepository.existsByPlaceIdAndScheduleDate(1L, LocalDate.of(2026, 7, 13))).thenReturn(true);
        when(scheduleRepository.existsByPlaceIdAndScheduleDate(1L, LocalDate.of(2026, 7, 6))).thenReturn(false);
        when(scheduleRepository.existsByPlaceIdAndScheduleDate(1L, LocalDate.of(2026, 7, 8))).thenReturn(false);
        when(scheduleRepository.existsByPlaceIdAndScheduleDate(1L, LocalDate.of(2026, 7, 15))).thenReturn(false);

        CreateScheduleRequest request = baseRequest()
                .startDate(LocalDate.of(2026, 7, 6))
                .endDate(LocalDate.of(2026, 7, 19))
                .daysOfWeek(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
                .build();

        CreateScheduleResponse response = service.createSchedules(request, admin);

        assertEquals(List.of(
                LocalDate.of(2026, 7, 6),
                LocalDate.of(2026, 7, 8),
                LocalDate.of(2026, 7, 15)
        ), response.getCreatedDates());
        assertEquals(List.of(LocalDate.of(2026, 7, 13)), response.getSkippedDates());
        assertEquals(3, response.getAttendCreatedCount());
    }

    @Test
    void zeroAssignedMembers_scheduleCreatedButZeroAttend() {
        when(memberRepository.findByAssignedPlaceId(1L)).thenReturn(List.of());
        when(scheduleRepository.existsByPlaceIdAndScheduleDate(eq(1L), any())).thenReturn(false);

        CreateScheduleRequest request = baseRequest().startDate(LocalDate.of(2026, 7, 13)).build();

        CreateScheduleResponse response = service.createSchedules(request, admin);

        assertEquals(List.of(LocalDate.of(2026, 7, 13)), response.getCreatedDates());
        assertEquals(0, response.getAttendCreatedCount());
        verify(scheduleRepository, times(1)).save(any(Schedule.class));
        verify(attendRepository, never()).save(any(Attend.class));
    }

    @Test
    void startDateAfterEndDate_throwsIllegalArgumentException() {
        CreateScheduleRequest request = baseRequest()
                .startDate(LocalDate.of(2026, 7, 15))
                .endDate(LocalDate.of(2026, 7, 13))
                .build();

        assertThrows(IllegalArgumentException.class, () -> service.createSchedules(request, admin));
    }

    @Test
    void startTimeNotBeforeEndTime_throwsIllegalArgumentException() {
        CreateScheduleRequest request = CreateScheduleRequest.builder()
                .placeId(1L).title("오전 근무")
                .startDate(LocalDate.of(2026, 7, 13))
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(9, 0))
                .build();

        assertThrows(IllegalArgumentException.class, () -> service.createSchedules(request, admin));
    }

    @Test
    void startTimeEqualsEndTime_throwsIllegalArgumentException() {
        CreateScheduleRequest request = baseRequest()
                .startDate(LocalDate.of(2026, 7, 13))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(9, 0))
                .build();

        assertThrows(IllegalArgumentException.class, () -> service.createSchedules(request, admin));
    }

    @Test
    void multiDayWithoutDaysOfWeek_throwsIllegalArgumentException() {
        CreateScheduleRequest request = baseRequest()
                .startDate(LocalDate.of(2026, 7, 6))
                .endDate(LocalDate.of(2026, 7, 19))
                .build();

        assertThrows(IllegalArgumentException.class, () -> service.createSchedules(request, admin));
    }

    @Test
    void rangeExceedsMaxRangeDays_throwsIllegalArgumentException() {
        ScheduleService strictService = new ScheduleService(
                scheduleRepository, placeRepository, memberRepository, attendRepository, 10);

        CreateScheduleRequest request = baseRequest()
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 7, 31))
                .daysOfWeek(Set.of(DayOfWeek.MONDAY))
                .build();

        assertThrows(IllegalArgumentException.class, () -> strictService.createSchedules(request, admin));
    }

    @Test
    void placeNotFound_throwsResourceNotFoundException() {
        when(placeRepository.findById(999L)).thenReturn(Optional.empty());
        CreateScheduleRequest request = CreateScheduleRequest.builder()
                .placeId(999L).title("오전 근무")
                .startDate(LocalDate.of(2026, 7, 13))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(13, 0))
                .build();

        assertThrows(ResourceNotFoundException.class, () -> service.createSchedules(request, admin));
    }
}
