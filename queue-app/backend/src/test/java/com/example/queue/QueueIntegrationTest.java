package com.example.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class QueueIntegrationTest {
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
    @Autowired QueueApplication.TicketIssuer issuer;
    @Autowired MockMvc mvc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM draw_records");
        jdbc.update("DELETE FROM ticket_sessions");
        jdbc.update("DELETE FROM job_postings");
        jdbc.update("UPDATE global_ticket_counter SET current_value = 0 WHERE singleton = TRUE");
    }

    @Test
    void oneHundredConcurrentApplicantsShareOneGlobalSequence() throws Exception {
        long firstJob = createJob("first");
        long secondJob = createJob("second");
        UUID firstSession = createSession(firstJob);
        UUID secondSession = createSession(secondJob);
        var pool = Executors.newFixedThreadPool(20);
        try {
            List<Callable<Long>> calls = java.util.stream.IntStream.range(0, 100)
                    .mapToObj(i -> (Callable<Long>) () -> issuer.issue(
                            i % 2 == 0 ? firstJob : secondJob,
                            i % 2 == 0 ? firstSession : secondSession,
                            "+8210555" + String.format("%04d", i)).number())
                    .toList();
            var futures = pool.invokeAll(calls);
            var numbers = new HashSet<Long>();
            for (var future : futures) numbers.add(future.get());
            assertThat(numbers).hasSize(100).contains(1L, 100L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM draw_records", Long.class)).isEqualTo(100L);
            assertThat(jdbc.queryForObject("SELECT current_value FROM global_ticket_counter", Long.class)).isEqualTo(100L);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void duplicateSurvivesCloseWithoutConsumingNumber() {
        long job = createJob("job");
        UUID session = createSession(job);
        var first = issuer.issue(job, session, "+821012345678");
        jdbc.update("UPDATE ticket_sessions SET status='CLOSED', closed_at=CURRENT_TIMESTAMP WHERE session_uid=?", session);
        var retry = issuer.issue(job, session, "+82 (10) 1234-5678");
        assertThat(retry.duplicate()).isTrue();
        assertThat(retry.number()).isEqualTo(first.number());
        assertThat(jdbc.queryForObject("SELECT current_value FROM global_ticket_counter", Long.class)).isEqualTo(1L);
    }

    @Test
    void overflowAndMismatchedJobDoNotMutateState() {
        long job = createJob("job");
        UUID session = createSession(job);
        assertThatThrownBy(() -> issuer.issue(job + 1, session, "+821012345678"))
                .isInstanceOf(QueueApplication.NotFoundException.class);
        assertThat(jdbc.queryForObject("SELECT current_value FROM global_ticket_counter", Long.class)).isZero();
        jdbc.update("UPDATE global_ticket_counter SET current_value=?", Long.MAX_VALUE);
        assertThatThrownBy(() -> issuer.issue(job, session, "+821012345678"))
                .isInstanceOf(QueueApplication.ServiceUnavailableException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM draw_records", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT current_value FROM global_ticket_counter", Long.class)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void adminMutationRequiresValidBearerToken() throws Exception {
        String body = "{\"title\":\"job\",\"unitName\":\"unit\"}";
        mvc.perform(post("/api/v1/admin/jobs").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/jobs").header("Authorization", "Bearer wrong")
                        .contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/jobs").header("Authorization", "Bearer " + SECRET)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());
    }
    @Test
    void adminJobListingRequiresBearerAndIncludesJobsWithoutSessions() throws Exception {
        long job = createJob("unsessioned-admin-job");

        mvc.perform(get("/api/v1/admin/jobs"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/jobs").header("Authorization", "Bearer " + SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(job))
                .andExpect(jsonPath("$[0].sessionUid").doesNotExist());
        mvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void publicDiscoveryToIssueUsesExactSessionAndDoesNotExposePhoneData() throws Exception {
        long job = createJob("public-job");
        UUID session = createSession(job);

        mvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(job))
                .andExpect(jsonPath("$[0].sessionUid").value(session.toString()))
                .andExpect(jsonPath("$[0].phoneHmac").doesNotExist())
                .andExpect(jsonPath("$[0].phoneLast4").doesNotExist());

        String response = mvc.perform(post("/api/v1/jobs/{jobId}/ticket-sessions/{sessionUid}/tickets", job, session)
                        .contentType("application/json")
                        .content("{\"phone\":\"+821012345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.duplicate").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("+821012345678", "phoneHmac", "phoneLast4", "hmacKeyVersion");

        long otherJob = createJob("other");
        mvc.perform(post("/api/v1/jobs/{jobId}/ticket-sessions/{sessionUid}/tickets", otherJob, session)
                        .contentType("application/json")
                        .content("{\"phone\":\"+821099999999\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM draw_records", Long.class)).isEqualTo(1L);
    }

    @Test
    void migrationConstraintsRejectInvalidAlternativeWriters() {
        long firstJob = createJob("first");
        long secondJob = createJob("second");
        UUID session = createSession(firstJob);

        assertThatThrownBy(() -> createSession(firstJob))
                .isInstanceOf(DataIntegrityViolationException.class);

        Long sessionId = jdbc.queryForObject("SELECT id FROM ticket_sessions WHERE session_uid=?", Long.class, session);
        assertThatThrownBy(() -> insertDraw(sessionId, secondJob, new byte[32], "1234", 1L))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDraw(sessionId, firstJob, new byte[31], "1234", 2L))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDraw(sessionId, firstJob, new byte[32], "12ab", 3L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test
    void adminLifecycleControlsPublicVisibilityAndIssuance() throws Exception {
        String auth = "Bearer " + SECRET;
        String created = mvc.perform(post("/api/v1/admin/jobs").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"title\":\"lifecycle\",\"unitName\":\"unit\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long job = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        String sessionBody = mvc.perform(post("/api/v1/admin/jobs/{jobId}/ticket-sessions", job)
                        .header("Authorization", auth)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID session = UUID.fromString(sessionBody.replaceAll(".*\"sessionUid\":\"([^\"]+)\".*", "$1"));

        mvc.perform(get("/api/v1/jobs")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(job))
                .andExpect(jsonPath("$[0].sessionUid").value(session.toString()));
        mvc.perform(post("/api/v1/admin/jobs/{jobId}/ticket-sessions", job).header("Authorization", auth))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SEQUENCE_INTEGRITY_FAILURE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_sessions WHERE job_id=?", Long.class, job)).isEqualTo(1L);

        mvc.perform(post("/api/v1/admin/ticket-sessions/{sessionUid}/close", session).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        mvc.perform(get("/api/v1/jobs")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(post("/api/v1/jobs/{jobId}/ticket-sessions/{sessionUid}/tickets", job, session)
                        .contentType("application/json").content("{\"phone\":\"+821012345678\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_CLOSED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM draw_records", Long.class)).isZero();
    }

    @Test
    void deniedAdminLifecycleOperationsDoNotMutateState() throws Exception {
        long job = createJob("denied");
        String wrong = "Bearer wrong";
        mvc.perform(post("/api/v1/admin/jobs/{jobId}/ticket-sessions", job).header("Authorization", wrong))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/ticket-sessions/{sessionUid}/close", UUID.randomUUID()).header("Authorization", wrong))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_sessions", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM job_postings", Long.class)).isEqualTo(1L);
    }

    private void insertDraw(long sessionId, long jobId, byte[] hmac, String last4, long number) {
        jdbc.update("INSERT INTO draw_records "
                        + "(ticket_uid, session_id, job_id, phone_hmac, phone_last4, hmac_key_version, "
                        + "job_title_snapshot, unit_name_snapshot, global_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), sessionId, jobId, hmac, last4, "v1", "job", "unit", number);
    }

    private long createJob(String title) {
        return jdbc.queryForObject("INSERT INTO job_postings(title, unit_name) VALUES (?, 'unit') RETURNING id", Long.class, title);
    }

    private UUID createSession(long job) {
        UUID uid = UUID.randomUUID();
        jdbc.update("INSERT INTO ticket_sessions(session_uid, job_id) VALUES (?, ?)", uid, job);
        return uid;
    }
}
