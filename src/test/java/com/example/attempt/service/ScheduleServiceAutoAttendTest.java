package com.example.attempt.service;

import com.example.attempt.domain.Member;
import com.example.attempt.domain.Place;
import com.example.attempt.dto.schedule.ScheduleCreateRequest;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.repository.PlaceRepository;
import com.example.attempt.repository.ScheduleRepository;
import com.example.attempt.domain.Schedule;
import com.example.attempt.repository.AttendRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ScheduleServiceAutoAttendTest {

    private ScheduleRepository scheduleRepository;
    private AttendRepository attendRepository;
    private MemberRepository memberRepository;
    private PlaceRepository placeRepository;
    private ScheduleService service;

    @BeforeEach
    void setup() {
        scheduleRepository = mock(ScheduleRepository.class);
        attendRepository = mock(AttendRepository.class);
        memberRepository = mock(MemberRepository.class);
        placeRepository = mock(PlaceRepository.class);
        service = new ScheduleService(scheduleRepository, attendRepository, memberRepository, placeRepository);

        // NOTE: real persistence populates Schedule.createdAt via @CreationTimestamp at flush time;
        // since save() is mocked here (no real Hibernate session), we set it manually so that
        // ScheduleService.buildCreateResponse()'s createdAt.format(...) call doesn't NPE.
        when(scheduleRepository.save(any())).thenAnswer(inv -> {
            Schedule schedule = inv.getArgument(0);
            if (schedule.getCreatedAt() == null) {
                schedule.setCreatedAt(LocalDateTime.now());
            }
            return schedule;
        });
    }

    @Test
    void createSchedules_includesMembersAssignedToPlace_evenWithoutExplicitSelection() {
        Place place = new Place("공원안전지킴이", "주소", 35.3, 129.0);
        place.setId(1L);
        when(placeRepository.findById(1L)).thenReturn(Optional.of(place));

        Member selfAssigned = new Member("김할매", "01000000000");
        selfAssigned.setId(10L);
        selfAssigned.setAssignedPlaceId(1L);
        when(memberRepository.findByAssignedPlaceId(1L)).thenReturn(List.of(selfAssigned));

        Member explicit = new Member("박할배", "01011111111");
        explicit.setId(20L);
        when(memberRepository.findAllById(List.of(20L))).thenReturn(List.of(explicit));

        ScheduleCreateRequest request = ScheduleCreateRequest.builder()
                .title("환경정비")
                .dates(List.of(LocalDate.now()))
                .placeId(1L)
                .memberIds(List.of(20L))
                .build();

        service.createSchedules(request);

        verify(scheduleRepository, times(1)).save(argThat(schedule ->
                schedule.getAttends().size() == 2 &&
                schedule.getAttends().stream().anyMatch(a -> a.getMember().getId().equals(10L)) &&
                schedule.getAttends().stream().anyMatch(a -> a.getMember().getId().equals(20L))
        ));
    }
}
