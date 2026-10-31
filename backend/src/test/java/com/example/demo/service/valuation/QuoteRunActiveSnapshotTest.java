package com.example.demo.service.valuation;

import com.example.demo.model.ActiveStrategySetting;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import com.example.demo.model.market.AuditActorType;
import com.example.demo.repository.ActiveStrategySettingRepository;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.PlayerStatsRepository;
import com.example.demo.service.valuation.strategy.StrategyType;
import com.example.demo.service.valuation.strategy.ValuationStrategyCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Spec 031, FR-024 y research R3/R4: la corrida usa la activa leída al empezar y registra la de mercado. */
class QuoteRunActiveSnapshotTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    private final ActiveStrategySettingRepository settings = mock(ActiveStrategySettingRepository.class);
    private final PlayerRepository playerRepository = mock(PlayerRepository.class);
    private final PlayerStatsRepository statsRepository = mock(PlayerStatsRepository.class);
    private final PlayerQuoteRepository quoteRepository = mock(PlayerQuoteRepository.class);
    private final StatsSnapshotService snapshotService = mock(StatsSnapshotService.class);
    private final ValuationAlgorithm official = mock(ValuationAlgorithm.class);
    private final ValuationAlgorithm momentum = mock(ValuationAlgorithm.class);
    private final AtomicReference<String> storedActive = new AtomicReference<>("OFFICIAL");
    private final List<Player> players = new ArrayList<>();
    private final List<Recorded> recorded = new ArrayList<>();

    private record Recorded(String code, Instant appliedAt, UUID runId) {
    }

    @BeforeEach
    void setUp() {
        when(settings.findById(ActiveStrategySetting.SINGLETON_ID)).thenAnswer(invocation -> {
            ActiveStrategySetting setting = ActiveStrategySetting.empty();
            setting.changeActive(storedActive.get(), AuditActorType.SYSTEM, null, NOW);
            return Optional.of(setting);
        });
        when(official.type()).thenReturn(StrategyType.OFFICIAL);
        when(momentum.type()).thenReturn(StrategyType.MOMENTUM);
        when(official.calculate(any(), any())).thenReturn(
                QuoteResult.calculated(new BigDecimal("2.0000"), new BigDecimal("1.0000"), Map.of()));
        when(momentum.calculate(any(), any())).thenReturn(
                QuoteResult.calculated(new BigDecimal("3.0000"), new BigDecimal("1.0000"), Map.of()));
        when(snapshotService.recentWindows(anyLong(), any(Integer.class))).thenReturn(List.of());
        when(playerRepository.findAll()).thenReturn(players);
        when(statsRepository.findAll()).thenReturn(List.of());
        when(quoteRepository.findLatestForAllPlayers(anyString())).thenReturn(List.of());
        when(quoteRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        for (long id = 1; id <= 3; id++) {
            players.add(Player.builder().id(id).fullName("Jugador " + id).team("Club").league(League.LA_LIGA)
                    .positions(Set.of(Position.FORWARD)).marketValue(new BigDecimal("1.0000")).build());
        }
    }

    @Test
    void aChangeDuringTheRunDoesNotMixStrategiesAndAppliesToTheNextRun() {
        when(playerRepository.findById(any())).thenAnswer(invocation -> {
            storedActive.set("MOMENTUM");
            return players.stream().filter(p -> p.getId().equals(invocation.getArgument(0))).findFirst();
        });
        QuoteCalculationService service = service((code, at, runId) -> recorded.add(new Recorded(code, at, runId)));

        QuoteRunResult first = service.recalculate(Optional.empty());

        assertThat(players).extracting(Player::getMarketValue).containsOnly(new BigDecimal("2.0000"));
        assertThat(first.activeStrategy()).isEqualTo("OFFICIAL");
        assertThat(recorded).containsExactly(new Recorded("OFFICIAL", NOW, first.runId()));

        QuoteRunResult second = service.recalculate(Optional.empty());

        assertThat(players).extracting(Player::getMarketValue).containsOnly(new BigDecimal("3.0000"));
        assertThat(second.activeStrategy()).isEqualTo("MOMENTUM");
        assertThat(recorded).last().isEqualTo(new Recorded("MOMENTUM", NOW, second.runId()));
    }

    @Test
    void aFailureRecordingTheMarketStrategyDoesNotFailTheRun() {
        when(playerRepository.findById(any())).thenAnswer(invocation ->
                players.stream().filter(p -> p.getId().equals(invocation.getArgument(0))).findFirst());
        QuoteCalculationService service = service((code, at, runId) -> {
            throw new IllegalStateException("audit down");
        });

        QuoteRunResult result = service.recalculate(Optional.empty());

        assertThat(result.marketValueUpdated()).isTrue();
        assertThat(result.playersProcessed()).isEqualTo(3);
    }

    @Test
    void aRunThatDoesNotUpdatePricesRecordsNothing() {
        when(playerRepository.findById(any())).thenAnswer(invocation ->
                players.stream().filter(p -> p.getId().equals(invocation.getArgument(0))).findFirst());
        QuoteCalculationService service = service((code, at, runId) -> recorded.add(new Recorded(code, at, runId)));

        QuoteRunResult result = service.recalculate(Optional.of("MOMENTUM"));

        assertThat(result.marketValueUpdated()).isFalse();
        assertThat(recorded).isEmpty();
        assertThat(players).extracting(Player::getMarketValue).containsOnly(new BigDecimal("1.0000"));
    }

    private QuoteCalculationService service(MarketStrategyRecorder recorder) {
        return new QuoteCalculationService(new ValuationStrategyCatalog("OFFICIAL", settings),
                List.of(official, momentum),
                new QuoteRunData(snapshotService, playerRepository, statsRepository, quoteRepository),
                Clock.fixed(NOW, ZoneOffset.UTC), mock(PlatformTransactionManager.class),
                mock(ApplicationEventPublisher.class), recorder);
    }
}
