package com.example.demo.scheduler;

import com.example.demo.config.QuoteSchedulingConfig;
import com.example.demo.config.SchedulingConfig;
import com.example.demo.exception.RecalculationInProgressException;
import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.service.PlayerStatsService;
import com.example.demo.service.valuation.QuoteCalculationService;
import com.example.demo.service.valuation.QuoteRunResult;
import com.example.demo.service.valuation.SkippedStrategy;
import com.example.demo.service.valuation.StrategyRunCounts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class QuoteRecalculationSchedulerTest {

    private final QuoteCalculationService calculationService = mock(QuoteCalculationService.class);
    private final PlayerStatsService playerStatsService = mock(PlayerStatsService.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(QuoteCalculationService.class, () -> calculationService);

    @AfterEach
    void clearMdc() {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }

    @Test
    void scheduledMetadataUsesConfigurableCronAndZoneWithWeeklyDefaults() throws Exception {
        Method run = QuoteRecalculationScheduler.class.getDeclaredMethod("run");
        Scheduled scheduled = run.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${valuation.schedule.cron:0 0 6 * * MON}");
        assertThat(scheduled.zone()).isEqualTo("${valuation.schedule.zone:UTC}");
    }

    @Test
    void delegatesOnceForAllStrategies() {
        when(calculationService.recalculate(Optional.empty())).thenReturn(successResult());

        new QuoteRecalculationScheduler(calculationService).run();

        verify(calculationService, times(1)).recalculate(Optional.empty());
    }

    @Test
    void acceptsCustomValidCronAndZoneWithoutRunningOnStartup() {
        quoteContextRunner()
                .withPropertyValues(
                        "valuation.schedule.enabled=true",
                        "valuation.schedule.cron=0 30 7 * * TUE",
                        "valuation.schedule.zone=America/Argentina/Buenos_Aires")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(QuoteSchedulingConfig.class);
                    assertThat(context).hasSingleBean(QuoteRecalculationScheduler.class);
                    verify(calculationService, never()).recalculate(Optional.empty());
                });
    }

    @Test
    void invalidCronFailsContextWithoutFallback() {
        quoteContextRunner()
                .withPropertyValues(
                        "valuation.schedule.enabled=true",
                        "valuation.schedule.cron=not-a-cron")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void invalidZoneFailsContextWithoutFallback() {
        quoteContextRunner()
                .withPropertyValues(
                        "valuation.schedule.enabled=true",
                        "valuation.schedule.zone=Invalid/Zone")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void neitherSchedulerExistsWhenBothFeaturesAreDisabled() {
        allSchedulersContextRunner()
                .withPropertyValues(
                        "valuation.schedule.enabled=false",
                        "whoscored.sync.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(QuoteSchedulingConfig.class);
                    assertThat(context).doesNotHaveBean(QuoteRecalculationScheduler.class);
                    assertThat(context).doesNotHaveBean(SchedulingConfig.class);
                    assertThat(context).doesNotHaveBean(PlayerStatsScheduler.class);
                    assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
                    verifyNoInteractions(calculationService, playerStatsService);
                });
    }

    @Test
    void quoteSchedulerWorksWhileWhoScoredIsDisabled() {
        allSchedulersContextRunner()
                .withPropertyValues(
                        "valuation.schedule.enabled=true",
                        "whoscored.sync.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(QuoteSchedulingConfig.class);
                    assertThat(context).hasSingleBean(QuoteRecalculationScheduler.class);
                    assertThat(context).doesNotHaveBean(SchedulingConfig.class);
                    assertThat(context).doesNotHaveBean(PlayerStatsScheduler.class);
                    assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
                    verifyNoInteractions(calculationService, playerStatsService);
                });
    }

    @Test
    void whoScoredSchedulerWorksWhileQuoteSchedulerIsDisabled() {
        allSchedulersContextRunner()
                .withPropertyValues(
                        "valuation.schedule.enabled=false",
                        "whoscored.sync.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(QuoteSchedulingConfig.class);
                    assertThat(context).doesNotHaveBean(QuoteRecalculationScheduler.class);
                    assertThat(context).hasSingleBean(SchedulingConfig.class);
                    assertThat(context).hasSingleBean(PlayerStatsScheduler.class);
                    assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
                    verifyNoInteractions(calculationService, playerStatsService);
                });
    }

    @Test
    void bothSchedulersCoexistWithOneSchedulingProcessor() {
        allSchedulersContextRunner()
                .withPropertyValues(
                        "valuation.schedule.enabled=true",
                        "whoscored.sync.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(QuoteSchedulingConfig.class);
                    assertThat(context).hasSingleBean(QuoteRecalculationScheduler.class);
                    assertThat(context).hasSingleBean(SchedulingConfig.class);
                    assertThat(context).hasSingleBean(PlayerStatsScheduler.class);
                    assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
                    verifyNoInteractions(calculationService, playerStatsService);
                });
    }

    @Test
    void successfulRunUsesOwnCorrelationIdAndLogsTheCompleteSummary(CapturedOutput output) {
        AtomicReference<String> delegatedCorrelationId = new AtomicReference<>();
        QuoteRunResult result = successResult();
        when(calculationService.recalculate(Optional.empty())).thenAnswer(invocation -> {
            delegatedCorrelationId.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            return result;
        });

        new QuoteRecalculationScheduler(calculationService).run();

        assertValuationCorrelationId(delegatedCorrelationId.get());
        assertThat(output).contains("quote_scheduled_recalculation_started")
                .contains("quote_scheduled_recalculation_finished")
                .contains("runId=" + result.runId())
                .contains("calculated=" + result.calculated())
                .contains("skipped=" + result.skipped())
                .contains("processed=10")
                .contains("failed=1")
                .contains("activeStrategy=OFFICIAL")
                .contains("marketValueUpdated=true")
                .doesNotContain("quote_scheduled_recalculation_skipped")
                .doesNotContain("quote_scheduled_recalculation_failed");
        assertThat(terminalEventCount(output.toString())).isEqualTo(1);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void inProgressRunIsLoggedAsSkippedAndNotPropagated(CapturedOutput output) {
        AtomicReference<String> delegatedCorrelationId = new AtomicReference<>();
        when(calculationService.recalculate(Optional.empty())).thenAnswer(invocation -> {
            delegatedCorrelationId.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            throw new RecalculationInProgressException();
        });

        assertThatCode(() -> new QuoteRecalculationScheduler(calculationService).run())
                .doesNotThrowAnyException();

        assertValuationCorrelationId(delegatedCorrelationId.get());
        assertThat(output).contains("quote_scheduled_recalculation_started")
                .contains("WARN")
                .contains("quote_scheduled_recalculation_skipped reason=recalculation_in_progress")
                .doesNotContain("quote_scheduled_recalculation_finished")
                .doesNotContain("quote_scheduled_recalculation_failed");
        assertThat(terminalEventCount(output.toString())).isEqualTo(1);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void unexpectedRuntimeExceptionLogsOnlyItsTypeAndIsNotPropagated(CapturedOutput output) {
        AtomicReference<String> delegatedCorrelationId = new AtomicReference<>();
        String sensitiveMessage = "secret-player-payload";
        when(calculationService.recalculate(Optional.empty())).thenAnswer(invocation -> {
            delegatedCorrelationId.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            throw new IllegalStateException(sensitiveMessage);
        });

        assertThatCode(() -> new QuoteRecalculationScheduler(calculationService).run())
                .doesNotThrowAnyException();

        assertValuationCorrelationId(delegatedCorrelationId.get());
        assertThat(output).contains("quote_scheduled_recalculation_started")
                .contains("ERROR")
                .contains("quote_scheduled_recalculation_failed errorType=IllegalStateException")
                .doesNotContain(sensitiveMessage)
                .doesNotContain("java.lang.IllegalStateException")
                .doesNotContain("quote_scheduled_recalculation_finished")
                .doesNotContain("quote_scheduled_recalculation_skipped");
        assertThat(terminalEventCount(output.toString())).isEqualTo(1);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    private ApplicationContextRunner quoteContextRunner() {
        return contextRunner.withUserConfiguration(
                QuoteSchedulingConfig.class,
                QuoteRecalculationScheduler.class);
    }

    private ApplicationContextRunner allSchedulersContextRunner() {
        return contextRunner
                .withBean(PlayerStatsService.class, () -> playerStatsService)
                .withUserConfiguration(
                        QuoteSchedulingConfig.class,
                        QuoteRecalculationScheduler.class,
                        SchedulingConfig.class,
                        PlayerStatsScheduler.class)
                .withPropertyValues(
                        "whoscored.sync.cron=0 0 4 * * MON",
                        "whoscored.sync.zone=UTC");
    }

    private QuoteRunResult successResult() {
        return new QuoteRunResult(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                Instant.parse("2026-10-26T09:00:00Z"),
                null,
                List.of(new StrategyRunCounts("OFFICIAL", 1, 7, 2, 1)),
                List.of(new SkippedStrategy("MOMENTUM", 1, SkippedStrategy.NO_ALGORITHM)),
                10,
                1,
                "OFFICIAL",
                true);
    }

    private void assertValuationCorrelationId(String correlationId) {
        assertThat(correlationId).startsWith("valuation-");
        assertThatCode(() -> UUID.fromString(correlationId.substring("valuation-".length())))
                .doesNotThrowAnyException();
    }

    private int terminalEventCount(String output) {
        return occurrences(output, "quote_scheduled_recalculation_finished")
                + occurrences(output, "quote_scheduled_recalculation_skipped")
                + occurrences(output, "quote_scheduled_recalculation_failed");
    }

    private int occurrences(String value, String fragment) {
        return (value.length() - value.replace(fragment, "").length()) / fragment.length();
    }
}
