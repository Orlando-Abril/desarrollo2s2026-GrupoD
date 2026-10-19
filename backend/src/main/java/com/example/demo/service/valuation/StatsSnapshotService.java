package com.example.demo.service.valuation;

import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.model.PlayerStats;
import com.example.demo.model.PlayerStatsSnapshot;
import com.example.demo.repository.PlayerStatsRepository;
import com.example.demo.repository.PlayerStatsSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Captura de manera append-only los totales estadísticos ya persistidos. */
@Service
public class StatsSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(StatsSnapshotService.class);

    private final PlayerStatsRepository statsRepository;
    private final PlayerStatsSnapshotRepository snapshotRepository;
    private final Clock clock;
    private final TransactionTemplate requiresNewTransaction;

    public StatsSnapshotService(PlayerStatsRepository statsRepository,
                                PlayerStatsSnapshotRepository snapshotRepository,
                                Clock clock,
                                PlatformTransactionManager transactionManager) {
        this.statsRepository = statsRepository;
        this.snapshotRepository = snapshotRepository;
        this.clock = clock;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Captura todos los {@link PlayerStats} locales cuya versión de origen todavía no fue guardada.
     * Una colisión de unicidad concurrente se reclasifica como salteada después de comprobar la clave.
     */
    public SnapshotResult snapshotAll(UUID runId) {
        String previousCorrelationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (previousCorrelationId == null || previousCorrelationId.isBlank()) {
            MDC.put(CorrelationIdFilter.MDC_KEY, UUID.randomUUID().toString());
        }

        try {
            Objects.requireNonNull(runId, "runId must not be null");
            SnapshotResult result = capture(runId);
            log.info("player_stats_snapshot_finished runId={} saved={} skipped={}",
                    runId, result.saved(), result.skipped());
            return result;
        } catch (RuntimeException exception) {
            log.error("player_stats_snapshot_failed runId={} errorType={}",
                    runId, exception.getClass().getSimpleName(), exception);
            throw exception;
        } finally {
            if (previousCorrelationId == null) {
                MDC.remove(CorrelationIdFilter.MDC_KEY);
            } else {
                MDC.put(CorrelationIdFilter.MDC_KEY, previousCorrelationId);
            }
        }
    }

    /** Devuelve el subconjunto reciente en orden cronológico ascendente. */
    public List<PlayerStatsSnapshot> recentSnapshots(Long playerId, int n) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        if (n <= 0) {
            throw new IllegalArgumentException("n must be greater than zero");
        }
        List<PlayerStatsSnapshot> recent = new ArrayList<>(
                snapshotRepository.findByPlayerIdOrderByCapturedAtDescIdDesc(
                        playerId, PageRequest.of(0, n)));
        Collections.reverse(recent);
        return List.copyOf(recent);
    }

    /** Deriva una ventana por cada par consecutivo de los últimos {@code n} snapshots. */
    public List<StatsWindow> recentWindows(Long playerId, int n) {
        List<PlayerStatsSnapshot> snapshots = recentSnapshots(playerId, n);
        if (snapshots.size() < 2) {
            return List.of();
        }
        List<StatsWindow> windows = new ArrayList<>(snapshots.size() - 1);
        for (int index = 1; index < snapshots.size(); index++) {
            windows.add(toWindow(snapshots.get(index - 1), snapshots.get(index)));
        }
        return List.copyOf(windows);
    }

    private SnapshotResult capture(UUID runId) {
        Instant capturedAt = clock.instant();
        Map<SnapshotKey, PlayerStats> candidates = new LinkedHashMap<>();
        for (PlayerStats stats : statsRepository.findAll()) {
            SnapshotKey key = new SnapshotKey(stats.getPlayer().getId(), stats.getFetchedAt());
            candidates.put(key, stats);
        }
        if (candidates.isEmpty()) {
            return new SnapshotResult(0, 0);
        }

        Set<SnapshotKey> pending = new LinkedHashSet<>(candidates.keySet());
        int initiallyPresent = removeExisting(pending);
        int saved = 0;
        int skipped = initiallyPresent;

        while (!pending.isEmpty()) {
            List<PlayerStatsSnapshot> batch = pending.stream()
                    .map(key -> toSnapshot(candidates.get(key), capturedAt, runId))
                    .toList();
            try {
                requiresNewTransaction.executeWithoutResult(status -> snapshotRepository.saveAllAndFlush(batch));
                saved += batch.size();
                pending.clear();
            } catch (DataIntegrityViolationException exception) {
                int concurrentWinners = removeExisting(pending);
                if (concurrentWinners == 0) {
                    throw exception;
                }
                skipped += concurrentWinners;
            }
        }

        return new SnapshotResult(saved, skipped);
    }

    private int removeExisting(Set<SnapshotKey> pending) {
        if (pending.isEmpty()) {
            return 0;
        }
        Collection<Long> playerIds = pending.stream().map(SnapshotKey::playerId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Collection<Instant> fetchedAtValues = pending.stream().map(SnapshotKey::sourceFetchedAt)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<SnapshotKey> existing = new LinkedHashSet<>();
        for (PlayerStatsSnapshotRepository.ExistingSnapshotKey key
                : snapshotRepository.findExistingKeys(playerIds, fetchedAtValues)) {
            existing.add(new SnapshotKey(key.getPlayerId(), key.getSourceFetchedAt()));
        }
        int before = pending.size();
        pending.removeAll(existing);
        return before - pending.size();
    }

    private PlayerStatsSnapshot toSnapshot(PlayerStats stats, Instant capturedAt, UUID runId) {
        return PlayerStatsSnapshot.builder()
                .player(stats.getPlayer())
                .minutesPlayed(stats.getMinutesPlayed())
                .goals(stats.getGoals())
                .assists(stats.getAssists())
                .shots(stats.getShots())
                .keyPasses(stats.getKeyPasses())
                .tackles(stats.getTackles())
                .dribbles(stats.getDribbles())
                .yellowCards(stats.getYellowCards())
                .redCards(stats.getRedCards())
                .rating(stats.getRating())
                .sourceFetchedAt(stats.getFetchedAt())
                .capturedAt(capturedAt)
                .runId(runId)
                .build();
    }

    private StatsWindow toWindow(PlayerStatsSnapshot previous, PlayerStatsSnapshot current) {
        boolean seasonReset = previous.getMinutesPlayed() != null
                && current.getMinutesPlayed() != null
                && current.getMinutesPlayed() < previous.getMinutesPlayed();

        return new StatsWindow(
                previous.getId(),
                current.getId(),
                previous.getCapturedAt(),
                current.getCapturedAt(),
                seasonReset,
                counter(previous.getMinutesPlayed(), current.getMinutesPlayed(), seasonReset),
                counter(previous.getGoals(), current.getGoals(), seasonReset),
                counter(previous.getAssists(), current.getAssists(), seasonReset),
                counter(previous.getShots(), current.getShots(), seasonReset),
                counter(previous.getKeyPasses(), current.getKeyPasses(), seasonReset),
                counter(previous.getTackles(), current.getTackles(), seasonReset),
                counter(previous.getDribbles(), current.getDribbles(), seasonReset),
                counter(previous.getYellowCards(), current.getYellowCards(), seasonReset),
                counter(previous.getRedCards(), current.getRedCards(), seasonReset),
                rating(previous, current, seasonReset));
    }

    private Integer counter(Integer previous, Integer current, boolean seasonReset) {
        if (seasonReset) {
            return current;
        }
        if (previous == null || current == null) {
            return null;
        }
        return Math.max(0, current - previous);
    }

    private BigDecimal rating(PlayerStatsSnapshot previous,
                              PlayerStatsSnapshot current,
                              boolean seasonReset) {
        if (seasonReset) {
            if (current.getRating() == null || current.getMinutesPlayed() == null
                    || current.getMinutesPlayed() <= 0) {
                return null;
            }
            return clampRating(current.getRating());
        }
        if (previous.getRating() == null || current.getRating() == null
                || previous.getMinutesPlayed() == null || current.getMinutesPlayed() == null) {
            return null;
        }
        int addedMinutes = current.getMinutesPlayed() - previous.getMinutesPlayed();
        if (addedMinutes <= 0) {
            return null;
        }
        BigDecimal currentWeighted = current.getRating()
                .multiply(BigDecimal.valueOf(current.getMinutesPlayed()), MathContext.DECIMAL64);
        BigDecimal previousWeighted = previous.getRating()
                .multiply(BigDecimal.valueOf(previous.getMinutesPlayed()), MathContext.DECIMAL64);
        BigDecimal windowRating = currentWeighted.subtract(previousWeighted, MathContext.DECIMAL64)
                .divide(BigDecimal.valueOf(addedMinutes), MathContext.DECIMAL64);
        return clampRating(windowRating);
    }

    private BigDecimal clampRating(BigDecimal value) {
        return value.max(BigDecimal.ZERO).min(BigDecimal.TEN);
    }

    private record SnapshotKey(Long playerId, Instant sourceFetchedAt) {
        private SnapshotKey {
            Objects.requireNonNull(playerId, "playerId must not be null");
            Objects.requireNonNull(sourceFetchedAt, "sourceFetchedAt must not be null");
        }
    }
}
