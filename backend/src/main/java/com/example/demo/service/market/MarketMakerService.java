package com.example.demo.service.market;

import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.model.market.AuditAction;
import com.example.demo.model.market.AuditActor;
import com.example.demo.model.market.Holding;
import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderSide;
import com.example.demo.repository.market.HoldingRepository;
import com.example.demo.repository.market.MarketOrderRepository;
import com.example.demo.service.audit.TransactionAuditService;
import com.example.demo.service.valuation.QuoteRunCompletedEvent;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * El superusuario como creador de mercado del lado de la venta (spec 027, US7, research D7): por cada jugador del que
 * tiene tokens, una única venta LIMIT con todos ellos a la cotización vigente. {@link #syncPlayer} es idempotente y
 * corre al arrancar y después de cada recálculo, en una transacción por jugador; su orden no se rige por la banda.
 */
@Service
public class MarketMakerService {

    private static final Logger log = LoggerFactory.getLogger(MarketMakerService.class);

    private final boolean enabled;
    private final SuperuserAccount superuserAccount;
    private final HoldingRepository holdingRepository;
    private final MarketOrderRepository orderRepository;
    private final MarketLocks locks;
    private final TransactionAuditService auditService;
    private final TransactionOperations transactionOperations;
    private final EntityManager entityManager;
    private final Clock clock;

    public MarketMakerService(@Value("${market.superuser.enabled:true}") boolean enabled,
                              SuperuserAccount superuserAccount, HoldingRepository holdingRepository,
                              MarketOrderRepository orderRepository, MarketLocks locks,
                              TransactionAuditService auditService, TransactionOperations transactionOperations,
                              EntityManager entityManager, Clock clock) {
        this.enabled = enabled;
        this.superuserAccount = superuserAccount;
        this.holdingRepository = holdingRepository;
        this.orderRepository = orderRepository;
        this.locks = locks;
        this.auditService = auditService;
        this.transactionOperations = transactionOperations;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** Corre después de todos los {@code ApplicationRunner}: el superusuario ya existe y sus tokens ya están emitidos. */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        boolean generated = MDC.get(CorrelationIdFilter.MDC_KEY) == null;
        if (generated) {
            MDC.put(CorrelationIdFilter.MDC_KEY, UUID.randomUUID().toString());
        }
        try {
            syncAll();
        } catch (RuntimeException ex) {
            log.warn("market_maker_startup_failed errorType={}", ex.getClass().getSimpleName());
        } finally {
            if (generated) {
                MDC.remove(CorrelationIdFilter.MDC_KEY);
            }
        }
    }

    /**
     * {@code QuoteCalculationService} publica el evento fuera de una transacción, así que sin
     * {@code fallbackExecution = true} este listener no correría; si algún día se publica dentro de una, corre después
     * del commit. El {@code @Order} lo deja después de la emisión de tokens. Usa el correlation id de la corrida, que
     * sigue en el MDC mientras se publica el evento, y nunca propaga una falla al recálculo.
     */
    @TransactionalEventListener(fallbackExecution = true)
    @Order(MarketListenerOrder.MARKET_MAKER)
    public void onQuoteRunCompleted(QuoteRunCompletedEvent event) {
        try {
            syncAll();
        } catch (RuntimeException ex) {
            log.warn("market_maker_after_quote_run_failed runId={} errorType={}", event.runId(),
                    ex.getClass().getSimpleName());
        }
    }

    public SyncResult syncAll() {
        if (!enabled) {
            log.info("market_maker_disabled");
            return SyncResult.EMPTY;
        }
        Long superuserId;
        try {
            superuserId = superuserAccount.id();
        } catch (IllegalStateException ex) {
            if (!SuperuserAccount.MISSING.equals(ex.getMessage())) {
                throw ex;
            }
            log.info("market_maker_skipped reason={}", SuperuserAccount.MISSING);
            return SyncResult.EMPTY;
        }
        int updated = 0;
        int unchanged = 0;
        int failed = 0;
        for (Long playerId : holdingRepository.findPlayerIdsWithTokens(superuserId)) {
            try {
                if (Boolean.TRUE.equals(transactionOperations.execute(status -> syncPlayer(superuserId, playerId)))) {
                    updated++;
                } else {
                    unchanged++;
                }
            } catch (RuntimeException ex) {
                failed++;
                log.warn("market_maker_sync_failed playerId={} errorType={}", playerId, ex.getClass().getSimpleName());
            }
        }
        log.info("market_maker_sync_completed updated={} unchanged={} failed={}", updated, unchanged, failed);
        return new SyncResult(updated, unchanged, failed);
    }

    /**
     * Deja la venta del superusuario de un jugador con todos sus tokens a la cotización vigente; devuelve si la
     * rehízo. Requiere una transacción: la auditoría corre con {@code MANDATORY}. Toma los locks en el orden de
     * {@link MarketLocks}: jugador, órdenes y tenencia.
     */
    public boolean syncPlayer(Long superuserId, Long playerId) {
        Player player = locks.lockPlayer(playerId);
        BigDecimal marketValue = player.getMarketValue();
        if (marketValue == null) {
            return false;
        }
        List<MarketOrder> active = orderRepository.findActiveSellsForUpdate(superuserId, playerId);
        Holding holding = locks.lockHolding(superuserId, playerId).orElse(null);
        int tokens = holding == null ? 0 : holding.getQuantity();
        if (isCurrent(active, marketValue, tokens) || (active.isEmpty() && tokens == 0)) {
            return false;
        }

        Instant now = Instant.now(clock);
        AuditActor system = AuditActor.system();
        for (MarketOrder order : active) {
            OrderSnapshot orderBefore = OrderSnapshot.of(order);
            order.cancel(now);
            auditService.audit(system, AuditAction.ORDER_CANCELLED, MarketEntityTypes.ORDER, order.getId(),
                    orderBefore, OrderSnapshot.of(order));
            if (holding != null && order.remaining() > 0) {
                HoldingSnapshot holdingBefore = HoldingSnapshot.of(holding);
                holding.release(order.remaining(), now);
                auditService.audit(system, AuditAction.HOLDING_CHANGED, MarketEntityTypes.HOLDING, holding.getId(),
                        holdingBefore, HoldingSnapshot.of(holding));
            }
        }
        if (holding == null || holding.available() == 0) {
            return true;
        }
        int quantity = holding.available();
        MarketOrder order = orderRepository.save(MarketOrder.limit(entityManager.getReference(User.class, superuserId),
                player, OrderSide.SELL, quantity, marketValue, now));
        auditService.audit(system, AuditAction.ORDER_CREATED, MarketEntityTypes.ORDER, order.getId(), null,
                OrderSnapshot.of(order));
        HoldingSnapshot holdingBefore = HoldingSnapshot.of(holding);
        holding.reserve(quantity, now);
        auditService.audit(system, AuditAction.HOLDING_CHANGED, MarketEntityTypes.HOLDING, holding.getId(),
                holdingBefore, HoldingSnapshot.of(holding));
        log.info("market_maker_repriced playerId={} orderId={} quantity={} price={}", playerId, order.getId(), quantity,
                order.getLimitPrice());
        return true;
    }

    private static boolean isCurrent(List<MarketOrder> active, BigDecimal marketValue, int tokens) {
        return active.size() == 1
                && active.get(0).getLimitPrice().compareTo(marketValue) == 0
                && active.get(0).remaining() == tokens;
    }
}
