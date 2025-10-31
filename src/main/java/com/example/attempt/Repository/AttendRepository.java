package com.example.attempt.Repository;

import com.example.attempt.domain.Attend;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.stereotype.Repository;

public interface AttendRepository extends CrudRepository<Attend,Long> {
}
