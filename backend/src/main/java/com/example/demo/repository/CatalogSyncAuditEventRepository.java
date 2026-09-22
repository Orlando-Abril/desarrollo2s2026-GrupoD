package com.example.demo.repository;

import com.example.demo.model.CatalogSyncAuditEvent;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.Optional;

public interface CatalogSyncAuditEventRepository extends Repository<CatalogSyncAuditEvent, Long> {
    CatalogSyncAuditEvent save(CatalogSyncAuditEvent event);
    Optional<CatalogSyncAuditEvent> findFirstByActionInOrderByOccurredAtDesc(Collection<String> actions);
    boolean existsByAction(String action);
}
