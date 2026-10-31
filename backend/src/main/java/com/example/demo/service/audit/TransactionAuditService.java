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

    private static final int MAX_CORRELATION_LENGTH = 36;

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

    /**
     * Correlation id del pedido o de la corrida. {@code audit_logs.correlation_id} admite 36 caracteres: un valor
     * más largo con un UUID al final (p. ej. {@code valuation-<uuid>} del recálculo programado) se guarda como ese
     * UUID; antes fallaba la inserción (spec 031).
     */
    static String correlationId() {
        String current = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (current == null || current.isBlank()) {
            return UUID.randomUUID().toString();
        }
        if (current.length() <= MAX_CORRELATION_LENGTH) {
            return current;
        }
        String suffix = current.substring(current.length() - MAX_CORRELATION_LENGTH);
        try {
            return UUID.fromString(suffix).toString();
        } catch (IllegalArgumentException notAUuid) {
            return UUID.randomUUID().toString();
        }
    }
}
