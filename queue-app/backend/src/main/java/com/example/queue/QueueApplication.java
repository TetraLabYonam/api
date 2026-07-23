package com.example.queue;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.context.annotation.Bean;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@SpringBootApplication
@EnableConfigurationProperties(QueueApplication.QueueProperties.class)
public class QueueApplication {
    public static void main(String[] args) {
        SpringApplication.run(QueueApplication.class, args);
    }

    @ConfigurationProperties(prefix = "queue")
    @Validated
    public record QueueProperties(@NotBlank String hmacKey, @NotBlank String hmacKeyVersion,
                                  Map<String, String> previousHmacKeys, @NotBlank String adminToken) {
        @ConstructorBinding
        public QueueProperties {
            previousHmacKeys = previousHmacKeys == null ? Map.of() : Map.copyOf(previousHmacKeys);
            requireSecret("queue.hmac-key", hmacKey);
            requireSecret("queue.admin-token", adminToken);
            if (hmacKeyVersion == null || hmacKeyVersion.isBlank()) {
                throw new IllegalArgumentException("queue.hmac-key-version must not be blank");
            }
            previousHmacKeys.forEach((version, key) -> {
                if (version == null || version.isBlank()) throw new IllegalArgumentException("Previous HMAC key version must not be blank");
                requireSecret("queue.previous-hmac-keys." + version, key);
            });
        }
        QueueProperties(String hmacKey, String hmacKeyVersion, String adminToken) {
            this(hmacKey, hmacKeyVersion, Map.of(), adminToken);
        }
        private static void requireSecret(String name, String value) {
            if (value == null || value.isBlank() || value.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalArgumentException(name + " must be at least 32 bytes");
            }
        }
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, AdminTokenFilter adminTokenFilter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .addFilterBefore(adminTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                writeSecurityError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Bearer token is required"))
                        .accessDeniedHandler((request, response, error) ->
                                writeSecurityError(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Valid admin token is required")))
                .build();
    }
    private static void writeSecurityError(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + status.value()
                + ",\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }

    @Component
    static final class AdminTokenFilter extends OncePerRequestFilter {
        private final byte[] expected;

        AdminTokenFilter(QueueProperties properties) {
            this.expected = properties.adminToken().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return !request.getRequestURI().startsWith("/api/v1/admin/");
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            String authorization = request.getHeader("Authorization");
            byte[] candidate = authorization != null && authorization.startsWith("Bearer ")
                    ? authorization.substring(7).getBytes(StandardCharsets.UTF_8) : new byte[0];
            if (MessageDigest.isEqual(expected, candidate)) {
                var authentication = UsernamePasswordAuthenticationToken.authenticated("bootstrap-admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .setAuthentication(authentication);
            }
            filterChain.doFilter(request, response);
        }
    }

    @RestController
    @RequestMapping("/api/v1/jobs")
    static final class PublicJobsController {
        private final JdbcTemplate jdbc;
        private final TicketIssuer issuer;

        PublicJobsController(JdbcTemplate jdbc, TicketIssuer issuer) {
            this.jdbc = jdbc;
            this.issuer = issuer;
        }

        @GetMapping
        List<PublicJobView> openJobs() {
            return jdbc.query("SELECT j.id, j.title, j.unit_name, s.session_uid FROM job_postings j "
                            + "JOIN ticket_sessions s ON s.job_id = j.id AND s.status = 'OPEN' "
                            + "WHERE j.status = 'OPEN' ORDER BY j.id",
                    (rs, row) -> new PublicJobView(rs.getLong("id"), rs.getString("title"),
                            rs.getString("unit_name"), rs.getObject("session_uid", UUID.class)));
        }

        @PostMapping("/{jobId}/ticket-sessions/{sessionUid}/tickets")
        TicketResult issue(@PathVariable long jobId, @PathVariable UUID sessionUid,
                           @Valid @RequestBody IssueTicketRequest request) {
            return issuer.issue(jobId, sessionUid, request.phone());
        }
    }

    @RestController
    @RequestMapping("/api/v1/admin")
    static final class AdminController {
        private final JdbcTemplate jdbc;
        private final TransactionTemplate transactions;

        AdminController(JdbcTemplate jdbc, TransactionTemplate transactions) {

            this.jdbc = jdbc;
            this.transactions = transactions;
        }
        @GetMapping("/jobs")
        List<AdminJobView> listJobs() {
            return jdbc.query("SELECT j.id, j.title, j.unit_name, s.session_uid FROM job_postings j "
                            + "LEFT JOIN ticket_sessions s ON s.job_id = j.id AND s.status = 'OPEN' "
                            + "ORDER BY j.id",
                    (rs, row) -> new AdminJobView(rs.getLong("id"), rs.getString("title"),
                            rs.getString("unit_name"), rs.getObject("session_uid", UUID.class)));
        }

        @PostMapping("/jobs")
        JobView createJob(@Valid @RequestBody CreateJobRequest request) {
            Long id = jdbc.queryForObject("INSERT INTO job_postings (title, unit_name) VALUES (?, ?) RETURNING id",
                    Long.class, request.title(), request.unitName());
            return new JobView(id, request.title(), request.unitName());
        }

        @PostMapping("/jobs/{jobId}/ticket-sessions")
        SessionView createSession(@PathVariable long jobId) {
            return transactions.execute(status -> {
                String jobStatus = jdbc.query("SELECT status FROM job_postings WHERE id = ? FOR UPDATE",
                        rs -> rs.next() ? rs.getString("status") : null, jobId);
                if (jobStatus == null) throw new NotFoundException("JOB_NOT_FOUND", "Job does not exist");
                if (!"OPEN".equals(jobStatus)) throw new ConflictException("JOB_CLOSED", "Job is not open");
                UUID uid = UUID.randomUUID();
                jdbc.update("INSERT INTO ticket_sessions (session_uid, job_id) VALUES (?, ?)", uid, jobId);
                return new SessionView(uid, jobId, "OPEN");
            });
        }

        @PostMapping("/ticket-sessions/{sessionUid}/close")
        SessionView closeSession(@PathVariable UUID sessionUid) {
            return transactions.execute(status -> {
                SessionLock session = jdbc.query("SELECT id, job_id, status FROM ticket_sessions WHERE session_uid = ? FOR UPDATE",
                        rs -> rs.next() ? new SessionLock(rs.getLong("id"), rs.getLong("job_id"), rs.getString("status")) : null,
                        sessionUid);
                if (session == null) throw new NotFoundException("SESSION_NOT_FOUND", "Ticket session does not exist");
                jdbc.queryForObject("SELECT id FROM job_postings WHERE id = ? FOR UPDATE", Long.class, session.jobId());
                if ("OPEN".equals(session.status())) {
                    jdbc.update("UPDATE ticket_sessions SET status = 'CLOSED', closed_at = CURRENT_TIMESTAMP WHERE id = ?", session.id());
                }
                return new SessionView(sessionUid, session.jobId(), "CLOSED");
            });
        }
    }

    @Component
    static final class TicketIssuer {
        private final JdbcTemplate jdbc;
        private final TransactionTemplate transactions;
        private final QueueProperties properties;
        private final MeterRegistry metrics;

        TicketIssuer(JdbcTemplate jdbc, TransactionTemplate transactions, QueueProperties properties, MeterRegistry metrics) {
            this.jdbc = jdbc;
            this.transactions = transactions;
            this.properties = properties;
            this.metrics = metrics;
        }

        TicketResult issue(long requestedJobId, UUID sessionUid, String suppliedPhone) {
            try {
                String phone = canonicalE164(suppliedPhone);
                List<byte[]> candidates = hmacCandidates(phone);
                TicketResult existing = findDuplicate(requestedJobId, sessionUid, candidates);
                if (existing != null) {
                    increment("duplicate");
                    return existing;
                }
                return transactions.execute(status -> issueLocked(requestedJobId, sessionUid, phone, candidates));
            } catch (RuntimeException e) {
                increment("rejected");
                throw e;
            }
        }

        private TicketResult issueLocked(long requestedJobId, UUID sessionUid, String phone, List<byte[]> candidates) {
            Timer.Sample timer = Timer.start(metrics);
            SessionLock session;
            try {
                session = jdbc.query("SELECT id, job_id, status FROM ticket_sessions WHERE session_uid = ? FOR UPDATE",
                        rs -> rs.next() ? new SessionLock(rs.getLong("id"), rs.getLong("job_id"), rs.getString("status")) : null, sessionUid);
            } finally {
                timer.stop(metrics.timer("queue.ticket.lock.wait", "resource", "session"));
            }
            if (session == null || session.jobId() != requestedJobId) {
                throw new NotFoundException("SESSION_NOT_FOUND", "Ticket session does not belong to this job");
            }
            timer = Timer.start(metrics);
            JobLock job;
            try {
                job = jdbc.query("SELECT id, title, unit_name, status FROM job_postings WHERE id = ? FOR UPDATE",
                        rs -> rs.next() ? new JobLock(rs.getLong("id"), rs.getString("title"), rs.getString("unit_name"), rs.getString("status")) : null,
                        requestedJobId);
            } finally {
                timer.stop(metrics.timer("queue.ticket.lock.wait", "resource", "job"));
            }
            if (job == null) throw new NotFoundException("JOB_NOT_FOUND", "Job does not exist");
            TicketResult existing = findDuplicate(requestedJobId, sessionUid, candidates);
            if (existing != null) {
                increment("duplicate");
                return existing;
            }
            if (!"OPEN".equals(session.status()) || !"OPEN".equals(job.status())) {
                throw new ConflictException("SESSION_CLOSED", "Ticket session is not open");
            }
            Long current = jdbc.queryForObject("SELECT current_value FROM global_ticket_counter WHERE singleton = TRUE FOR UPDATE", Long.class);
            if (current == null) throw new SequenceIntegrityException("Global ticket counter is missing");
            if (current == Long.MAX_VALUE) {
                throw new ServiceUnavailableException("TICKET_SEQUENCE_EXHAUSTED", "Ticket sequence is exhausted");
            }
            Long number = jdbc.queryForObject("UPDATE global_ticket_counter SET current_value = current_value + 1 "
                    + "WHERE singleton = TRUE RETURNING current_value", Long.class);
            if (number == null) throw new SequenceIntegrityException("Global ticket counter update failed");
            Instant issuedAt = Instant.now();
            jdbc.update("INSERT INTO draw_records (ticket_uid, session_id, job_id, phone_hmac, phone_last4, hmac_key_version, "
                            + "job_title_snapshot, unit_name_snapshot, global_number, issued_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), session.id(), requestedJobId, candidates.getFirst(), phone.substring(phone.length() - 4),
                    properties.hmacKeyVersion(), job.title(), job.unitName(), number, Timestamp.from(issuedAt));
            increment("issued");
            return new TicketResult(number, false, new JobView(job.id(), job.title(), job.unitName()), issuedAt);
        }

        private TicketResult findDuplicate(long jobId, UUID sessionUid, List<byte[]> candidates) {
            for (byte[] candidate : candidates) {
                TicketResult result = jdbc.query("SELECT d.global_number, d.job_title_snapshot, d.unit_name_snapshot, d.issued_at, d.job_id "
                                + "FROM draw_records d JOIN ticket_sessions s ON s.id = d.session_id "
                                + "WHERE s.session_uid = ? AND d.job_id = ? AND d.phone_hmac = ?",
                        rs -> rs.next() ? new TicketResult(rs.getLong("global_number"), true,
                                new JobView(rs.getLong("job_id"), rs.getString("job_title_snapshot"), rs.getString("unit_name_snapshot")),
                                rs.getTimestamp("issued_at").toInstant()) : null, sessionUid, jobId, candidate);
                if (result != null) return result;
            }
            return null;
        }

        private String canonicalE164(String phone) {
            String canonical = phone.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "");
            if (!canonical.matches("^\\+[1-9]\\d{7,14}$")) {
                throw new BadRequestException("INVALID_PHONE", "Phone must be a canonical E.164 number");
            }
            return canonical;
        }

        private List<byte[]> hmacCandidates(String canonicalPhone) {
            List<byte[]> candidates = new ArrayList<>();
            candidates.add(hmac(canonicalPhone, properties.hmacKey()));
            properties.previousHmacKeys().forEach((version, key) -> candidates.add(hmac(canonicalPhone, key)));
            return candidates;
        }

        private byte[] hmac(String canonicalPhone, String key) {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                return mac.doFinal(canonicalPhone.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new IllegalStateException("Unable to calculate phone HMAC", e);
            }
        }

        private void increment(String outcome) {
            Counter.builder("queue.ticket.requests").tag("outcome", outcome).register(metrics).increment();
        }
    }

    record PublicJobView(long id, String title, String unitName, UUID sessionUid) { }
    record AdminJobView(long id, String title, String unitName, UUID sessionUid) { }
    record JobView(long id, String title, String unitName) { }
    record SessionView(UUID sessionUid, long jobId, String status) { }
    record TicketResult(long number, boolean duplicate, JobView job, Instant issuedAt) { }
    record IssueTicketRequest(@NotBlank String phone) { }
    record CreateJobRequest(@NotBlank @Pattern(regexp = ".{1,200}") String title,
                            @NotBlank @Pattern(regexp = ".{1,200}") String unitName) { }
    record SessionLock(long id, long jobId, String status) { }
    record JobLock(long id, String title, String unitName, String status) { }

    static final class NotFoundException extends RuntimeException {
        final String code;
        NotFoundException(String code, String message) { super(message); this.code = code; }
    }
    static final class ConflictException extends RuntimeException {
        final String code;
        ConflictException(String code, String message) { super(message); this.code = code; }
    }
    static final class BadRequestException extends RuntimeException {
        final String code;
        BadRequestException(String code, String message) { super(message); this.code = code; }
    }
    static final class ServiceUnavailableException extends RuntimeException {
        final String code;
        ServiceUnavailableException(String code, String message) { super(message); this.code = code; }
    }
    static final class SequenceIntegrityException extends RuntimeException {
        SequenceIntegrityException(String message) { super(message); }
    }

    @RestControllerAdvice
    static final class ApiErrors {
        @ExceptionHandler(NotFoundException.class)
        ResponseEntity<ApiError> notFound(NotFoundException e) { return error(HttpStatus.NOT_FOUND, e.code, e.getMessage()); }
        @ExceptionHandler(ConflictException.class)
        ResponseEntity<ApiError> conflict(ConflictException e) { return error(HttpStatus.CONFLICT, e.code, e.getMessage()); }
        @ExceptionHandler(ServiceUnavailableException.class)
        ResponseEntity<ApiError> unavailable(ServiceUnavailableException e) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, e.code, e.getMessage());
        }
        @ExceptionHandler({SequenceIntegrityException.class, DataAccessException.class})
        ResponseEntity<ApiError> integrity(RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "SEQUENCE_INTEGRITY_FAILURE", "Ticket sequence integrity failure");
        }
        @ExceptionHandler(BadRequestException.class)
        ResponseEntity<ApiError> badRequest(BadRequestException e) { return error(HttpStatus.BAD_REQUEST, e.code, e.getMessage()); }
        private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
            return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), code, message));
        }
    }
    record ApiError(Instant timestamp, int status, String code, String message) { }
}
