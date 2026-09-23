package com.example.demo.service;

import com.example.demo.model.CatalogSyncAuditEvent;
import com.example.demo.model.League;
import com.example.demo.repository.CatalogSyncAuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CatalogSyncAuditService {
    public static final List<String> TERMINAL_ACTIONS =
            List.of("COMPLETED", "PARTIAL_FAILURE", "FAILED");

    private final CatalogSyncAuditEventRepository repository;

    public CatalogSyncAuditService(CatalogSyncAuditEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Eventos que deben sobrevivir al rollback del trabajo que auditan: STARTED, terminales
     * y PLAYER_FAILED.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(Long actorUserId, UUID correlationId, String action, String detail,
                       String entityType, String entityId, String beforeState, String afterState,
                       League league, Integer processedCount, String failureCode) {
        save(actorUserId, correlationId, action, detail, entityType, entityId, beforeState, afterState,
                league, processedCount, failureCode);
    }

    /**
     * Eventos de cambio de un jugador (PLAYER_CREATED/UPDATED, TOKENS_ALLOCATED): viajan en la
     * transacción del jugador para que un jugador revertido no deje auditoría huérfana.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendInCurrentTransaction(Long actorUserId, UUID correlationId, String action, String detail,
                                           String entityType, String entityId, String beforeState,
                                           String afterState, League league, Integer processedCount,
                                           String failureCode) {
        save(actorUserId, correlationId, action, detail, entityType, entityId, beforeState, afterState,
                league, processedCount, failureCode);
    }

    private void save(Long actorUserId, UUID correlationId, String action, String detail,
                      String entityType, String entityId, String beforeState, String afterState,
                      League league, Integer processedCount, String failureCode) {
        repository.save(CatalogSyncAuditEvent.builder()
                .actorUserId(actorUserId)
                .correlationId(correlationId)
                .action(sanitize(action, 64))
                .detail(sanitize(detail, 1000))
                .entityType(sanitize(entityType, 64))
                .entityId(sanitize(entityId, 255))
                .beforeState(sanitize(beforeState, 4000))
                .afterState(sanitize(afterState, 4000))
                .league(league)
                .processedCount(processedCount)
                .failureCode(sanitize(failureCode, 64))
                .build());
    }

    @Transactional(readOnly = true)
    public Optional<CatalogSyncAuditEvent> lastTerminalEvent() {
        return repository.findFirstByActionInOrderByOccurredAtDesc(TERMINAL_ACTIONS);
    }

    @Transactional(readOnly = true)
    public boolean hasSuccessfulSnapshot() {
        return repository.existsByAction("COMPLETED") || repository.existsByAction("PARTIAL_FAILURE");
    }

    private String sanitize(String value, int max) {
        if (value == null) return null;
        String clean = value.replaceAll("(?i)(token|authorization|password)\\s*[:=]\\s*[^,; }]+", "$1=[redacted]");
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
