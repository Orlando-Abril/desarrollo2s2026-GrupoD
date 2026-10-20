package com.example.demo.service.valuation;

import com.example.demo.model.Player;
import com.example.demo.model.PlayerStats;
import com.example.demo.model.PlayerStatsSnapshot;
import com.example.demo.repository.PlayerStatsRepository;
import com.example.demo.repository.PlayerStatsSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsSnapshotServiceTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-10-18T10:00:00Z");

    @Mock
    private PlayerStatsRepository statsRepository;

    @Mock
    private PlayerStatsSnapshotRepository snapshotRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private StatsSnapshotService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        service = new StatsSnapshotService(statsRepository, snapshotRepository,
                Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC), transactionManager);
    }

    @Test
    void rejectsNullRunIdBeforeReadingStats() {
        assertThatThrownBy(() -> service.snapshotAll(null)).isInstanceOf(NullPointerException.class);
        verify(statsRepository, never()).findAll();
    }

    @Test
    void retriesAUniqueRaceAndCountsTheConcurrentWinnerAsSkipped() {
        PlayerStats stats = stats();
        when(statsRepository.findAll()).thenReturn(List.of(stats));
        when(snapshotRepository.findExistingKeys(any(), any()))
                .thenReturn(List.of(), List.of(existingKey(stats.getPlayerId(), FETCHED_AT)));
        when(snapshotRepository.saveAllAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThat(service.snapshotAll(UUID.randomUUID())).isEqualTo(new SnapshotResult(0, 1));
    }

    @Test
    void propagatesAnIntegrityErrorWhenNoConcurrentKeyAppeared() {
        when(statsRepository.findAll()).thenReturn(List.of(stats()));
        when(snapshotRepository.findExistingKeys(any(), any())).thenReturn(List.of(), List.of());
        when(snapshotRepository.saveAllAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("not a duplicate"));

        assertThatThrownBy(() -> service.snapshotAll(UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("not a duplicate");
    }

    @Test
    void recentSnapshotsReversesOnlyTheLimitedDescendingSubset() {
        PlayerStatsSnapshot older = snapshot(1L, "2026-09-01T12:00:00Z", 100, "8.00").build();
        PlayerStatsSnapshot newer = snapshot(2L, "2026-09-08T12:00:00Z", 200, "7.00").build();
        when(snapshotRepository.findByPlayerIdOrderByCapturedAtDescIdDesc(any(), any()))
                .thenReturn(List.of(newer, older));

        assertThat(service.recentSnapshots(7L, 2)).containsExactly(older, newer);
        verify(snapshotRepository).findByPlayerIdOrderByCapturedAtDescIdDesc(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.argThat(pageable -> pageable.getPageSize() == 2));
    }

    @Test
    void recentSnapshotsReturnsEmptyAndRejectsInvalidLimit() {
        when(snapshotRepository.findByPlayerIdOrderByCapturedAtDescIdDesc(any(), any())).thenReturn(List.of());
        assertThat(service.recentSnapshots(7L, 3)).isEmpty();
        assertThatThrownBy(() -> service.recentSnapshots(7L, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("greater than zero");
    }

    @Test
    void oneSnapshotProducesNoWindowAndThreeProduceTwo() {
        PlayerStatsSnapshot first = snapshot(1L, "2026-09-01T12:00:00Z", 100, "8.00").build();
        PlayerStatsSnapshot second = snapshot(2L, "2026-09-08T12:00:00Z", 200, "7.00").build();
        PlayerStatsSnapshot third = snapshot(3L, "2026-09-15T12:00:00Z", 300, "7.00").build();
        when(snapshotRepository.findByPlayerIdOrderByCapturedAtDescIdDesc(any(), any()))
                .thenReturn(List.of(first), List.of(third, second, first));

        assertThat(service.recentWindows(7L, 1)).isEmpty();
        assertThat(service.recentWindows(7L, 3)).hasSize(2);
    }

    @Test
    void normalWindowCalculatesEveryCounterAndWeightedRatingThatCanFall() {
        PlayerStatsSnapshot previous = snapshot(1L, "2026-09-01T12:00:00Z", 100, "8.00")
                .goals(5).assists(3).shots(20).keyPasses(10).tackles(8).dribbles(6)
                .yellowCards(2).redCards(1).build();
        PlayerStatsSnapshot current = snapshot(2L, "2026-09-08T12:00:00Z", 200, "7.00")
                .goals(7).assists(4).shots(27).keyPasses(14).tackles(11).dribbles(10)
                .yellowCards(3).redCards(1).build();
        mockRecent(previous, current);

        StatsWindow window = service.recentWindows(7L, 2).get(0);

        assertThat(window.previousSnapshotId()).isEqualTo(1L);
        assertThat(window.currentSnapshotId()).isEqualTo(2L);
        assertThat(window.seasonReset()).isFalse();
        assertThat(window.minutesPlayed()).isEqualTo(100);
        assertThat(window.goals()).isEqualTo(2);
        assertThat(window.assists()).isEqualTo(1);
        assertThat(window.shots()).isEqualTo(7);
        assertThat(window.keyPasses()).isEqualTo(4);
        assertThat(window.tackles()).isEqualTo(3);
        assertThat(window.dribbles()).isEqualTo(4);
        assertThat(window.yellowCards()).isEqualTo(1);
        assertThat(window.redCards()).isZero();
        assertThat(window.rating()).isEqualByComparingTo("6.00");
    }

    @Test
    void nullsStayNullAndNegativeCountersBecomeZeroIndependently() {
        PlayerStatsSnapshot previous = snapshot(1L, "2026-09-01T12:00:00Z", 100, "7.00")
                .goals(5).assists(null).shots(20).build();
        PlayerStatsSnapshot current = snapshot(2L, "2026-09-08T12:00:00Z", 150, "7.00")
                .goals(4).assists(4).shots(null).build();
        mockRecent(previous, current);

        StatsWindow window = service.recentWindows(7L, 2).get(0);
        assertThat(window.goals()).isZero();
        assertThat(window.assists()).isNull();
        assertThat(window.shots()).isNull();
    }

    @Test
    void normalRatingNeedsEveryEndpointAndPositiveAddedMinutesAndIsClamped() {
        PlayerStatsSnapshot previous = snapshot(1L, "2026-09-01T12:00:00Z", 100, "2.00").build();
        PlayerStatsSnapshot high = snapshot(2L, "2026-09-08T12:00:00Z", 110, "10.00").build();
        mockRecent(previous, high);
        assertThat(service.recentWindows(7L, 2).get(0).rating()).isEqualByComparingTo("10");

        PlayerStatsSnapshot lowPrevious = snapshot(5L, "2026-08-01T12:00:00Z", 100, "10.00").build();
        PlayerStatsSnapshot lowCurrent = snapshot(6L, "2026-08-08T12:00:00Z", 110, "2.00").build();
        mockRecent(lowPrevious, lowCurrent);
        assertThat(service.recentWindows(7L, 2).get(0).rating()).isEqualByComparingTo("0");

        PlayerStatsSnapshot missing = snapshot(3L, "2026-09-15T12:00:00Z", 120, null).build();
        mockRecent(high, missing);
        assertThat(service.recentWindows(7L, 2).get(0).rating()).isNull();

        PlayerStatsSnapshot sameMinutes = snapshot(4L, "2026-09-22T12:00:00Z", 120, "8.00").build();
        mockRecent(missing, sameMinutes);
        assertThat(service.recentWindows(7L, 2).get(0).rating()).isNull();
    }

    @Test
    void seasonResetCopiesNewTotalsAndClampsItsRating() {
        PlayerStatsSnapshot previous = snapshot(1L, "2026-05-01T12:00:00Z", 1800, "7.00")
                .goals(15).assists(9).shots(100).keyPasses(50).tackles(30).dribbles(40)
                .yellowCards(5).redCards(1).build();
        PlayerStatsSnapshot current = snapshot(2L, "2026-09-01T12:00:00Z", 90, "11.00")
                .goals(1).assists(null).shots(4).keyPasses(2).tackles(1).dribbles(3)
                .yellowCards(0).redCards(0).build();
        mockRecent(previous, current);

        StatsWindow window = service.recentWindows(7L, 2).get(0);
        assertThat(window.seasonReset()).isTrue();
        assertThat(window.minutesPlayed()).isEqualTo(90);
        assertThat(window.goals()).isEqualTo(1);
        assertThat(window.assists()).isNull();
        assertThat(window.shots()).isEqualTo(4);
        assertThat(window.keyPasses()).isEqualTo(2);
        assertThat(window.tackles()).isEqualTo(1);
        assertThat(window.dribbles()).isEqualTo(3);
        assertThat(window.yellowCards()).isZero();
        assertThat(window.redCards()).isZero();
        assertThat(window.rating()).isEqualByComparingTo("10");
    }

    @Test
    void resetWithZeroMinutesHasNoRatingAndMissingMinutesDoesNotTriggerReset() {
        PlayerStatsSnapshot previous = snapshot(1L, "2026-05-01T12:00:00Z", 1800, "7.00").build();
        PlayerStatsSnapshot zero = snapshot(2L, "2026-09-01T12:00:00Z", 0, "8.00").build();
        mockRecent(previous, zero);
        StatsWindow reset = service.recentWindows(7L, 2).get(0);
        assertThat(reset.seasonReset()).isTrue();
        assertThat(reset.rating()).isNull();

        PlayerStatsSnapshot missingMinutes = snapshot(3L, "2026-09-08T12:00:00Z", null, "8.00").build();
        mockRecent(zero, missingMinutes);
        StatsWindow normal = service.recentWindows(7L, 2).get(0);
        assertThat(normal.seasonReset()).isFalse();
        assertThat(normal.minutesPlayed()).isNull();
        assertThat(normal.rating()).isNull();
    }

    private PlayerStats stats() {
        Player player = Player.builder().externalId("3180").fullName("Bukayo Saka").build();
        ReflectionTestUtils.setField(player, "id", 7L);
        PlayerStats stats = new PlayerStats(player);
        ReflectionTestUtils.setField(stats, "playerId", 7L);
        stats.setFetchedAt(FETCHED_AT);
        return stats;
    }

    private PlayerStatsSnapshotRepository.ExistingSnapshotKey existingKey(Long playerId, Instant fetchedAt) {
        return new PlayerStatsSnapshotRepository.ExistingSnapshotKey() {
            @Override
            public Long getPlayerId() {
                return playerId;
            }

            @Override
            public Instant getSourceFetchedAt() {
                return fetchedAt;
            }
        };
    }

    private PlayerStatsSnapshot.PlayerStatsSnapshotBuilder snapshot(Long id, String capturedAt,
                                                                     Integer minutes, String rating) {
        return PlayerStatsSnapshot.builder()
                .id(id)
                .player(Player.builder().fullName("Player").build())
                .sourceFetchedAt(Instant.parse(capturedAt).minusSeconds(60))
                .capturedAt(Instant.parse(capturedAt))
                .runId(UUID.randomUUID())
                .minutesPlayed(minutes)
                .rating(rating == null ? null : new BigDecimal(rating));
    }

    private void mockRecent(PlayerStatsSnapshot previous, PlayerStatsSnapshot current) {
        when(snapshotRepository.findByPlayerIdOrderByCapturedAtDescIdDesc(any(), any()))
                .thenReturn(List.of(current, previous));
    }
}
