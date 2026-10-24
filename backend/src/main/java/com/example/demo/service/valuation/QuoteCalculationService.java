package com.example.demo.service.valuation;

import com.example.demo.exception.RecalculationFailedException;
import com.example.demo.exception.RecalculationInProgressException;
import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.model.Player;
import com.example.demo.model.PlayerQuote;
import com.example.demo.model.PlayerStats;
import com.example.demo.model.QuoteStatus;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.PlayerStatsRepository;
import com.example.demo.service.valuation.strategy.StrategyType;
import com.example.demo.service.valuation.strategy.ValuationStrategyCatalog;
import com.example.demo.service.valuation.strategy.ValuationStrategyDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Corrida de recálculo de cotizaciones (feature 016): toma un snapshot de estadísticas, valúa a cada jugador con
 * cada estrategia que tiene algoritmo y guarda una {@link PlayerQuote} por jugador y estrategia. Si la estrategia
 * activa se calculó, copia su valor a {@link Player#getMarketValue()}. Sólo usa datos persistidos.
 * <p>
 * Cada jugador se guarda en su propia transacción: una falla revierte sus cotizaciones y su precio, y la corrida
 * sigue. Como máximo corre una corrida a la vez (una sola instancia, ver research D6).
 */
@Service
public class QuoteCalculationService {

    private static final Logger log = LoggerFactory.getLogger(QuoteCalculationService.class);
    private static final int SCALE = 4;

    private final ValuationStrategyCatalog catalog;
    private final Map<StrategyType, ValuationAlgorithm> algorithms;
    private final StatsSnapshotService snapshotService;
    private final PlayerRepository playerRepository;
    private final PlayerStatsRepository statsRepository;
    private final PlayerQuoteRepository quoteRepository;
    private final Clock clock;
    private final TransactionOperations playerTransaction;
    private final TransactionTemplate readOnlyTransaction;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public QuoteCalculationService(ValuationStrategyCatalog catalog,
                                   List<ValuationAlgorithm> algorithms,
                                   StatsSnapshotService snapshotService,
                                   PlayerRepository playerRepository,
                                   PlayerStatsRepository statsRepository,
                                   PlayerQuoteRepository quoteRepository,
                                   Clock clock,
                                   TransactionOperations playerTransaction,
                                   PlatformTransactionManager transactionManager) {
        this.catalog = catalog;
        this.algorithms = byType(algorithms);
        this.snapshotService = snapshotService;
        this.playerRepository = playerRepository;
        this.statsRepository = statsRepository;
        this.quoteRepository = quoteRepository;
        this.clock = clock;
        this.playerTransaction = playerTransaction;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    /**
     * Ejecuta una corrida para la estrategia pedida o, si está vacía, para todas las del catálogo.
     *
     * @throws com.example.demo.exception.UnknownValuationStrategyException si el código no existe
     * @throws RecalculationInProgressException si ya hay una corrida en curso
     * @throws RecalculationFailedException si falla el snapshot, la carga o el armado del resumen
     */
    public QuoteRunResult recalculate(Optional<String> strategyCode) {
        Objects.requireNonNull(strategyCode, "strategyCode must not be null");
        List<ValuationStrategyDefinition> definitions = strategyCode
                .map(code -> List.of(catalog.find(code)))
                .orElseGet(catalog::all);
        if (!running.compareAndSet(false, true)) {
            throw new RecalculationInProgressException();
        }

        String previousCorrelationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        String correlationId = previousCorrelationId;
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
        }
        try {
            Run run = new Run(UUID.randomUUID(), clock.instant(), correlationId);
            return execute(run, strategyCode.orElse(null), definitions);
        } finally {
            running.set(false);
            if (previousCorrelationId == null) {
                MDC.remove(CorrelationIdFilter.MDC_KEY);
            } else {
                MDC.put(CorrelationIdFilter.MDC_KEY, previousCorrelationId);
            }
        }
    }

    private QuoteRunResult execute(Run run, String requestedStrategy, List<ValuationStrategyDefinition> definitions) {
        long startedAt = System.nanoTime();
        log.info("quote_recalculation_started runId={} requestedStrategy={}", run.id(), requestedStrategy);
        try {
            Plan plan = preparePlan(run, definitions);

            Tally tally = new Tally(plan.strategies());
            for (Player player : plan.players()) {
                process(player, plan, run, tally);
            }

            QuoteRunResult result = summarizeSafely(run, requestedStrategy, plan, tally);
            log.info("quote_recalculation_finished runId={} calculated={} skipped={} processed={} failed={} "
                            + "marketValueUpdated={} durationMs={}",
                    run.id(), result.calculated(), result.skipped(), result.playersProcessed(),
                    result.playersFailed(), result.marketValueUpdated(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return result;
        } catch (RuntimeException exception) {
            Throwable cause = exception instanceof RecalculationFailedException && exception.getCause() != null
                    ? exception.getCause()
                    : exception;
            log.error("quote_recalculation_failed runId={} errorType={}",
                    run.id(), cause.getClass().getSimpleName(), exception);
            throw exception;
        }
    }

    private Plan preparePlan(Run run, List<ValuationStrategyDefinition> definitions) {
        try {
            snapshotService.snapshotAll(run.id());
            return load(definitions);
        } catch (RuntimeException exception) {
            throw new RecalculationFailedException(run.id(), exception);
        }
    }

    /** Fija jugadores, estadísticas, población y última cotización por estrategia antes de calcular. */
    private Plan load(List<ValuationStrategyDefinition> definitions) {
        return Objects.requireNonNull(readOnlyTransaction.execute(status -> {
            List<Player> players = playerRepository.findAll();
            Map<Long, PlayerStats> stats = new HashMap<>();
            for (PlayerStats row : statsRepository.findAll()) {
                stats.put(row.getPlayer().getId(), row);
            }
            List<PopulationEntry> entries = players.stream()
                    .map(player -> new PopulationEntry(player.getId(), player.getPositions(), stats.get(player.getId())))
                    .toList();

            List<StrategyPlan> strategies = new ArrayList<>();
            List<SkippedStrategy> skipped = new ArrayList<>();
            for (ValuationStrategyDefinition definition : definitions) {
                ValuationAlgorithm algorithm = algorithms.get(definition.type());
                if (algorithm == null) {
                    skipped.add(new SkippedStrategy(definition.code(), definition.version(),
                            SkippedStrategy.NO_ALGORITHM));
                    continue;
                }
                Map<Long, PlayerQuote> latest = new HashMap<>();
                for (PlayerQuote quote : quoteRepository.findLatestForAllPlayers(definition.code())) {
                    latest.put(quote.getPlayer().getId(), quote);
                }
                strategies.add(new StrategyPlan(definition, algorithm, algorithm.preparePopulation(entries), latest));
            }
            return new Plan(players, stats, strategies, skipped);
        }), "read-only load returned null");
    }

    private void process(Player player, Plan plan, Run run, Tally tally) {
        AtomicReference<String> currentStrategy = new AtomicReference<>();
        try {
            List<PlayerQuote> quotes = playerTransaction.execute(status -> {
                Player managed = playerRepository.findById(player.getId())
                        .orElseThrow(() -> new IllegalStateException("El jugador " + player.getId() + " ya no existe"));
                List<PlayerQuote> built = new ArrayList<>(plan.strategies().size());
                for (StrategyPlan strategy : plan.strategies()) {
                    currentStrategy.set(strategy.definition().code());
                    built.add(quote(managed, plan.stats().get(managed.getId()), strategy, run));
                }
                currentStrategy.set(null);
                quoteRepository.saveAll(built);
                String active = catalog.active().code();
                for (PlayerQuote quote : built) {
                    if (quote.getStrategyCode().equals(active)) {
                        managed.setMarketValue(quote.getValue());
                    }
                }
                return built;
            });
            tally.processed(quotes == null ? List.of() : quotes);
        } catch (RuntimeException exception) {
            tally.failed();
            log.error("quote_recalculation_player_failed runId={} playerId={} strategy={} errorType={}",
                    run.id(), player.getId(), currentStrategy.get(), exception.getClass().getSimpleName(), exception);
        }
    }

    private PlayerQuote quote(Player player, PlayerStats stats, StrategyPlan strategy, Run run) {
        ValuationStrategyDefinition definition = strategy.definition();
        PlayerQuote latest = strategy.latest().get(player.getId());
        QuoteResult result = strategy.algorithm().calculate(
                new ValuationInput(player.getId(), player.getPositions(), stats, strategy.population(),
                        latest == null ? List.of() : List.of(latest), List.of()),
                definition);

        BigDecimal value;
        BigDecimal score;
        if (result.status() == QuoteStatus.CALCULATED) {
            value = result.value();
            score = result.score();
        } else {
            value = latest != null ? latest.getValue() : definition.baseValue().setScale(SCALE, RoundingMode.HALF_UP);
            score = null;
        }
        return PlayerQuote.builder()
                .player(player)
                .strategyCode(definition.code())
                .strategyVersion(definition.version())
                .value(value)
                .score(score)
                .status(result.status())
                .calculatedAt(run.calculatedAt())
                .runId(run.id())
                .correlationId(run.correlationId())
                .build();
    }

    private QuoteRunResult summarizeSafely(Run run, String requestedStrategy, Plan plan, Tally tally) {
        try {
            return summarize(run, requestedStrategy, plan, tally);
        } catch (RuntimeException exception) {
            throw new RecalculationFailedException(run.id(), exception);
        }
    }

    private QuoteRunResult summarize(Run run, String requestedStrategy, Plan plan, Tally tally) {
        String active = catalog.active().code();
        boolean activeCalculated = plan.strategies().stream()
                .anyMatch(strategy -> strategy.definition().code().equals(active));
        boolean marketValueUpdated = activeCalculated && (tally.processed > 0 || plan.players().isEmpty());
        return new QuoteRunResult(run.id(), run.calculatedAt(), requestedStrategy, tally.counts(plan.strategies()),
                plan.skipped(), tally.processed, tally.failed, active, marketValueUpdated);
    }

    private static Map<StrategyType, ValuationAlgorithm> byType(List<ValuationAlgorithm> algorithms) {
        Map<StrategyType, ValuationAlgorithm> byType = new EnumMap<>(StrategyType.class);
        for (ValuationAlgorithm algorithm : algorithms) {
            if (byType.put(algorithm.type(), algorithm) != null) {
                throw new IllegalStateException("Hay más de un algoritmo para " + algorithm.type());
            }
        }
        return byType;
    }

    private record Run(UUID id, Instant calculatedAt, String correlationId) {
    }

    private record StrategyPlan(ValuationStrategyDefinition definition, ValuationAlgorithm algorithm,
                                NormalizedPopulation population, Map<Long, PlayerQuote> latest) {
    }

    private record Plan(List<Player> players, Map<Long, PlayerStats> stats, List<StrategyPlan> strategies,
                        List<SkippedStrategy> skipped) {
    }

    /** Conteos de la corrida; sólo suma jugadores cuya transacción se confirmó. */
    private static final class Tally {

        private final Map<String, int[]> byStrategy = new HashMap<>();
        private int processed;
        private int failed;

        Tally(List<StrategyPlan> strategies) {
            for (StrategyPlan strategy : strategies) {
                byStrategy.put(strategy.definition().code(), new int[QuoteStatus.values().length]);
            }
        }

        void processed(List<PlayerQuote> quotes) {
            processed++;
            for (PlayerQuote quote : quotes) {
                byStrategy.get(quote.getStrategyCode())[quote.getStatus().ordinal()]++;
            }
        }

        void failed() {
            failed++;
        }

        List<StrategyRunCounts> counts(List<StrategyPlan> strategies) {
            return strategies.stream().map(strategy -> {
                ValuationStrategyDefinition definition = strategy.definition();
                int[] counts = byStrategy.get(definition.code());
                return new StrategyRunCounts(definition.code(), definition.version(),
                        counts[QuoteStatus.CALCULATED.ordinal()],
                        counts[QuoteStatus.NO_STATS.ordinal()],
                        counts[QuoteStatus.INSUFFICIENT_HISTORY.ordinal()]);
            }).toList();
        }
    }
}
