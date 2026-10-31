package com.example.demo.service.audit;

import com.example.demo.filter.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Spec 031: el correlation id de la auditoría entra siempre en {@code audit_logs.correlation_id} (36). */
class AuditCorrelationIdTest {

    @AfterEach
    void clean() {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }

    @Test
    void keepsARequestUuid() {
        String uuid = UUID.randomUUID().toString();
        MDC.put(CorrelationIdFilter.MDC_KEY, uuid);

        assertThat(TransactionAuditService.correlationId()).isEqualTo(uuid);
    }

    @Test
    void keepsTheUuidOfAScheduledRunCorrelation() {
        String uuid = UUID.randomUUID().toString();
        MDC.put(CorrelationIdFilter.MDC_KEY, "valuation-" + uuid);

        assertThat(TransactionAuditService.correlationId()).isEqualTo(uuid);
    }

    @Test
    void replacesALongValueWithoutUuidWithAFreshUuid() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "x".repeat(60));

        String correlationId = TransactionAuditService.correlationId();

        assertThat(correlationId).hasSize(36);
        assertThatCode(() -> UUID.fromString(correlationId)).doesNotThrowAnyException();
    }

    @Test
    void generatesOneWithoutMdc() {
        assertThat(TransactionAuditService.correlationId()).hasSize(36);
    }
}
