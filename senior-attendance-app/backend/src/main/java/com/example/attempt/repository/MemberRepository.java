package com.example.attempt.repository;

import com.example.attempt.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Member 엔티티 Repository
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    /**
     * 이름으로 회원 조회
     */
    List<Member> findByUsername(String username);

    /**
     * 전화번호로 회원 조회
     */
    Optional<Member> findByPhoneNumber(String phoneNumber);

    /**
     * 직번으로 회원 조회
     */
    Optional<Member> findByEmployeeId(Long employeeId);

    /**
     * 현재까지 부여된 최대 직번(없으면 1000)
     */
    @Query("SELECT COALESCE(MAX(m.employeeId), 1000) FROM Member m")
    Long findMaxEmployeeIdOrDefault();

    /**
     * 사업단명으로 회원 조회 (Unit은 Embedded 타입)
     */
    @Query("SELECT m FROM Member m WHERE m.unit.name = :unitName")
    List<Member> findByUnitName(@Param("unitName") String unitName);

    /**
     * 여러 사업단명으로 회원 조회
     */
    @Query("SELECT m FROM Member m WHERE m.unit.name IN :unitNames")
    List<Member> findByUnitNames(@Param("unitNames") List<String> unitNames);

    /**
     * 사업단 타입으로 회원 조회
     */
    @Query("SELECT m FROM Member m WHERE m.unit.type = :unitType")
    List<Member> findByUnitType(@Param("unitType") String unitType);

    /**
     * 본인 일자리로 연결(assignedPlaceId)된 회원 조회
     */
    List<Member> findByAssignedPlaceId(Long assignedPlaceId);
}
