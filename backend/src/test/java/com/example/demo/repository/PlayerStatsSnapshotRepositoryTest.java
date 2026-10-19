package com.example.demo.repository;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.PlayerStatsSnapshot;
import com.example.demo.support.UnitTestProfile;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@UnitTestProfile
class PlayerStatsSnapshotRepositoryTest {

    private static final UUID RUN_ID = UUID.fromString("7c1d2a8e-0b7f-4f0e-9a51-2f3c4d5e6f70");

    @Autowired
    private PlayerStatsSnapshotRepository snapshotRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Player player;

    @BeforeEach
    void setUp() {
        player = playerRepository.save(Player.builder().externalId("3180").fullName("Bukayo Saka")
                .team("Arsenal FC").league(League.PREMIER_LEAGUE)
                .marketValue(new BigDecimal("1.00")).build());
    }

    @Test
    void storesAndReadsEveryFieldIncludingNullMetrics() {
        PlayerStatsSnapshot saved = snapshotRepository.save(snapshot("2026-09-25T13:30:00Z", "2026-09-26T10:00:00Z")
                .minutesPlayed(450).goals(3).assists(1).shots(19).keyPasses(13).tackles(5)
                .dribbles(12).yellowCards(null).redCards(0).rating(new BigDecimal("7.39")).build());
        entityManager.flush();
        entityManager.clear();

        PlayerStatsSnapshot stored = snapshotRepository.findById(saved.getId()).orElseThrow();
        assertThat(stored.getPlayer().getId()).isEqualTo(player.getId());
        assertThat(stored.getMinutesPlayed()).isEqualTo(450);
        assertThat(stored.getGoals()).isEqualTo(3);
        assertThat(stored.getAssists()).isEqualTo(1);
        assertThat(stored.getShots()).isEqualTo(19);
        assertThat(stored.getKeyPasses()).isEqualTo(13);
        assertThat(stored.getTackles()).isEqualTo(5);
        assertThat(stored.getDribbles()).isEqualTo(12);
        assertThat(stored.getYellowCards()).isNull();
        assertThat(stored.getRedCards()).isZero();
        assertThat(stored.getRating()).isEqualByComparingTo("7.39");
        assertThat(stored.getSourceFetchedAt()).isEqualTo(Instant.parse("2026-09-25T13:30:00Z"));
        assertThat(stored.getCapturedAt()).isEqualTo(Instant.parse("2026-09-26T10:00:00Z"));
        assertThat(stored.getRunId()).isEqualTo(RUN_ID);
    }

    @Test
    void rejectsSamePlayerAndSourceFetchedAtTwice() {
        snapshotRepository.saveAllAndFlush(java.util.List.of(
                snapshot("2026-09-25T13:30:00Z", "2026-09-26T10:00:00Z").build()));

        PlayerStatsSnapshot duplicate = snapshot("2026-09-25T13:30:00Z", "2026-09-27T10:00:00Z")
                .runId(UUID.randomUUID()).build();

        assertThatThrownBy(() -> snapshotRepository.saveAllAndFlush(java.util.List.of(duplicate)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void repositoryAndEntityExposeAppendOnlyIntent() {
        assertThat(PlayerStatsSnapshot.class).hasAnnotation(Immutable.class);
        assertThat(Arrays.stream(PlayerStatsSnapshotRepository.class.getMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("delete"));
    }

    @Test
    void selectsOnlyTheLatestSnapshotsOfOnePlayerWithStableTieBreaking() {
        Player other = playerRepository.save(Player.builder().externalId("2048").fullName("Declan Rice")
                .team("Arsenal FC").league(League.PREMIER_LEAGUE)
                .marketValue(new BigDecimal("1.00")).build());
        PlayerStatsSnapshot oldest = snapshotRepository.save(snapshot(
                "2026-09-01T10:00:00Z", "2026-09-01T12:00:00Z").build());
        PlayerStatsSnapshot tiedFirst = snapshotRepository.save(snapshot(
                "2026-09-08T10:00:00Z", "2026-09-08T12:00:00Z").build());
        PlayerStatsSnapshot tiedSecond = snapshotRepository.save(snapshot(
                "2026-09-09T10:00:00Z", "2026-09-08T12:00:00Z").build());
        snapshotRepository.save(PlayerStatsSnapshot.builder().player(other)
                .sourceFetchedAt(Instant.parse("2026-09-10T10:00:00Z"))
                .capturedAt(Instant.parse("2026-09-10T12:00:00Z")).runId(RUN_ID).build());
        entityManager.flush();

        List<PlayerStatsSnapshot> latest = snapshotRepository.findByPlayerIdOrderByCapturedAtDescIdDesc(
                player.getId(), PageRequest.of(0, 2));

        assertThat(latest).extracting(PlayerStatsSnapshot::getId)
                .containsExactly(tiedSecond.getId(), tiedFirst.getId())
                .doesNotContain(oldest.getId());
        assertThat(snapshotRepository.findByPlayerIdOrderByCapturedAtDescIdDesc(
                player.getId(), PageRequest.of(0, 10))).hasSize(3);
    }

    private PlayerStatsSnapshot.PlayerStatsSnapshotBuilder snapshot(String sourceFetchedAt, String capturedAt) {
        return PlayerStatsSnapshot.builder()
                .player(player)
                .sourceFetchedAt(Instant.parse(sourceFetchedAt))
                .capturedAt(Instant.parse(capturedAt))
                .runId(RUN_ID);
    }
}
