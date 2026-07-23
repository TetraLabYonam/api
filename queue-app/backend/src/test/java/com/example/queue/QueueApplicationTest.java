package com.example.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class QueueApplicationTest {
    private static final String ACTIVE = "a".repeat(32);
    private static final String PREVIOUS = "b".repeat(32);

    @Test
    void canonicalPhonePreservesLeadingZeroAndRemovesOnlyAsciiSeparators() throws Exception {
        QueueApplication.TicketIssuer issuer = issuer();
        assertEquals("+10123456789", canonical(issuer, " +1 (0) 123-456-789 "));
    }

    @Test
    void canonicalPhoneRejectsUnicodeWhitespace() {
        QueueApplication.TicketIssuer issuer = issuer();
        assertThrows(QueueApplication.BadRequestException.class, () -> canonical(issuer, "+1\u00a0123456789"));
    }

    @Test
    void hmacRotationSuppliesActiveAndPreviousCandidates() throws Exception {
        QueueApplication.TicketIssuer issuer = new QueueApplication.TicketIssuer(new JdbcTemplate(), new TransactionTemplate(),
                new QueueApplication.QueueProperties(ACTIVE, "v2", Map.of("v1", PREVIOUS), "c".repeat(32)), new SimpleMeterRegistry());
        Method method = QueueApplication.TicketIssuer.class.getDeclaredMethod("hmacCandidates", String.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        var candidates = (java.util.List<byte[]>) method.invoke(issuer, "+10123456789");
        assertEquals(2, candidates.size());
        org.junit.jupiter.api.Assertions.assertFalse(Arrays.equals(candidates.get(0), candidates.get(1)));
    }

    @Test
    void startupRejectsShortSecrets() {
        assertThrows(IllegalArgumentException.class, () -> new QueueApplication.QueueProperties("short", "v1", "c".repeat(32)));
        assertThrows(IllegalArgumentException.class, () -> new QueueApplication.QueueProperties(ACTIVE, "v1", "short"));
    }

    @Test
    void sequenceExhaustionMapsToServiceUnavailable() {
        QueueApplication.ApiErrors errors = new QueueApplication.ApiErrors();
        var response = errors.unavailable(new QueueApplication.ServiceUnavailableException("TICKET_SEQUENCE_EXHAUSTED", "exhausted"));
        assertEquals(503, response.getStatusCode().value());
        assertEquals("TICKET_SEQUENCE_EXHAUSTED", response.getBody().code());
    }

    @Test
    void databaseIntegrityFailureIsNotReportedAsConflict() {
        QueueApplication.ApiErrors errors = new QueueApplication.ApiErrors();
        var response = errors.integrity(new org.springframework.dao.DataIntegrityViolationException("duplicate global number"));
        assertEquals(500, response.getStatusCode().value());
        assertEquals("SEQUENCE_INTEGRITY_FAILURE", response.getBody().code());
    }

    private static QueueApplication.TicketIssuer issuer() {
        return new QueueApplication.TicketIssuer(new JdbcTemplate(), new TransactionTemplate(),
                new QueueApplication.QueueProperties(ACTIVE, "v1", "c".repeat(32)), new SimpleMeterRegistry());
    }

    private static String canonical(QueueApplication.TicketIssuer issuer, String value) {
        try {
            Method method = QueueApplication.TicketIssuer.class.getDeclaredMethod("canonicalE164", String.class);
            method.setAccessible(true);
            return (String) method.invoke(issuer, value);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new AssertionError(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
