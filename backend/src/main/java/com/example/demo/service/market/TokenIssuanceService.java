package com.example.demo.service.market;

import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.model.market.AuditAction;
import com.example.demo.model.market.AuditActor;
import com.example.demo.model.market.Holding;
import com.example.demo.repository.market.HoldingRepository;
import com.example.demo.service.audit.TransactionAuditService;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;

/**
 * Emite los 100 tokens de cada jugador a nombre del superusuario (spec 026, FR-006). La tenencia del
 * superusuario es la marca de emitido: como nunca se borra, un jugador se emite una sola vez aunque el
 * superusuario venda todos sus tokens. Cada jugador se emite en su propia transacción.
 */
@Service
public class TokenIssuanceService {

    public static final int TOKENS_PER_PLAYER = 100;

    private static final Logger log = LoggerFactory.getLogger(TokenIssuanceService.class);

    private final SuperuserAccount superuserAccount;
    private final HoldingRepository holdingRepository;
    private final TransactionAuditService auditService;
    private final TransactionOperations transactionOperations;
    private final EntityManager entityManager;
    private final Clock clock;

    public TokenIssuanceService(SuperuserAccount superuserAccount, HoldingRepository holdingRepository,
                                TransactionAuditService auditService, TransactionOperations transactionOperations,
                                EntityManager entityManager, Clock clock) {
        this.superuserAccount = superuserAccount;
        this.holdingRepository = holdingRepository;
        this.auditService = auditService;
        this.transactionOperations = transactionOperations;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** @throws IllegalStateException con {@link SuperuserAccount#MISSING} si no existe el superusuario */
    public IssuanceResult issueMissing() {
        Long superuserId = superuserAccount.id();
        int issued = 0;
        int failed = 0;
        for (Long playerId : holdingRepository.findPlayerIdsWithoutHolding(superuserId)) {
            try {
                transactionOperations.executeWithoutResult(status -> issue(superuserId, playerId));
                issued++;
            } catch (DataIntegrityViolationException ex) {
                log.debug("market_issuance_already_issued playerId={}", playerId);
            } catch (RuntimeException ex) {
                failed++;
                log.warn("market_issuance_failed playerId={} code={}", playerId, ex.getClass().getSimpleName());
            }
        }
        log.info("market_issuance_completed issued={} failed={}", issued, failed);
        return new IssuanceResult(issued, failed);
    }

    private void issue(Long superuserId, Long playerId) {
        Holding holding = holdingRepository.save(Holding.issue(entityManager.getReference(User.class, superuserId),
                entityManager.getReference(Player.class, playerId), TOKENS_PER_PLAYER, Instant.now(clock)));
        auditService.record(AuditActor.system(), AuditAction.TOKENS_ISSUED, "HOLDING", holding.getId(),
                null, HoldingSnapshot.of(holding));
    }
}
