package com.example.attempt.domain;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class PlaceMemberSchemaTest {

    @Autowired
    private jakarta.persistence.EntityManager em;

    @Test
    void place_persistsAndReloadsUnitType() {
        Place place = new Place("공원안전지킴이", "경남 양산시 어딘가", 35.33, 129.03);
        place.setUnitType(UnitType.PUBLIC_INTEREST);
        em.persist(place);
        em.flush();
        em.clear();

        Place reloaded = em.find(Place.class, place.getId());
        assertEquals(UnitType.PUBLIC_INTEREST, reloaded.getUnitType());
    }

    @Test
    void member_persistsAndReloadsConsentAndAssignedPlace() {
        Place place = new Place("공원안전지킴이", "경남 양산시 어딘가", 35.33, 129.03);
        em.persist(place);

        Member member = Member.withPhoneNumberHash("김할매", "01000000000");
        member.setAssignedPlaceId(place.getId());
        LocalDateTime consentAt = LocalDateTime.now().withNano(0);
        member.setLocationConsentAgreedAt(consentAt);
        em.persist(member);
        em.flush();
        em.clear();

        Member reloaded = em.find(Member.class, member.getId());
        assertEquals(place.getId(), reloaded.getAssignedPlaceId());
        assertEquals(consentAt, reloaded.getLocationConsentAgreedAt());
    }
}
