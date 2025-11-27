package com.example.attempt.repository;

import com.example.attempt.domain.Attend;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class AttendRepository {

    private final EntityManager em;

    public void save(Attend attend) {
        em.persist(attend);
    }

    public Attend find(Long id) {
        return em.find(Attend.class, id);
    }

    /** 특정 멤버가 특정 스케줄에서 이미 출근했는지 확인 */
    public Attend findCheckIn(Long memberId, Long scheduleId) {
        List<Attend> result = em.createQuery(
                        "select a from Attend a " +
                                "where a.member.id = :memberId " +
                                "and a.schedule.id = :scheduleId " +
                                "and a.checkIn = true", Attend.class)
                .setParameter("memberId", memberId)
                .setParameter("scheduleId", scheduleId)
                .getResultList();

        return result.isEmpty() ? null : result.get(0);
    }

    /** 해당 멤버 + 스케줄 기준 최근 Attend 기록 조회 */
    public Attend findLatest(Long memberId, Long scheduleId) {
        List<Attend> result = em.createQuery(
                        "select a from Attend a " +
                                "where a.member.id = :memberId " +
                                "and a.schedule.id = :scheduleId " +
                                "order by a.attendTime desc", Attend.class)
                .setParameter("memberId", memberId)
                .setParameter("scheduleId", scheduleId)
                .setMaxResults(1)
                .getResultList();

        return result.isEmpty() ? null : result.get(0);
    }
}
