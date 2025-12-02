package com.example.attempt.repository;

import com.example.attempt.domain.Attend;
import org.springframework.data.repository.CrudRepository;
import java.util.Optional; // optional 사용으로 자녀가 없거나 첫 출근인 회원인 경우 테이블이 생성되지 않는게 당연하므로 오류가 나지 않게 함.

public interface AttendRepository extends CrudRepository<Attend,Long> {
    Optional<Attend> findByMemberIdAndScheduleId(Long memberId, Long scheduleId);
    boolean existsByMemberIdAndScheduleId(Long memberId, Long scheduleId);
    void deleteByMemberId(Long memberId);
}
