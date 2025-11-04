package com.example.attempt.repository;

import com.example.attempt.domain.Place;
import org.springframework.data.repository.CrudRepository;

public interface PlaceRepository extends CrudRepository<Place, Long> {
}
