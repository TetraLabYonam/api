package com.example.attempt.repository;

import com.example.attempt.domain.Attend;
import com.example.attempt.domain.AttendStatus;
import com.example.attempt.domain.Member;
import com.example.attempt.domain.Place;
import com.example.attempt.domain.Schedule;
import com.example.attempt.domain.UnitType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AttendRepositoryUnitTypeSummaryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AttendRepository attendRepository;

    @Test
    void getAttendanceStatsByUnitTypeAndDateRange_groupsByUnitTypeAndStatus_withinDateRange() {
        Place publicPlace = new Place("공원안전지킴이", "주소1", 35.3, 129.0);
        publicPlace.setUnitType(UnitType.PUBLIC_INTEREST);
        entityManager.persist(publicPlace);

        Place marketPlace = new Place("동네마당재활용", "주소2", 35.4, 129.1);
        marketPlace.setUnitType(UnitType.MARKET);
        entityManager.persist(marketPlace);

        Member member = Member.withPhoneNumberHash("홍길동", "01011112222");
        entityManager.persist(member);

        Schedule inRangeSchedule = Schedule.builder()
                .title("오전 근무")
                .scheduleDate(LocalDate.of(2026, 7, 10))
                .place(publicPlace)
                .build();
        entityManager.persist(inRangeSchedule);

        Schedule outOfRangeSchedule = Schedule.builder()
                .title("과거 근무")
                .scheduleDate(LocalDate.of(2026, 1, 1))
                .place(publicPlace)
                .build();
        entityManager.persist(outOfRangeSchedule);

        Schedule marketSchedule = Schedule.builder()
                .title("시장 근무")
                .scheduleDate(LocalDate.of(2026, 7, 11))
                .place(marketPlace)
                .build();
        entityManager.persist(marketSchedule);

        entityManager.persist(Attend.builder().member(member).schedule(inRangeSchedule).status(AttendStatus.PRESENT).build());
        entityManager.persist(Attend.builder().member(member).schedule(outOfRangeSchedule).status(AttendStatus.PRESENT).build());
        entityManager.persist(Attend.builder().member(member).schedule(marketSchedule).status(AttendStatus.LATE).build());
        entityManager.flush();

        List<Object[]> result = attendRepository.getAttendanceStatsByUnitTypeAndDateRange(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertEquals(2, result.size());

        boolean hasPublicPresent = result.stream().anyMatch(row ->
                row[0] == UnitType.PUBLIC_INTEREST && row[1] == AttendStatus.PRESENT && ((Long) row[2]) == 1L);
        boolean hasMarketLate = result.stream().anyMatch(row ->
                row[0] == UnitType.MARKET && row[1] == AttendStatus.LATE && ((Long) row[2]) == 1L);

        assertTrue(hasPublicPresent);
        assertTrue(hasMarketLate);
    }
}
