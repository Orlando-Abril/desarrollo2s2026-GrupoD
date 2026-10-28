package com.example.demo.service.audit;

import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.model.market.AuditAction;
import com.example.demo.model.market.AuditActor;
import com.example.demo.model.market.AuditActorType;
import com.example.demo.model.market.AuditLog;
import com.example.demo.repository.market.AuditLogRepository;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@UnitTestProfile
@Import({TransactionAuditService.class, TransactionAuditServiceTest.Config.class})
class TransactionAuditServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");
    private static final String CORRELATION_ID = "3f2b8c1e-5d7a-4c9b-8e6f-1a2b3c4d5e6f";

    @TestConfiguration
    static class Config {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }

    record Snapshot(Long id, int quantity) {
    }

    @Autowired
    private TransactionAuditService auditService;

    @Autowired
    private AuditLogRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void clearMdc() {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }

    @Test
    void recordsEveryField() {
        MDC.put(CorrelationIdFilter.MDC_KEY, CORRELATION_ID);

        AuditLog saved = auditService.audit(AuditActor.user(7L), AuditAction.TOKENS_ISSUED, "HOLDING", 42L,
                new Snapshot(42L, 0), new Snapshot(42L, 100));

        AuditLog reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getActorType()).isEqualTo(AuditActorType.USER);
        assertThat(reloaded.getActorUserId()).isEqualTo(7L);
        assertThat(reloaded.getAction()).isEqualTo(AuditAction.TOKENS_ISSUED);
        assertThat(reloaded.getEntityType()).isEqualTo("HOLDING");
        assertThat(reloaded.getEntityId()).isEqualTo("42");
        assertThat(reloaded.getOccurredAt()).isEqualTo(NOW);
        assertThat(reloaded.getCorrelationId()).isEqualTo(CORRELATION_ID);
        assertThat(objectMapper.readTree(reloaded.getBeforeState()).get("quantity").asInt()).isZero();
    }

    @Test
    void generatesACorrelationIdWhenThereIsNoRequest() {
        AuditLog saved = auditService.audit(AuditActor.system(), AuditAction.SUPERUSER_CREATED, "USER", 1L,
                null, new Snapshot(1L, 0));

        assertThat(UUID.fromString(saved.getCorrelationId())).isNotNull();
    }

    @Test
    void storesNullBeforeStateAndReadableAfterState() {
        AuditLog saved = auditService.audit(AuditActor.system(), AuditAction.TOKENS_ISSUED, "HOLDING", 5L,
                null, new Snapshot(5L, 100));

        AuditLog reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getBeforeState()).isNull();
        JsonNode after = objectMapper.readTree(reloaded.getAfterState());
        assertThat(after.isObject()).isTrue();
        assertThat(after.get("quantity").asInt()).isEqualTo(100);
    }

    @Test
    void rejectsAMissingAfterState() {
        assertThatThrownBy(() -> auditService.audit(AuditActor.system(), AuditAction.TOKENS_ISSUED,
                "HOLDING", 5L, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void requiresAnActiveTransaction() {
        assertThatThrownBy(() -> auditService.audit(AuditActor.system(), AuditAction.TOKENS_ISSUED,
                "HOLDING", 9L, null, new Snapshot(9L, 100)))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(repository.findByActionOrderByIdAsc(AuditAction.TOKENS_ISSUED))
                .extracting(AuditLog::getEntityId)
                .doesNotContain("9");
    }
}
