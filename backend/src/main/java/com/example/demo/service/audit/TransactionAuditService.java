package com.example.demo.service.audit;

import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.model.market.AuditAction;
import com.example.demo.model.market.AuditActor;
import com.example.demo.model.market.AuditLog;
import com.example.demo.model.market.AuditTarget;
import com.example.demo.repository.market.AuditLogRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Único punto para registrar auditoría del mercado (spec 026, FR-023). Corre en la transacción de quien
 * llama, así el cambio y su auditoría se confirman o se revierten juntos. Quien llama pasa snapshots
 * (records), nunca entidades.
 */
@Service
public class TransactionAuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TransactionAuditService(AuditLogRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditLog audit(AuditActor actor, AuditAction action, String entityType, Object entityId,
                          Object before, Object after) {
        if (after == null) {
            throw new IllegalArgumentException("after is required");
        }
        String beforeJson = before == null ? null : objectMapper.writeValueAsString(before);
        AuditLog log = AuditLog.of(actor, action, new AuditTarget(entityType, String.valueOf(entityId)), beforeJson,
                objectMapper.writeValueAsString(after), Instant.now(clock), correlationId());
        return repository.save(log);
    }

    private static String correlationId() {
        String current = MDC.get(CorrelationIdFilter.MDC_KEY);
        return current == null || current.isBlank() ? UUID.randomUUID().toString() : current;
    }
}
