package com.example.queue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class QueueMigrationSeedIntegrationTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("queue.hmac-key", () -> SECRET);
        registry.add("queue.hmac-key-version", () -> "v1");
        registry.add("queue.admin-token", () -> SECRET);
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void flywayV2SeedsExactSeniorJobsAndOpenSessions() {
        List<String> jobs = jdbc.query("SELECT title || ':' || unit_name FROM job_postings ORDER BY id",
                (rs, row) -> rs.getString(1));
        assertThat(jobs).containsExactly("공익형:공익형", "사회서비스형:사회서비스형", "시장형:시장형");
        List<String> sessions = jdbc.query("SELECT session_uid::text FROM ticket_sessions ORDER BY job_id",
                (rs, row) -> rs.getString(1));
        assertThat(sessions).containsExactly(
                "11111111-1111-4111-8111-111111111111",
                "22222222-2222-4222-8222-222222222222",
                "33333333-3333-4333-8333-333333333333");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_sessions WHERE status='OPEN'", Long.class)).isEqualTo(3L);
    }
}
