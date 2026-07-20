package com.example.attempt.config;

import com.example.attempt.domain.Attend;
import com.example.attempt.domain.AttendStatus;
import com.example.attempt.domain.Member;
import com.example.attempt.domain.Place;
import com.example.attempt.domain.Schedule;
import com.example.attempt.domain.UnitType;
import com.example.attempt.repository.AttendRepository;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.repository.PlaceRepository;
import com.example.attempt.repository.ScheduleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Playwright e2e 테스트/실사용자 UAT 확인용 고정 시드 데이터.
 * "e2e-seed" 프로파일에서만 동작하며, 운영 환경에서는 활성화되지 않는다.
 */
@Configuration
@Profile("e2e-seed")
public class E2eSeedDataInitializer {

    public static final String PLACE_NAME = "행복 노인 일자리센터";
    public static final String MEMBER_1_NAME = "신경준";
    public static final String MEMBER_2_NAME = "홍길동";
    public static final String MEMBER_3_NAME = "김철수";

    @Bean
    CommandLineRunner seedE2eFixtures(PlaceRepository placeRepository,
                                       MemberRepository memberRepository,
                                       ScheduleRepository scheduleRepository,
                                       AttendRepository attendRepository) {
        return args -> {
            if (placeRepository.findAll().stream().anyMatch(p -> PLACE_NAME.equals(p.getName()))) {
                return;
            }

            Place place = new Place(PLACE_NAME, "서울시 종로구 세종대로 1", 37.572, 126.9769);
            place.setUnitType(UnitType.PUBLIC_INTEREST);
            place = placeRepository.save(place);

            Member m1 = new Member(MEMBER_1_NAME, "01011111111");
            m1.setAssignedPlaceId(place.getId());
            m1 = memberRepository.save(m1);

            Member m2 = new Member(MEMBER_2_NAME, "01022222222");
            m2.setAssignedPlaceId(place.getId());
            m2 = memberRepository.save(m2);

            Member m3 = new Member(MEMBER_3_NAME, "01033333333");
            m3.setAssignedPlaceId(place.getId());
            m3 = memberRepository.save(m3);

            Schedule schedule = Schedule.builder()
                    .title("오전 근무")
                    .scheduleDate(LocalDate.now())
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(13, 0))
                    .place(place)
                    .build();
            schedule = scheduleRepository.save(schedule);

            attendRepository.save(Attend.builder().member(m1).schedule(schedule).status(AttendStatus.PRESENT).build());
            attendRepository.save(Attend.builder().member(m2).schedule(schedule).status(AttendStatus.ABSENT).note("병가").build());
            attendRepository.save(Attend.builder().member(m3).schedule(schedule).status(AttendStatus.SCHEDULED).build());

            System.out.println("[E2eSeedDataInitializer] Seeded e2e fixture data: place=" + place.getId());
        };
    }
}
