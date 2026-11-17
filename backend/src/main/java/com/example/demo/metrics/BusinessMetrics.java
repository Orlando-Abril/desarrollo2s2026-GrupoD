package com.example.demo.metrics;

import com.example.demo.model.market.OrderSide;
import com.example.demo.model.market.OrderType;
import com.example.demo.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Métricas de negocio de la aplicación (spec 036), publicadas por {@code /actuator/prometheus}. Es el único lugar con
 * nombres, descripciones y etiquetas; services y adapters llaman a sus métodos y no conocen Micrometer.
 *
 * <p>Medir nunca cambia el resultado de la operación: cada método captura sus propias fallas y las deja en el log.
 * Órdenes creadas y trades se cuentan al confirmarse la transacción, si la hay; los demás eventos, en el momento.
 */
@Component
public class BusinessMetrics {

    static final String ORDERS_PLACED = "gdmarket.orders.placed";
    static final String ORDERS_REJECTED = "gdmarket.orders.rejected";
    static final String TRADES_EXECUTED = "gdmarket.trades.executed";
    static final String TRADES_TOKENS = "gdmarket.trades.tokens";
    static final String TRADES_VOLUME = "gdmarket.trades.volume.credits";
    static final String RECALCULATION = "gdmarket.quotes.recalculation";
    static final String RECALCULATION_LAST = "gdmarket.quotes.recalculation.last";
    static final String QUOTES_CALCULATED = "gdmarket.quotes.calculated";
    static final String STRATEGY_CHANGES = "gdmarket.strategy.changes";
    static final String EXTERNAL_CALLS = "gdmarket.external.calls";
    static final String REGISTRATIONS = "gdmarket.auth.registrations";
    static final String LOGINS = "gdmarket.auth.logins";
    static final String USERS_REGISTERED = "gdmarket.users.registered";

    private static final Logger log = LoggerFactory.getLogger(BusinessMetrics.class);

    private final MeterRegistry registry;
    private final UserRepository userRepository;
    private final Map<OrderSide, Map<OrderType, Counter>> ordersCreated = new EnumMap<>(OrderSide.class);
    private final Map<OrderRejectionReason, Counter> ordersRejected = new EnumMap<>(OrderRejectionReason.class);
    private final Counter tradesExecuted;
    private final Counter tradesTokens;
    private final Counter tradesVolume;
    private final Map<RecalculationTrigger, Map<RecalculationOutcome, Timer>> recalculations =
            new EnumMap<>(RecalculationTrigger.class);
    private final AtomicLong lastRecalculationNanos = new AtomicLong();
    private final Counter strategyChanges;
    private final Map<ExternalSource, Map<ExternalCallOutcome, Counter>> externalCalls =
            new EnumMap<>(ExternalSource.class);
    private final Map<AuthOutcome, Counter> registrationCounters = new EnumMap<>(AuthOutcome.class);
    private final Map<AuthOutcome, Counter> loginCounters = new EnumMap<>(AuthOutcome.class);

    public BusinessMetrics(MeterRegistry registry, UserRepository userRepository) {
        this.registry = registry;
        this.userRepository = userRepository;

        for (OrderSide side : OrderSide.values()) {
            Map<OrderType, Counter> byType = new EnumMap<>(OrderType.class);
            for (OrderType type : OrderType.values()) {
                byType.put(type, Counter.builder(ORDERS_PLACED)
                        .description("Órdenes de mercado aceptadas y confirmadas")
                        .tag("side", side.name()).tag("type", type.name())
                        .register(registry));
            }
            ordersCreated.put(side, byType);
        }
        for (OrderRejectionReason reason : OrderRejectionReason.values()) {
            ordersRejected.put(reason, Counter.builder(ORDERS_REJECTED)
                    .description("Órdenes rechazadas por saldo, tokens o banda de precio")
                    .tag("reason", reason.tag())
                    .register(registry));
        }
        tradesExecuted = Counter.builder(TRADES_EXECUTED)
                .description("Trades ejecutados y confirmados").register(registry);
        tradesTokens = Counter.builder(TRADES_TOKENS)
                .description("Tokens operados en trades confirmados").register(registry);
        tradesVolume = Counter.builder(TRADES_VOLUME)
                .description("Volumen operado en créditos (cantidad × precio)").register(registry);

        for (RecalculationTrigger trigger : RecalculationTrigger.values()) {
            Map<RecalculationOutcome, Timer> byOutcome = new EnumMap<>(RecalculationOutcome.class);
            for (RecalculationOutcome outcome : RecalculationOutcome.values()) {
                byOutcome.put(outcome, Timer.builder(RECALCULATION)
                        .description("Duración de cada recálculo de cotizaciones")
                        .tag("trigger", trigger.tag()).tag("outcome", outcome.tag())
                        .publishPercentileHistogram()
                        .register(registry));
            }
            recalculations.put(trigger, byOutcome);
        }
        TimeGauge.builder(RECALCULATION_LAST, lastRecalculationNanos, TimeUnit.NANOSECONDS, AtomicLong::doubleValue)
                .description("Duración de la última corrida de recálculo terminada, con éxito o con fallo; "
                        + "0 hasta la primera")
                .register(registry);
        strategyChanges = Counter.builder(STRATEGY_CHANGES)
                .description("Cambios confirmados de la estrategia activa").register(registry);

        for (ExternalSource source : ExternalSource.values()) {
            Map<ExternalCallOutcome, Counter> byOutcome = new EnumMap<>(ExternalCallOutcome.class);
            for (ExternalCallOutcome outcome : ExternalCallOutcome.values()) {
                byOutcome.put(outcome, Counter.builder(EXTERNAL_CALLS)
                        .description("Consultas a fuentes externas por resultado")
                        .tag("source", source.tag()).tag("outcome", outcome.tag())
                        .register(registry));
            }
            externalCalls.put(source, byOutcome);
        }
        for (AuthOutcome outcome : AuthOutcome.values()) {
            registrationCounters.put(outcome, Counter.builder(REGISTRATIONS)
                    .description("Registros de usuarios por resultado")
                    .tag("outcome", outcome.tag()).register(registry));
            loginCounters.put(outcome, Counter.builder(LOGINS)
                    .description("Logins por resultado")
                    .tag("outcome", outcome.tag()).register(registry));
        }
        Gauge.builder(USERS_REGISTERED, this, BusinessMetrics::registeredUsers)
                .description("Cuentas de usuario registradas")
                .strongReference(true)
                .register(registry);
    }

