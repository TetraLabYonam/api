package com.example.attempt.repository;

import com.example.attempt.domain.Guardian;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class GuardianRepository {

    private final EntityManager em;

    public void save(Guardian guardian) {
        em.persist(guardian);
    }

    public Guardian find(Long id) {
        return em.find(Guardian.class, id);
    }

    public List<Guardian> findByMemberId(Long memberId) {
        return em.createQuery(
                        "select g from Guardian g where g.member.id = :memberId",
                        Guardian.class
                )
                .setParameter("memberId", memberId)
                .getResultList();
    }
}
