package com.example.demo.e2e;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.PlayerStats;
import com.example.demo.model.PlayerStatsSnapshot;
import com.example.demo.repository.PlayerStatsRepository;
import com.example.demo.repository.PlayerStatsSnapshotRepository;
import com.example.demo.service.valuation.SnapshotResult;
import com.example.demo.service.valuation.StatsSnapshotService;
import com.example.demo.support.E2EFixtures;
import com.example.demo.support.E2ETest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contrato de historial, esquema y concurrencia verificado contra PostgreSQL real. */
@E2ETest
class PlayerStatsSnapshotE2ETest {

    @Autowired
    private E2EFixtures fixtures;

    @Autowired
    private PlayerStatsRepository statsRepository;

    @Autowired
    private PlayerStatsSnapshotRepository snapshotRepository;

    @Autowired
    private StatsSnapshotService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Player player;
    private Player other;

    @BeforeEach
    void setUp() {
        E2EFixtures.FixtureSet players = fixtures.createPlayersFromFiveLeagues();
        player = players.forLeague(League.PREMIER_LEAGUE);
        other = players.forLeague(League.LA_LIGA);
    }

    @Test
    void latestNIsAscendingAndIsolatedAndPostgresHasTheHistoryIndex() {
        snapshot(player, "2026-09-01T10:00:00Z", "2026-09-01T12:00:00Z");
        snapshot(player, "2026-09-08T10:00:00Z", "2026-09-08T12:00:00Z");
        PlayerStatsSnapshot third = snapshot(player, "2026-09-15T10:00:00Z", "2026-09-15T12:00:00Z");
        PlayerStatsSnapshot tieFirst = snapshot(player, "2026-09-16T10:00:00Z", "2026-09-22T12:00:00Z");
        PlayerStatsSnapshot tieSecond = snapshot(player, "2026-09-17T10:00:00Z", "2026-09-22T12:00:00Z");
        snapshot(other, "2026-09-30T10:00:00Z", "2026-09-30T12:00:00Z");

        assertThat(service.recentSnapshots(player.getId(), 3))
                .extracting(PlayerStatsSnapshot::getId)
                .containsExactly(third.getId(), tieFirst.getId(), tieSecond.getId());
        assertThat(service.recentSnapshots(player.getId(), 3))
                .allMatch(snapshot -> snapshot.getPlayer().getId().equals(player.getId()));

        List<String> indexes = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where tablename = 'player_stats_snapshots'", String.class);
        assertThat(indexes).contains("idx_player_stats_snapshots_player_captured");
    }

    @Test
    void postgresRejectsTheLogicalKeyTwice() {
        snapshot(player, "2026-09-01T10:00:00Z", "2026-09-01T12:00:00Z");

        assertThatThrownBy(() -> snapshot(player,
                "2026-09-01T10:00:00Z", "2026-09-02T12:00:00Z"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void twoSynchronizedCapturesLeaveOneRowAndComplementaryResults() throws Exception {
        PlayerStats stats = new PlayerStats(player);
        stats.setMinutesPlayed(90);
        stats.setGoals(1);
        stats.setDribbles(3);
        stats.setFetchedAt(Instant.parse("2026-09-01T10:00:00Z"));
        statsRepository.saveAndFlush(stats);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<SnapshotResult> first = executor.submit(() -> captureWhenReleased(ready, start));
            Future<SnapshotResult> second = executor.submit(() -> captureWhenReleased(ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(new SnapshotResult(1, 0), new SnapshotResult(0, 1));
        } finally {
            executor.shutdownNow();
        }

        assertThat(snapshotRepository.countByPlayerIdAndSourceFetchedAt(
                player.getId(), Instant.parse("2026-09-01T10:00:00Z"))).isEqualTo(1);
    }

    private SnapshotResult captureWhenReleased(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("capture start was not released");
        }
        return service.snapshotAll(UUID.randomUUID());
    }

    private PlayerStatsSnapshot snapshot(Player target, String sourceFetchedAt, String capturedAt) {
        return snapshotRepository.saveAllAndFlush(List.of(PlayerStatsSnapshot.builder()
                .player(target)
                .sourceFetchedAt(Instant.parse(sourceFetchedAt))
                .capturedAt(Instant.parse(capturedAt))
                .runId(UUID.randomUUID())
                .build())).get(0);
    }
}
