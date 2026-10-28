package com.example.demo.repository.market;

import com.example.demo.model.market.AuditAction;
import com.example.demo.model.market.AuditLog;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Auditoría del mercado. Extiende {@link Repository} y no {@code JpaRepository} para que no exista
 * ninguna operación de borrado: una entrada de auditoría es inmutable (spec 026, FR-022).
 */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog auditLog);

    Optional<AuditLog> findById(Long id);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(String entityType, String entityId);

    List<AuditLog> findByActionOrderByIdAsc(AuditAction action);
}
