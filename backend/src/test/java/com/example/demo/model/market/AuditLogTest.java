package com.example.demo.model.market;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditLogTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    private static final String CORRELATION_ID = "3f2b8c1e-5d7a-4c9b-8e6f-1a2b3c4d5e6f";
    private static final AuditActor ACTOR = AuditActor.system();
    private static final AuditAction ACTION = AuditAction.TOKENS_ISSUED;

    @Test
    void buildsAnEntryWithItsFields() {
        AuditLog log = AuditLog.of(AuditActor.user(3L), ACTION, "HOLDING", "7", null, "{}", NOW, CORRELATION_ID);

        assertThat(log.getActorType()).isEqualTo(AuditActorType.USER);
        assertThat(log.getActorUserId()).isEqualTo(3L);
        assertThat(log.getBeforeState()).isNull();
    }

    @Test
    void rejectsMissingRequiredFields() {
        List<Runnable> invalid = List.of(
                () -> AuditLog.of(ACTOR, null, "HOLDING", "7", null, "{}", NOW, CORRELATION_ID),
                () -> AuditLog.of(ACTOR, ACTION, null, "7", null, "{}", NOW, CORRELATION_ID),
                () -> AuditLog.of(ACTOR, ACTION, "HOLDING", null, null, "{}", NOW, CORRELATION_ID),
                () -> AuditLog.of(ACTOR, ACTION, "HOLDING", "7", null, null, NOW, CORRELATION_ID),
                () -> AuditLog.of(ACTOR, ACTION, "HOLDING", "7", null, "{}", null, CORRELATION_ID),
                () -> AuditLog.of(ACTOR, ACTION, "HOLDING", "7", null, "{}", NOW, null));

        invalid.forEach(call -> assertThatThrownBy(call::run).isInstanceOf(IllegalArgumentException.class));
    }
}
