package com.example.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class QueueObservabilityIntegrationTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Pattern LABELS = Pattern.compile("queue_ticket_requests(?:\\{([^}]*)})?");

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
    @Autowired MockMvc mvc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM draw_records");
        jdbc.update("DELETE FROM ticket_sessions");
        jdbc.update("DELETE FROM job_postings");
        jdbc.update("UPDATE global_ticket_counter SET current_value = 0 WHERE singleton = TRUE");
    }

    @Test
    void readinessProbeIsAvailable() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
    }

    @Test
    void ticketRequestChangesOnlyExpectedPrometheusCounterAndLabelsContainNoIdentifiers() throws Exception {
        String before = mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        long job = jdbc.queryForObject("INSERT INTO job_postings(title, unit_name) VALUES ('safe-title', 'safe-unit') RETURNING id", Long.class);
        UUID session = UUID.randomUUID();
        jdbc.update("INSERT INTO ticket_sessions(session_uid, job_id) VALUES (?, ?)", session, job);
        mvc.perform(post("/api/v1/jobs/{jobId}/ticket-sessions/{sessionUid}/tickets", job, session)
                        .contentType("application/json").content("{\"phone\":\"+82105550123\"}"))
                .andExpect(status().isOk());

        String after = mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String metric = after.lines().filter(line -> line.startsWith("queue_ticket_requests_total")).findFirst().orElseThrow();
        String labels = LABELS.matcher(metric).find() ? LABELS.matcher(metric).results().findFirst().map(m -> m.group(1)).orElse("") : "";
        assertThat(metric).contains("outcome=\"issued\"");
        assertThat(labels).doesNotContain("job", "session", "phone", "title", "unit");
        assertThat(after).contains("queue_ticket_requests_total");
        assertThat(after).doesNotContain("+82105550123", session.toString());
        assertThat(before).doesNotContain("+82105550123", session.toString());
    }
    private double metricValue(String prometheus, String outcome) {
        return prometheus.lines()
                .filter(line -> line.startsWith("queue_ticket_requests_total{"))
                .filter(line -> line.contains("outcome=\"" + outcome + "\""))
                .map(line -> line.substring(line.lastIndexOf(' ') + 1))
                .mapToDouble(Double::parseDouble)
                .findFirst().orElse(0);
    }

    @Test
    void metricDeltaIsIsolatedToIssuedOutcome() throws Exception {
        String before = mvc.perform(get("/actuator/prometheus")).andReturn().getResponse().getContentAsString();
        long job = jdbc.queryForObject("INSERT INTO job_postings(title, unit_name) VALUES ('delta-title', 'delta-unit') RETURNING id", Long.class);
        UUID session = UUID.randomUUID();
        jdbc.update("INSERT INTO ticket_sessions(session_uid, job_id) VALUES (?, ?)", session, job);
        mvc.perform(post("/api/v1/jobs/{jobId}/ticket-sessions/{sessionUid}/tickets", job, session)
                        .contentType("application/json").content("{\"phone\":\"+82105550234\"}"))
                .andExpect(status().isOk());
        String after = mvc.perform(get("/actuator/prometheus")).andReturn().getResponse().getContentAsString();
        assertThat(metricValue(after, "issued") - metricValue(before, "issued")).isEqualTo(1.0);
        assertThat(metricValue(after, "duplicate") - metricValue(before, "duplicate")).isZero();
    }
}
