package com.example.demo.repository.market;

import com.example.demo.config.ImmutableEntityConfig;
import com.example.demo.model.market.AuditAction;
import com.example.demo.model.market.AuditActor;
import com.example.demo.model.market.AuditLog;
import com.example.demo.model.market.AuditTarget;
import com.example.demo.support.UnitTestProfile;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@UnitTestProfile
@Import(ImmutableEntityConfig.class)
class AuditLogRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-10-27T10:00:00Z");

    @Autowired private AuditLogRepository repository;
    @Autowired private TestEntityManager entityManager;

    private AuditLog saved(String entityId, Instant occurredAt) {
        AuditLog log = repository.save(AuditLog.of(AuditActor.system(), AuditAction.TOKENS_ISSUED,
                new AuditTarget("HOLDING", entityId), null, "{\"quantity\":100}", occurredAt, UUID.randomUUID().toString()));
        entityManager.flush();
        return log;
    }

    @Test
    void ignoresChangesToALoadedEntry() {
        AuditLog log = saved("1", T0);

        ReflectionTestUtils.setField(log, "entityId", "changed");
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findById(log.getId()).orElseThrow().getEntityId()).isEqualTo("1");
    }

    @Test
    void rejectsBulkUpdates() {
        AuditLog log = saved("1", T0);

        assertThatThrownBy(() -> entityManager.getEntityManager()
                .createQuery("update AuditLog a set a.entityId = 'changed'")
                .executeUpdate())
                .hasMessageContaining("immutable");
        assertThat(entityManager.getEntityManager()
                .createNativeQuery("SELECT entity_id FROM audit_logs WHERE id = " + log.getId())
                .getSingleResult()).isEqualTo("1");
    }

    @Test
    void rejectsRemovingAnEntry() {
        AuditLog log = saved("1", T0);

        assertThatThrownBy(() -> entityManager.remove(log))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(((Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT count(*) FROM audit_logs WHERE id = " + log.getId())
                .getSingleResult()).longValue()).isEqualTo(1);
    }

    @Test
    void offersNoDeleteOperation() {
        assertThat(Arrays.stream(AuditLogRepository.class.getMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("delete"));
    }

    @Test
    void returnsTheHistoryOfAnEntityInOrder() {
        String entityId = UUID.randomUUID().toString();
        AuditLog later = saved(entityId, T0.plusSeconds(60));
        AuditLog earlier = saved(entityId, T0);
        saved("other", T0);

        assertThat(repository.findByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc("HOLDING", entityId))
                .containsExactly(earlier, later);
    }

    @Test
    void theDatabaseRejectsAUserActorWithoutId() {
        AuditLog log = saved("1", T0);

        assertThatThrownBy(() -> entityManager.getEntityManager().createNativeQuery(
                        "UPDATE audit_logs SET actor_type = 'USER' WHERE id = " + log.getId())
                .executeUpdate())
                .isInstanceOf(PersistenceException.class);
    }
}
