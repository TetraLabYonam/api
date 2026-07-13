package com.example.attempt.repository;

import com.example.attempt.domain.JobKeywordSynonym;
import com.example.attempt.domain.Place;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class JobKeywordSynonymRepositoryTest {

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private JobKeywordSynonymRepository synonymRepository;

    @Test
    void findByPlaceId_returnsSynonymsForThatPlaceOnly() {
        Place park = placeRepository.save(new Place("공원안전지킴이", "주소1", 35.3, 129.0));
        Place recycle = placeRepository.save(new Place("동네마당재활용", "주소2", 35.4, 129.1));

        synonymRepository.save(new JobKeywordSynonym(park, "청소"));
        synonymRepository.save(new JobKeywordSynonym(park, "쓰레기 줍기"));
        synonymRepository.save(new JobKeywordSynonym(recycle, "재활용"));

        List<JobKeywordSynonym> result = synonymRepository.findByPlaceId(park.getId());

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(s -> s.getKeyword().equals("청소")));
    }
}
