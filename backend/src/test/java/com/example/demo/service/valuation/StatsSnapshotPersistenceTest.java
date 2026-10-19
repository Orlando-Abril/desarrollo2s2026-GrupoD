package com.example.demo.service.valuation;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.PlayerStats;
import com.example.demo.model.PlayerStatsSnapshot;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.PlayerStatsRepository;
import com.example.demo.repository.PlayerStatsSnapshotRepository;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@UnitTestProfile
@Import({StatsSnapshotService.class, StatsSnapshotPersistenceTest.FixedClockConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StatsSnapshotPersistenceTest {

    private static final Instant CAPTURED_AT = Instant.parse("2026-10-19T12:00:00Z");

    @Autowired
    private StatsSnapshotService service;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerStatsRepository statsRepository;

    @Autowired
    private PlayerStatsSnapshotRepository snapshotRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("delete from player_stats_snapshots");
        jdbcTemplate.update("delete from player_stats");
        jdbcTemplate.update("delete from players");
    }

    @Test
    void emptyCaptureReturnsZeros() {
        assertThat(service.snapshotAll(UUID.randomUUID())).isEqualTo(new SnapshotResult(0, 0));
    }

    @Test
    void capturesAllStatsWithOneInstantAndSkipsThemOnTheSecondRun() {
        Player first = player("3180", "Bukayo Saka");
        Player second = player("2048", "Declan Rice");
        statsRepository.saveAndFlush(stats(first, "2026-10-18T10:00:00Z", 450, 12));
        statsRepository.saveAndFlush(stats(second, "2026-10-18T10:00:00Z", null, null));

        assertThat(service.snapshotAll(UUID.randomUUID())).isEqualTo(new SnapshotResult(2, 0));
        assertThat(service.snapshotAll(UUID.randomUUID())).isEqualTo(new SnapshotResult(0, 2));

        List<PlayerStatsSnapshot> snapshots = jdbcTemplate.query(
                "select id from player_stats_snapshots order by id",
                (rs, rowNum) -> snapshotRepository.findById(rs.getLong(1)).orElseThrow());
        assertThat(snapshots).hasSize(2).allSatisfy(snapshot -> assertThat(snapshot.getCapturedAt()).isEqualTo(CAPTURED_AT));
        assertThat(snapshots).extracting(PlayerStatsSnapshot::getDribbles).containsExactlyInAnyOrder(12, null);
    }

    @Test
    void olderDistinctSourceIsAppendedWithoutChangingTheNewerSnapshot() {
        Player player = player("3180", "Bukayo Saka");
        PlayerStats current = stats(player, "2026-10-18T10:00:00Z", 450, 12);
        statsRepository.saveAndFlush(current);
        service.snapshotAll(UUID.randomUUID());

        current.setFetchedAt(Instant.parse("2026-10-10T10:00:00Z"));
        current.setGoals(1);
        statsRepository.saveAndFlush(current);

        assertThat(service.snapshotAll(UUID.randomUUID())).isEqualTo(new SnapshotResult(1, 0));
        assertThat(snapshotRepository.findByPlayerIdOrderByCapturedAtDescIdDesc(
                player.getId(), org.springframework.data.domain.PageRequest.of(0, 10)))
                .hasSize(2)
                .extracting(PlayerStatsSnapshot::getSourceFetchedAt)
                .containsExactlyInAnyOrder(Instant.parse("2026-10-18T10:00:00Z"),
                        Instant.parse("2026-10-10T10:00:00Z"));
    }

    private Player player(String externalId, String name) {
        return playerRepository.saveAndFlush(Player.builder().externalId(externalId).fullName(name)
                .team("Arsenal FC").league(League.PREMIER_LEAGUE)
                .marketValue(new BigDecimal("1.00")).build());
    }

    private PlayerStats stats(Player player, String fetchedAt, Integer minutes, Integer dribbles) {
        PlayerStats stats = new PlayerStats(player);
        stats.setMinutesPlayed(minutes);
        stats.setGoals(3);
        stats.setAssists(1);
        stats.setShots(19);
        stats.setKeyPasses(13);
        stats.setTackles(5);
        stats.setDribbles(dribbles);
        stats.setYellowCards(null);
        stats.setRedCards(0);
        stats.setRating(minutes == null ? null : new BigDecimal("7.39"));
        stats.setFetchedAt(Instant.parse(fetchedAt));
        return stats;
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        Clock clock() {
            return Clock.fixed(CAPTURED_AT, ZoneOffset.UTC);
        }
    }
}
