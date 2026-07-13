package com.example.attempt.repository;

import com.example.attempt.domain.JobKeywordSynonym;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobKeywordSynonymRepository extends JpaRepository<JobKeywordSynonym, Long> {
    List<JobKeywordSynonym> findByPlaceId(Long placeId);
}
