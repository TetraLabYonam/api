package com.example.attempt.repository;

import com.example.attempt.domain.Place;
import com.example.attempt.domain.UnitType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    List<Place> findByUnitType(UnitType unitType);

    @Query("""
        SELECT DISTINCT p FROM Place p
        LEFT JOIN JobKeywordSynonym s ON s.place = p
        WHERE p.unitType = :unitType
        AND (
            LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(s.keyword) LIKE LOWER(CONCAT('%', :keyword, '%'))
        )
        """)
    List<Place> searchByUnitTypeAndKeyword(@Param("unitType") UnitType unitType, @Param("keyword") String keyword);
}
