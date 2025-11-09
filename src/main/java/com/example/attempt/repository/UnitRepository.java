package com.example.attempt.repository;

import com.example.attempt.domain.Unit;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface UnitRepository extends CrudRepository<Unit, Long> {
    Optional<Unit> findByName(String name);
}
