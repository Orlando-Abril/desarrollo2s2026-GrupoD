package com.example.demo.model.market;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

/**
 * Registro inmutable de un cambio de estado financiero (spec 026, FR-021). Nunca se actualiza ni se borra.
 * {@code @Immutable} hace que Hibernate ignore los cambios sucios, pero Hibernate permite borrar entidades
 * inmutables: por eso {@link #preventRemove()} rechaza el borrado por entidad. Los {@code DELETE} JPQL masivos
 * y el SQL nativo quedan fuera de esta garantía; la aplicación no los usa sobre esta tabla.
 */
@Entity
@Table(name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_logs_entity", columnList = "entity_type, entity_id"),
                @Index(name = "idx_audit_logs_occurred", columnList = "occurred_at")
        },
        check = @CheckConstraint(name = "ck_audit_logs_actor",
                constraint = "(actor_type = 'SYSTEM' AND actor_user_id IS NULL)"
                        + " OR (actor_type = 'USER' AND actor_user_id IS NOT NULL)"))
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", length = 16, nullable = false, updatable = false)
    private AuditActorType actorType;

    /** Sin FK: la auditoría sobrevive a cualquier cambio en {@code users}. */
    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false, updatable = false)
    private AuditAction action;

    @Column(name = "entity_type", length = 32, nullable = false, updatable = false)
    private String entityType;

    @Column(name = "entity_id", length = 64, nullable = false, updatable = false)
    private String entityId;

    /** Nulo cuando la entidad no existía antes del cambio. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", updatable = false)
    private String beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", nullable = false, updatable = false)
    private String afterState;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "correlation_id", length = 36, nullable = false, updatable = false)
    private String correlationId;

    public static AuditLog of(AuditActor actor, AuditAction action, AuditTarget target,
                              String beforeJson, String afterJson, Instant occurredAt, String correlationId) {
        AuditLog log = new AuditLog();
        log.actorType = required(actor, "actor").type();
        log.actorUserId = actor.userId();
        log.action = required(action, "action");
        log.entityType = required(required(target, "target").type(), "entityType");
        log.entityId = required(target.id(), "entityId");
        log.beforeState = beforeJson;
        log.afterState = required(afterJson, "afterJson");
        log.occurredAt = required(occurredAt, "occurredAt");
        log.correlationId = required(correlationId, "correlationId");
        return log;
    }

    @PreRemove
    void preventRemove() {
        throw new UnsupportedOperationException("audit_log_is_immutable");
    }

    private static <T> T required(T value, String name) {
        if (Objects.isNull(value) || value instanceof String text && text.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
