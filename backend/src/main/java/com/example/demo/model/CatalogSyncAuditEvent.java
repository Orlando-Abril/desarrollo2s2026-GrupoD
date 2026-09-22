package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "catalog_sync_audit_events")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatalogSyncAuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private Long actorUserId;
    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;
    @Column(nullable = false, updatable = false, length = 64)
    private String action;
    @Column(nullable = false, updatable = false, length = 1000)
    private String detail;
    @Column(name = "entity_type", nullable = false, updatable = false, length = 64)
    private String entityType;
    @Column(name = "entity_id", updatable = false)
    private String entityId;
    @Column(name = "before_state", columnDefinition = "text", updatable = false)
    private String beforeState;
    @Column(name = "after_state", columnDefinition = "text", updatable = false)
    private String afterState;
    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private League league;
    @Column(name = "processed_count", updatable = false)
    private Integer processedCount;
    @Column(name = "failure_code", length = 64, updatable = false)
    private String failureCode;

    @PrePersist
    void initialize() {
        if (occurredAt == null) occurredAt = LocalDateTime.now();
    }
}