    /** Una orden aceptada y guardada; se cuenta al confirmarse la transacción. */
    public void orderCreated(OrderSide side, OrderType type) {
        safely(ORDERS_PLACED, () -> onCommit(ORDERS_PLACED, () -> ordersCreated.get(side).get(type).increment()));
    }

    /** Una orden rechazada por uno de los tres motivos; se cuenta en el momento, porque la transacción se revierte. */
    public void orderRejected(OrderRejectionReason reason) {
        safely(ORDERS_REJECTED, () -> ordersRejected.get(reason).increment());
    }

    /** Un trade de {@code tokens} por {@code credits} en total; se cuenta al confirmarse la transacción. */
    public void tradeExecuted(int tokens, BigDecimal credits) {
        safely(TRADES_EXECUTED, () -> onCommit(TRADES_EXECUTED, () -> {
            tradesExecuted.increment();
            tradesTokens.increment(tokens);
            tradesVolume.increment(credits.doubleValue());
        }));
    }

    /** Fin de una corrida de recálculo: su duración por disparador y resultado, y la duración de la última. */
    public void recalculationFinished(RecalculationTrigger trigger, RecalculationOutcome outcome, Duration duration) {
        safely(RECALCULATION, () -> {
            lastRecalculationNanos.set(duration.toNanos());
            recalculations.get(trigger).get(outcome).record(duration);
        });
    }

    /** Cotizaciones calculadas por una estrategia en una corrida. El contador se crea al primer uso. */
    public void quotesCalculated(String strategy, int count) {
        if (count <= 0) {
            return;
        }
        safely(QUOTES_CALCULATED, () -> Counter.builder(QUOTES_CALCULATED)
                .description("Cotizaciones calculadas por estrategia")
                .tag("strategy", strategy)
                .register(registry)
                .increment(count));
    }

    /** Un cambio confirmado de la estrategia activa. */
    public void activeStrategyChanged() {
        safely(STRATEGY_CHANGES, strategyChanges::increment);
    }

    /** Una consulta a una fuente externa y su resultado. */
    public void externalCall(ExternalSource source, ExternalCallOutcome outcome) {
        safely(EXTERNAL_CALLS, () -> externalCalls.get(source).get(outcome).increment());
    }

    /** Un intento de registro y su resultado. */
    public void registration(AuthOutcome outcome) {
        safely(REGISTRATIONS, () -> registrationCounters.get(outcome).increment());
    }

    /** Un intento de login y su resultado. */
    public void login(AuthOutcome outcome) {
        safely(LOGINS, () -> loginCounters.get(outcome).increment());
    }

    /** Valor del gauge de usuarios: {@code NaN} si el conteo falla, para no romper la lectura de métricas. */
    double registeredUsers() {
        try {
            return userRepository.count();
        } catch (RuntimeException exception) {
            logFailure(USERS_REGISTERED, exception);
            return Double.NaN;
        }
    }

    /** Ejecuta la medición al confirmarse la transacción en curso, o ya si no hay ninguna. */
    private static void onCommit(String metric, Runnable measurement) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            measurement.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safely(metric, measurement);
            }
        });
    }

    private static void safely(String metric, Runnable measurement) {
        try {
            measurement.run();
        } catch (RuntimeException exception) {
            logFailure(metric, exception);
        }
    }

    private static void logFailure(String metric, RuntimeException exception) {
        log.warn("business_metric_failed metric={} errorType={}", metric, exception.getClass().getSimpleName());
    }
}
