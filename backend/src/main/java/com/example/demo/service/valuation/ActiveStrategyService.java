package com.example.demo.service.valuation;

import com.example.demo.model.ActiveStrategySetting;
import com.example.demo.model.User;
import com.example.demo.model.market.AuditAction;
import com.example.demo.model.market.AuditActor;
import com.example.demo.repository.ActiveStrategySettingRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.audit.TransactionAuditService;
import com.example.demo.service.valuation.strategy.ValuationStrategyCatalog;
import com.example.demo.service.valuation.strategy.ValuationStrategyDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Estrategia activa en ejecución y estrategia de mercado (spec 031). La configuración vive en la única fila de
 * {@link ActiveStrategySetting}; los cambios se serializan con un lock pesimista sobre esa fila y se auditan.
 */
@Service
public class ActiveStrategyService implements MarketStrategyRecorder {

    static final String AUDIT_ENTITY = "VALUATION_STRATEGY";
    private static final Logger log = LoggerFactory.getLogger(ActiveStrategyService.class);

    private final ValuationStrategyCatalog catalog;
    private final ActiveStrategySettingRepository settings;
    private final UserRepository users;
    private final TransactionAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final TransactionTemplate insertTransaction;

    public ActiveStrategyService(ValuationStrategyCatalog catalog, ActiveStrategySettingRepository settings,
                                 UserRepository users, TransactionAuditService auditService,
                                 ApplicationEventPublisher eventPublisher, Clock clock,
                                 PlatformTransactionManager transactionManager) {
        this.catalog = catalog;
        this.settings = settings;
        this.users = users;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.insertTransaction = new TransactionTemplate(transactionManager);
        this.insertTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public ActiveStrategyView view() {
        Optional<ActiveStrategySetting> setting = settings.findById(ActiveStrategySetting.SINGLETON_ID);
        ValuationStrategyDefinition active = catalog.active();
        ValuationStrategyDefinition market = setting.map(ActiveStrategySetting::getMarketStrategyCode)
                .map(catalog::find)
                .orElse(active);
        ActiveStrategyView.ChangedBy changedBy = setting
                .filter(row -> row.getChangedByActorType() != null)
                .map(row -> new ActiveStrategyView.ChangedBy(row.getChangedByActorType(), row.getChangedByUserId(),
                        row.getChangedByUserId() == null ? null
                                : users.findById(row.getChangedByUserId()).map(User::getUsername).orElse(null)))
                .orElse(null);
        return new ActiveStrategyView(active, catalog.alternative(), market,
                setting.map(ActiveStrategySetting::getChangedAt).orElse(null), changedBy);
    }

    /** Estrategia con que está fijado el {@code marketValue} vigente; la activa si nunca se registró. */
    @Transactional(readOnly = true)
    public ValuationStrategyDefinition marketStrategy() {
        return settings.findById(ActiveStrategySetting.SINGLETON_ID)
                .map(ActiveStrategySetting::getMarketStrategyCode)
                .map(catalog::find)
                .orElseGet(catalog::active);
    }

    /**
     * Registra la estrategia que fijó los precios en la corrida {@code runId}. Si cambió, audita
     * {@link AuditAction#MARKET_STRATEGY_APPLIED} una vez, con actor de sistema. El {@code marketValue} de cada
     * jugador no se audita (feature 016).
     */
    @Override
    @Transactional
    public void recordMarketStrategy(String code, Instant appliedAt, UUID runId) {
        ValuationStrategyDefinition applied = catalog.find(code);
        ActiveStrategySetting setting = lockedSetting();
        String previous = setting.getMarketStrategyCode();
        setting.recordMarket(applied.code(), appliedAt);
        if (!applied.code().equals(previous)) {
            Map<String, Object> after = state(applied);
            after.put("runId", runId.toString());
            auditService.audit(AuditActor.system(), AuditAction.MARKET_STRATEGY_APPLIED, AUDIT_ENTITY, "market",
                    previous == null ? null : state(catalog.find(previous)), after);
            log.info("market_strategy_applied from={} to={} runId={}", previous, applied.code(), runId);
        }
    }

    /**
     * Cambia la estrategia activa. No toca {@code marketValue}: se aplica en el próximo recálculo.
     *
     * @return false si la estrategia ya estaba activa (sin escritura ni auditoría)
     * @throws com.example.demo.exception.UnknownValuationStrategyException si el código no existe
     */
    @Transactional
    public boolean change(String rawCode, Authentication authentication) {
        ValuationStrategyDefinition target = catalog.find(rawCode == null ? null : rawCode.trim());
        ActiveStrategySetting setting = lockedSetting();
        ValuationStrategyDefinition current = catalog.active();
        if (current.code().equals(target.code())) {
            return false;
        }
        AuditActor actor = actorOf(authentication);
        setting.assumeMarketIfUnknown(current.code());
        setting.changeActive(target.code(), actor.type(), actor.userId(), clock.instant());
        auditService.audit(actor, AuditAction.ACTIVE_STRATEGY_CHANGED, AUDIT_ENTITY, "active",
                state(current), state(target));
        eventPublisher.publishEvent(new ActiveStrategyChangedEvent(current.code(), target.code()));
        log.info("active_strategy_changed from={} to={} actorType={}", current.code(), target.code(), actor.type());
        return true;
    }

    private ActiveStrategySetting lockedSetting() {
        if (settings.findLockedById(ActiveStrategySetting.SINGLETON_ID).isEmpty()) {
            insertIfAbsent();
        }
        return settings.findLockedById(ActiveStrategySetting.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("active_strategy_settings sin fila"));
    }

    /** Inserta la fila en su propia transacción; si otro pedido la insertó antes, gana ese. */
    private void insertIfAbsent() {
        try {
            insertTransaction.executeWithoutResult(status -> {
                if (!settings.existsById(ActiveStrategySetting.SINGLETON_ID)) {
                    settings.saveAndFlush(ActiveStrategySetting.empty());
                }
            });
        } catch (DataIntegrityViolationException concurrentInsert) {
            log.debug("active_strategy_settings_insert_race");
        }
    }

    private AuditActor actorOf(Authentication authentication) {
        if (authentication == null) {
            return AuditActor.system();
        }
        return users.findByUsername(authentication.getName())
                .map(user -> AuditActor.user(user.getId()))
                .orElseGet(AuditActor::system);
    }

    private static Map<String, Object> state(ValuationStrategyDefinition definition) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("strategy", definition.code());
        state.put("version", definition.version());
        return state;
    }
}
