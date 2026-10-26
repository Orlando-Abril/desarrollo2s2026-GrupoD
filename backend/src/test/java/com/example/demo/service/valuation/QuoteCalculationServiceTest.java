package com.example.demo.service.valuation;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.demo.adapter.footballdata.FootballDataAdapter;
import com.example.demo.adapter.whoscored.WhoScoredAdapter;
import com.example.demo.exception.RecalculationFailedException;
import com.example.demo.exception.RecalculationInProgressException;
import com.example.demo.exception.UnknownValuationStrategyException;
import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.PlayerQuote;
import com.example.demo.model.PlayerStats;
import com.example.demo.model.Position;
import com.example.demo.model.QuoteStatus;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.PlayerStatsRepository;
import com.example.demo.service.PlayerStatsService;
import com.example.demo.service.valuation.momentum.MomentumFixtures;
import com.example.demo.service.valuation.momentum.MomentumFormAlgorithm;
import com.example.demo.service.valuation.official.OfficialPerformanceAlgorithm;
import com.example.demo.service.valuation.strategy.StrategyType;
import com.example.demo.service.valuation.strategy.ValuationStrategyCatalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuoteCalculationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T18:00:00Z");
    private static final Instant BEFORE = Instant.parse("2026-10-16T18:00:00Z");
    private static final String CORRELATION_ID = "11111111-2222-3333-4444-555555555555";

    private final StatsSnapshotService snapshotService = mock(StatsSnapshotService.class);
    private final PlayerRepository playerRepository = mock(PlayerRepository.class);
    private final PlayerStatsRepository statsRepository = mock(PlayerStatsRepository.class);
    private final PlayerQuoteRepository quoteRepository = mock(PlayerQuoteRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ValuationAlgorithm official = mock(ValuationAlgorithm.class);
    private final Logger logger = (Logger) LoggerFactory.getLogger(QuoteCalculationService.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private final List<Player> players = new ArrayList<>();
    private final List<PlayerStats> stats = new ArrayList<>();
    private final List<PlayerQuote> previous = new ArrayList<>();
    private final Map<Long, QuoteResult> officialResults = new HashMap<>();
    private final List<PlayerQuote> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        MDC.clear();
        MDC.put(CorrelationIdFilter.MDC_KEY, CORRELATION_ID);
        appender.start();
        logger.addAppender(appender);

        when(official.type()).thenReturn(StrategyType.OFFICIAL);
        when(official.preparePopulation(any())).thenReturn(null);
        when(official.calculate(any(), any())).thenAnswer(invocation -> {
            ValuationInput input = invocation.getArgument(0);
            return officialResults.getOrDefault(input.playerId(), QuoteResult.noStats());
        });
        when(playerRepository.findAll()).thenReturn(players);
        when(playerRepository.findById(any())).thenAnswer(invocation -> players.stream()
                .filter(player -> player.getId().equals(invocation.getArgument(0)))
                .findFirst());
        when(statsRepository.findAll()).thenReturn(stats);
        when(quoteRepository.findLatestForAllPlayers(anyString())).thenAnswer(invocation -> previous.stream()
                .filter(quote -> quote.getStrategyCode().equals(invocation.getArgument(0)))
                .toList());
        when(quoteRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<PlayerQuote> quotes = invocation.getArgument(0);
            quotes.forEach(saved::add);
            return quotes;
        });
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        MDC.clear();
    }

    // US1: corrida

    @Test
    void calculatedQuotesShareRunIdInstantAndCorrelationId() {
        Player a = player(1, "1.0000");
        Player b = player(2, "1.0000");
        Player c = player(3, "1.0000");
        withStats(a, b, c);
        officialResults.put(1L, calculated("1.0960", "0.9600"));
        officialResults.put(2L, calculated("1.0120", "0.1200"));
        officialResults.put(3L, calculated("1.0540", "0.5400"));

        QuoteRunResult result = service().recalculate(Optional.empty());

        assertThat(saved).hasSize(3).allSatisfy(quote -> {
            assertThat(quote.getStrategyCode()).isEqualTo("OFFICIAL");
            assertThat(quote.getStrategyVersion()).isEqualTo(1);
            assertThat(quote.getStatus()).isEqualTo(QuoteStatus.CALCULATED);
            assertThat(quote.getRunId()).isEqualTo(result.runId());
            assertThat(quote.getCalculatedAt()).isEqualTo(NOW);
            assertThat(quote.getCorrelationId()).isEqualTo(CORRELATION_ID);
        });
        assertThat(quoteOf(1).getValue()).isEqualByComparingTo("1.0960");
        assertThat(quoteOf(1).getScore()).isEqualByComparingTo("0.9600");
        assertThat(result.calculatedAt()).isEqualTo(NOW);
        assertThat(result.calculated()).containsExactly(new StrategyRunCounts("OFFICIAL", 1, 3, 0, 0));
        assertThat(result.playersProcessed()).isEqualTo(3);
        assertThat(result.playersFailed()).isZero();

        InOrder order = inOrder(snapshotService, playerRepository);
        order.verify(snapshotService).snapshotAll(result.runId());
        order.verify(playerRepository).findAll();
    }

    @Test
    void noStatsKeepsThePreviousValueOfTheSameStrategy() {
        Player a = player(1, "1.0450");
        previous.add(previousQuote(a, "OFFICIAL", "1.0450"));

        service().recalculate(Optional.empty());

        assertThat(quoteOf(1).getStatus()).isEqualTo(QuoteStatus.NO_STATS);
        assertThat(quoteOf(1).getValue()).isEqualByComparingTo("1.0450");
        assertThat(quoteOf(1).getScore()).isNull();
    }

    @Test
    void noStatsWithoutPreviousQuoteUsesTheBaseValueWithScaleFour() {
        player(1, "1.0000");

        service().recalculate(Optional.empty());

        assertThat(quoteOf(1).getValue()).isEqualTo(new BigDecimal("1.0000"));
        assertThat(quoteOf(1).getScore()).isNull();
    }

    @Test
    void insufficientHistoryKeepsThePreviousValue() {
        Player a = player(1, "1.0200");
        previous.add(previousQuote(a, "OFFICIAL", "1.0200"));
        officialResults.put(1L, QuoteResult.insufficientHistory());

        QuoteRunResult result = service().recalculate(Optional.empty());

        assertThat(quoteOf(1).getStatus()).isEqualTo(QuoteStatus.INSUFFICIENT_HISTORY);
        assertThat(quoteOf(1).getValue()).isEqualByComparingTo("1.0200");
        assertThat(quoteOf(1).getScore()).isNull();
        assertThat(result.calculated()).containsExactly(new StrategyRunCounts("OFFICIAL", 1, 0, 0, 1));
    }

    @Test
    void algorithmReceivesOnlyTheLatestQuoteOfItsOwnStrategy() {
        Player a = player(1, "1.0450");
        withStats(a);
        PlayerQuote officialQuote = previousQuote(a, "OFFICIAL", "1.0450");
        previous.add(officialQuote);
        previous.add(PlayerQuote.builder().player(a).strategyCode("MOMENTUM").strategyVersion(1)
                .value(new BigDecimal("1.3000")).status(QuoteStatus.NO_STATS)
                .calculatedAt(NOW.minusSeconds(60)).runId(UUID.randomUUID()).correlationId("x").build());
        officialResults.put(1L, calculated("1.0500", "0.5000"));

        service().recalculate(Optional.empty());

        ArgumentCaptor<ValuationInput> input = ArgumentCaptor.forClass(ValuationInput.class);
        verify(official).calculate(input.capture(), any());
        assertThat(input.getValue().previousQuotes()).containsExactly(officialQuote);
        verify(quoteRepository).findLatestForAllPlayers("OFFICIAL");
        verify(quoteRepository, never()).findLatestForAllPlayers("MOMENTUM");
    }

    @Test
    void strategyWithoutAlgorithmIsSkipped() {
        player(1, "1.0000");

        QuoteRunResult result = service().recalculate(Optional.empty());

        assertThat(result.skipped()).containsExactly(new SkippedStrategy("MOMENTUM", 1, SkippedStrategy.NO_ALGORITHM));
        assertThat(saved).extracting(PlayerQuote::getStrategyCode).containsOnly("OFFICIAL");
    }

    @SuppressWarnings("unchecked")
    @Test
    void populationIsPreparedOncePerStrategyWithEveryPlayer() {
        Player a = player(1, "1.0000");
        player(2, "1.0000");
        withStats(a);

        service().recalculate(Optional.empty());

        ArgumentCaptor<Collection<PopulationEntry>> entries = ArgumentCaptor.forClass(Collection.class);
        verify(official, times(1)).preparePopulation(entries.capture());
        assertThat(entries.getValue()).extracting(PopulationEntry::playerId).containsExactly(1L, 2L);
        assertThat(entries.getValue()).extracting(entry -> entry.stats() != null).containsExactly(true, false);
    }

    @Test
    void snapshotFailureSavesNothing() {
        player(1, "1.0000");
        doThrowOnSnapshot(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service().recalculate(Optional.empty()))
                .isInstanceOf(RecalculationFailedException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        verify(quoteRepository, never()).saveAll(any());
        verify(playerRepository, never()).findAll();
    }

    @Test
    void loadFailureAfterTheSnapshotSavesNothing() {
        Player a = player(1, "1.0000");
        when(quoteRepository.findLatestForAllPlayers("OFFICIAL")).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> service().recalculate(Optional.empty()))
                .isInstanceOf(RecalculationFailedException.class);
        verify(quoteRepository, never()).saveAll(any());
        assertThat(a.getMarketValue()).isEqualByComparingTo("1.0000");
    }

    @Test
    void unknownStrategyFailsBeforeTheSnapshot() {
        QuoteCalculationService service = service();

        for (String code : List.of("FOO", "", "official", " official ")) {
            assertThatThrownBy(() -> service.recalculate(Optional.of(code)))
                    .as("código '%s'", code)
                    .isInstanceOf(UnknownValuationStrategyException.class);
        }
        verify(snapshotService, never()).snapshotAll(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void acceptedRunPublishesItsCompletionOnSuccessBeforeReleasingTheGuard() {
        player(1, "1.0000");
        QuoteCalculationService service = service();
        doAnswer(invocation -> {
            assertThatThrownBy(() -> service.recalculate(Optional.empty()))
                    .isInstanceOf(RecalculationInProgressException.class);
            return null;
        }).when(eventPublisher).publishEvent(any(QuoteRunCompletedEvent.class));

        QuoteRunResult result = service.recalculate(Optional.empty());

        ArgumentCaptor<QuoteRunCompletedEvent> event = ArgumentCaptor.forClass(QuoteRunCompletedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().runId()).isEqualTo(result.runId());
    }

    @Test
    void acceptedRunPublishesItsCompletionOnTerminalFailure() {
        doThrowOnSnapshot(new IllegalStateException("db down"));

        RecalculationFailedException failure = (RecalculationFailedException) org.assertj.core.api.Assertions
                .catchThrowable(() -> service().recalculate(Optional.empty()));

        ArgumentCaptor<QuoteRunCompletedEvent> event = ArgumentCaptor.forClass(QuoteRunCompletedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().runId()).isEqualTo(failure.runId());
    }

    @Test
    void missingCorrelationIdIsGeneratedAndRemovedAfterwards() {
        MDC.clear();
        player(1, "1.0000");

        service().recalculate(Optional.empty());

        assertThat(UUID.fromString(quoteOf(1).getCorrelationId())).isNotNull();
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void existingCorrelationIdIsUsedAndKept() {
        player(1, "1.0000");

        service().recalculate(Optional.empty());

        assertThat(quoteOf(1).getCorrelationId()).isEqualTo(CORRELATION_ID);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo(CORRELATION_ID);
    }

    @Test
    void emptyCatalogFinishesWithZeroCounts() {
        QuoteRunResult result = service().recalculate(Optional.empty());

        assertThat(result.playersProcessed()).isZero();
        assertThat(result.playersFailed()).isZero();
        assertThat(result.calculated()).containsExactly(new StrategyRunCounts("OFFICIAL", 1, 0, 0, 0));
        assertThat(result.marketValueUpdated()).isTrue();
    }

    @Test
    void serviceDoesNotDependOnExternalSources() {
        List<Class<?>> forbidden = List.of(WhoScoredAdapter.class, FootballDataAdapter.class, PlayerStatsService.class);

        for (Constructor<?> constructor : QuoteCalculationService.class.getDeclaredConstructors()) {
            assertThat(Arrays.asList(constructor.getParameterTypes())).doesNotContainAnyElementsOf(forbidden);
        }
    }

    @Test
    void requestedStrategyIsReportedAndNothingIsSkipped() {
        player(1, "1.0000");

        QuoteRunResult result = service().recalculate(Optional.of("OFFICIAL"));

        assertThat(result.requestedStrategy()).isEqualTo("OFFICIAL");
        assertThat(result.skipped()).isEmpty();
        assertThat(result.calculated()).extracting(StrategyRunCounts::code).containsExactly("OFFICIAL");
    }

    @Test
    void startAndFinishAreLogged() {
        player(1, "1.0000");

        QuoteRunResult result = service().recalculate(Optional.empty());

        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("quote_recalculation_started")
                        .contains("runId=" + result.runId()))
                .anySatisfy(message -> assertThat(message).contains("quote_recalculation_finished")
                        .contains("runId=" + result.runId()).contains("processed=1").contains("failed=0"));
        assertThat(appender.list).allSatisfy(event ->
                assertThat(event.getMDCPropertyMap()).containsEntry(CorrelationIdFilter.MDC_KEY, CORRELATION_ID));
    }

    // US2: precio de mercado

    @Test
    void marketValueFollowsTheActiveStrategyWithScaleFour() {
        Player a = player(1, "1.0000");
        Player b = player(2, "1.0000");
        withStats(a);
        previous.add(previousQuote(b, "OFFICIAL", "1.0450"));
        officialResults.put(1L, calculated("1.0437", "0.4370"));

        QuoteRunResult result = service().recalculate(Optional.empty());

        assertThat(a.getMarketValue()).isEqualByComparingTo("1.0437");
        assertThat(a.getMarketValue().scale()).isEqualTo(4);
        assertThat(b.getMarketValue()).isEqualByComparingTo("1.0450");
        assertThat(result.marketValueUpdated()).isTrue();
        assertThat(result.activeStrategy()).isEqualTo("OFFICIAL");
    }

    @Test
    void marketValueIsUntouchedWhenTheActiveStrategyIsNotCalculated() {
        Player a = player(1, "1.0000");
        withStats(a);
        officialResults.put(1L, calculated("1.0800", "0.8000"));

        QuoteRunResult result = service(new ValuationStrategyCatalog("MOMENTUM"), official)
                .recalculate(Optional.empty());

        assertThat(quoteOf(1).getValue()).isEqualByComparingTo("1.0800");
        assertThat(a.getMarketValue()).isEqualByComparingTo("1.0000");
        assertThat(result.marketValueUpdated()).isFalse();
        assertThat(result.activeStrategy()).isEqualTo("MOMENTUM");
    }

    @Test
    void requestingOnlyAStrategyWithoutAlgorithmChangesNothing() {
        Player a = player(1, "1.0000");

        QuoteRunResult result = service().recalculate(Optional.of("MOMENTUM"));

        assertThat(result.calculated()).isEmpty();
        assertThat(result.skipped()).extracting(SkippedStrategy::code).containsExactly("MOMENTUM");
        assertThat(result.marketValueUpdated()).isFalse();
        assertThat(saved).isEmpty();
        assertThat(a.getMarketValue()).isEqualByComparingTo("1.0000");
        verify(snapshotService).snapshotAll(any());
    }

    // US5: fallas por jugador y corrida única

    @Test
    void aFailingPlayerDoesNotAbortTheRun() {
        for (long id = 1; id <= 5; id++) {
            withStats(player(id, "1.0000"));
            officialResults.put(id, calculated("1.0500", "0.5000"));
        }
        doAnswer(invocation -> {
            Iterable<PlayerQuote> quotes = invocation.getArgument(0);
            if (quotes.iterator().next().getPlayer().getId() == 3L) {
                throw new IllegalStateException("constraint");
            }
            quotes.forEach(saved::add);
            return quotes;
        }).when(quoteRepository).saveAll(any());

        QuoteRunResult result = service().recalculate(Optional.empty());

        assertThat(result.playersProcessed()).isEqualTo(4);
        assertThat(result.playersFailed()).isEqualTo(1);
        assertThat(result.calculated()).containsExactly(new StrategyRunCounts("OFFICIAL", 1, 4, 0, 0));
        assertThat(players.get(2).getMarketValue()).isEqualByComparingTo("1.0000");
        assertThat(players).filteredOn(player -> player.getId() != 3L)
                .allSatisfy(player -> assertThat(player.getMarketValue()).isEqualByComparingTo("1.0500"));
        assertThat(appender.list).filteredOn(event -> event.getLevel() == Level.ERROR)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage())
                        .contains("quote_recalculation_player_failed")
                        .contains("runId=" + result.runId())
                        .contains("playerId=3")
                        .contains("errorType=IllegalStateException"));
    }

    @Test
    void aFailingSecondStrategyDiscardsTheFirstOneForThatPlayer() {
        Player a = player(1, "1.0000");
        Player b = player(2, "1.0000");
        withStats(a, b);
        officialResults.put(1L, calculated("1.0500", "0.5000"));
        officialResults.put(2L, calculated("1.0600", "0.6000"));
        ValuationAlgorithm momentum = mock(ValuationAlgorithm.class);
        when(momentum.type()).thenReturn(StrategyType.MOMENTUM);
        when(momentum.calculate(any(), any())).thenAnswer(invocation -> {
            ValuationInput input = invocation.getArgument(0);
            if (input.playerId() == 2L) {
                throw new IllegalArgumentException("bad input");
            }
            return QuoteResult.noStats();
        });

        QuoteRunResult result = service(new ValuationStrategyCatalog("OFFICIAL"), official, momentum)
                .recalculate(Optional.empty());

        assertThat(saved).extracting(quote -> quote.getPlayer().getId()).containsOnly(1L);
        assertThat(saved).extracting(PlayerQuote::getStrategyCode).containsExactly("OFFICIAL", "MOMENTUM");
        assertThat(b.getMarketValue()).isEqualByComparingTo("1.0000");
        assertThat(result.playersFailed()).isEqualTo(1);
        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("playerId=2").contains("strategy=MOMENTUM"));
    }

    @Test
    void everyPlayerFailingLeavesMarketValueNotUpdated() {
        for (long id = 1; id <= 3; id++) {
            withStats(player(id, "1.0000"));
            officialResults.put(id, calculated("1.0500", "0.5000"));
        }
        doThrow(new IllegalStateException("constraint")).when(quoteRepository).saveAll(any());

        QuoteRunResult result = service().recalculate(Optional.empty());

        assertThat(result.playersProcessed()).isZero();
        assertThat(result.playersFailed()).isEqualTo(3);
        assertThat(result.calculated()).containsExactly(new StrategyRunCounts("OFFICIAL", 1, 0, 0, 0));
        assertThat(result.marketValueUpdated()).isFalse();
        assertThat(players).allSatisfy(player -> assertThat(player.getMarketValue()).isEqualByComparingTo("1.0000"));
    }

    @Test
    void aSecondRunWhileOneIsInProgressIsRejected() throws Exception {
        player(1, "1.0000");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return new SnapshotResult(0, 0);
        }).when(snapshotService).snapshotAll(any());
        QuoteCalculationService service = service();

        // Cubre FR-036: la corrida se invoca sin HTTP con la misma guardia.
        CompletableFuture<QuoteRunResult> first = CompletableFuture.supplyAsync(() -> service.recalculate(Optional.empty()));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> service.recalculate(Optional.empty()))
                .isInstanceOf(RecalculationInProgressException.class);
        assertThatThrownBy(() -> service.recalculate(Optional.of("FOO")))
                .isInstanceOf(UnknownValuationStrategyException.class);

        release.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS).playersProcessed()).isEqualTo(1);
        verify(snapshotService, times(1)).snapshotAll(any());
        verify(eventPublisher, times(1)).publishEvent(any(QuoteRunCompletedEvent.class));
    }

    @Test
    void theGuardIsReleasedAfterAFailedRun() {
        player(1, "1.0000");
        QuoteCalculationService service = service();
        doThrowOnSnapshot(new IllegalStateException("db down"));
        assertThatThrownBy(() -> service.recalculate(Optional.empty())).isInstanceOf(RecalculationFailedException.class);

        doAnswer(invocation -> new SnapshotResult(0, 0)).when(snapshotService).snapshotAll(any());
        when(quoteRepository.findLatestForAllPlayers("OFFICIAL")).thenThrow(new IllegalStateException("boom"));
        assertThatThrownBy(() -> service.recalculate(Optional.empty())).isInstanceOf(RecalculationFailedException.class);

        doReturn(List.of()).when(quoteRepository).findLatestForAllPlayers("OFFICIAL");
        assertThat(service.recalculate(Optional.empty()).playersProcessed()).isEqualTo(1);
    }

    @Test
    void aFailedRunLogsItsErrorType() {
        doThrowOnSnapshot(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service().recalculate(Optional.empty())).isInstanceOf(RecalculationFailedException.class);

        assertThat(appender.list).filteredOn(event -> event.getLevel() == Level.ERROR)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage())
                        .contains("quote_recalculation_failed")
                        .contains("errorType=IllegalStateException"));
    }

    // Feature 019: MOMENTUM

    @Test
    void momentumIsCalculatedWithOfficialAndIsNoLongerSkipped() {
        Player a = player(1, "1.0000");
        Player b = player(2, "1.0000");
        withStats(a, b);
        officialResults.put(1L, calculated("1.0500", "0.5000"));
        officialResults.put(2L, calculated("1.0600", "0.6000"));
        when(snapshotService.recentWindows(anyLong(), eq(6))).thenReturn(MomentumFixtures.repeated(5,
                MomentumFixtures.idle()));

        QuoteRunResult result = momentumService(official).recalculate(Optional.empty());

        assertThat(result.skipped()).isEmpty();
        assertThat(saved).extracting(quote -> quote.getPlayer().getId() + ":" + quote.getStrategyCode())
                .containsExactlyInAnyOrder("1:OFFICIAL", "1:MOMENTUM", "2:OFFICIAL", "2:MOMENTUM");
        assertThat(saved).filteredOn(quote -> quote.getStrategyCode().equals("MOMENTUM")).allSatisfy(quote -> {
            assertThat(quote.getStatus()).isEqualTo(QuoteStatus.CALCULATED);
            assertThat(quote.getValue()).isEqualByComparingTo("0.9700");
        });
        assertThat(a.getMarketValue()).isEqualByComparingTo("1.0500");
    }

    @Test
    void onlyMomentumReceivesTheWindowsOfEachPlayer() {
        Player a = player(1, "1.0000");
        Player b = player(2, "1.0000");
        withStats(a, b);
        List<StatsWindow> windows = MomentumFixtures.repeated(5, MomentumFixtures.idle());
        when(snapshotService.recentWindows(anyLong(), anyInt())).thenReturn(windows);
        ValuationAlgorithm momentum = mock(ValuationAlgorithm.class);
        when(momentum.type()).thenReturn(StrategyType.MOMENTUM);
        when(momentum.calculate(any(), any())).thenReturn(QuoteResult.insufficientHistory());

        service(new ValuationStrategyCatalog("OFFICIAL"), official, momentum).recalculate(Optional.empty());

        verify(snapshotService).recentWindows(1L, 6);
        verify(snapshotService).recentWindows(2L, 6);
        verify(snapshotService, times(2)).recentWindows(anyLong(), anyInt());
        ArgumentCaptor<ValuationInput> momentumInputs = ArgumentCaptor.forClass(ValuationInput.class);
        verify(momentum, times(2)).calculate(momentumInputs.capture(), any());
        assertThat(momentumInputs.getAllValues()).allSatisfy(input -> assertThat(input.windows()).isEqualTo(windows));
        ArgumentCaptor<ValuationInput> officialInputs = ArgumentCaptor.forClass(ValuationInput.class);
        verify(official, times(2)).calculate(officialInputs.capture(), any());
        assertThat(officialInputs.getAllValues()).allSatisfy(input -> assertThat(input.windows()).isEmpty());
    }

    @Test
    void withoutWindowsMomentumKeepsItsValueAsInsufficientHistory() {
        Player a = player(1, "1.0000");
        player(2, "1.0000");
        withStats(a);
        previous.add(previousQuote(a, "MOMENTUM", "1.2000"));
        when(snapshotService.recentWindows(anyLong(), anyInt())).thenReturn(List.of());

        momentumService(new OfficialPerformanceAlgorithm(90)).recalculate(Optional.empty());

        assertThat(saved).filteredOn(quote -> quote.getStrategyCode().equals("MOMENTUM"))
                .extracting(quote -> quote.getPlayer().getId() + ":" + quote.getStatus() + ":"
                        + quote.getValue().toPlainString())
                .containsExactlyInAnyOrder("1:INSUFFICIENT_HISTORY:1.2000", "2:INSUFFICIENT_HISTORY:1.0000");
        assertThat(saved).filteredOn(quote -> quote.getStrategyCode().equals("OFFICIAL")
                        && quote.getPlayer().getId() == 2L)
                .singleElement().extracting(PlayerQuote::getStatus).isEqualTo(QuoteStatus.NO_STATS);
    }

    @Test
    void officialQuotesAreTheSameWithAndWithoutMomentum() {
        for (long id = 1; id <= 3; id++) {
            Player player = player(id, "1.0000");
            PlayerStats row = new PlayerStats();
            row.setPlayer(player);
            row.setMinutesPlayed(900);
            row.setGoals((int) id * 2);
            row.setAssists((int) id);
            row.setRating(new BigDecimal("6.50").add(BigDecimal.valueOf(id)));
            row.setFetchedAt(BEFORE);
            stats.add(row);
        }
        when(snapshotService.recentWindows(anyLong(), anyInt())).thenReturn(MomentumFixtures.repeated(5,
                MomentumFixtures.idle()));

        service(new ValuationStrategyCatalog("OFFICIAL"), new OfficialPerformanceAlgorithm(90))
                .recalculate(Optional.empty());
        List<String> alone = officialSummary();
        saved.clear();
        momentumService(new OfficialPerformanceAlgorithm(90)).recalculate(Optional.empty());

        assertThat(officialSummary()).hasSize(3).isEqualTo(alone);
    }

    @Test
    void aPlayerWhoseWindowsFailToLoadFailsAndTheRunContinues() {
        Player a = player(1, "1.0000");
        Player b = player(2, "1.0000");
        withStats(a, b);
        officialResults.put(1L, calculated("1.0500", "0.5000"));
        officialResults.put(2L, calculated("1.0600", "0.6000"));
        when(snapshotService.recentWindows(1L, 6)).thenReturn(List.of());
        when(snapshotService.recentWindows(2L, 6)).thenThrow(new IllegalStateException("db"));

        QuoteRunResult result = momentumService(official).recalculate(Optional.empty());

        assertThat(result.playersProcessed()).isEqualTo(1);
        assertThat(result.playersFailed()).isEqualTo(1);
        assertThat(saved).extracting(quote -> quote.getPlayer().getId()).containsOnly(1L);
    }

    @Test
    void withoutNewDataMomentumKeepsTheValue() {
        Player a = player(1, "1.0000");
        withStats(a);
        List<StatsWindow> windows = MomentumFixtures.repeated(5, MomentumFixtures.idle());
        previous.add(PlayerQuote.builder().player(a).strategyCode("MOMENTUM").strategyVersion(1)
                .value(new BigDecimal("1.2000")).score(new BigDecimal("0.4000")).status(QuoteStatus.CALCULATED)
                .calculatedAt(BEFORE).runId(windows.get(4).currentRunId()).correlationId("previous").build());
        when(snapshotService.recentWindows(anyLong(), anyInt())).thenReturn(windows);

        momentumService(official).recalculate(Optional.empty());

        assertThat(saved).filteredOn(quote -> quote.getStrategyCode().equals("MOMENTUM")).singleElement()
                .satisfies(quote -> {
                    assertThat(quote.getStatus()).isEqualTo(QuoteStatus.CALCULATED);
                    assertThat(quote.getValue()).isEqualByComparingTo("1.2000");
                });
    }

    private List<String> officialSummary() {
        return saved.stream()
                .filter(quote -> quote.getStrategyCode().equals("OFFICIAL"))
                .map(quote -> quote.getPlayer().getId() + ":" + quote.getStatus() + ":" + quote.getValue() + ":"
                        + quote.getScore())
                .sorted()
                .toList();
    }

    private QuoteCalculationService momentumService(ValuationAlgorithm officialAlgorithm) {
        return service(new ValuationStrategyCatalog("OFFICIAL"), officialAlgorithm, new MomentumFormAlgorithm());
    }

    private QuoteCalculationService service() {
        return service(new ValuationStrategyCatalog("OFFICIAL"), official);
    }

    private QuoteCalculationService service(ValuationStrategyCatalog catalog, ValuationAlgorithm... algorithms) {
        return new QuoteCalculationService(catalog, List.of(algorithms), snapshotService, playerRepository,
                statsRepository, quoteRepository, Clock.fixed(NOW, ZoneOffset.UTC),
                TransactionOperations.withoutTransaction(), transactionManager, eventPublisher);
    }

    private void doThrowOnSnapshot(RuntimeException exception) {
        doAnswer(invocation -> {
            throw exception;
        }).when(snapshotService).snapshotAll(any());
    }

    private Player player(long id, String marketValue) {
        Player player = Player.builder()
                .id(id)
                .fullName("Jugador " + id)
                .team("Arsenal FC")
                .league(League.PREMIER_LEAGUE)
                .positions(Set.of(Position.MIDFIELDER))
                .marketValue(new BigDecimal(marketValue))
                .build();
        players.add(player);
        return player;
    }

    private void withStats(Player... withStats) {
        for (Player player : withStats) {
            PlayerStats row = new PlayerStats();
            row.setPlayer(player);
            row.setMinutesPlayed(900);
            row.setFetchedAt(BEFORE);
            stats.add(row);
        }
    }

    private static PlayerQuote previousQuote(Player player, String strategy, String value) {
        return PlayerQuote.builder()
                .player(player)
                .strategyCode(strategy)
                .strategyVersion(1)
                .value(new BigDecimal(value))
                .score(new BigDecimal("0.5000"))
                .status(QuoteStatus.CALCULATED)
                .calculatedAt(BEFORE)
                .runId(UUID.randomUUID())
                .correlationId("previous")
                .build();
    }

    private static QuoteResult calculated(String value, String score) {
        return QuoteResult.calculated(new BigDecimal(value), new BigDecimal(score), Map.of());
    }

    private PlayerQuote quoteOf(long playerId) {
        return saved.stream().filter(quote -> quote.getPlayer().getId() == playerId).findFirst().orElseThrow();
    }
}
