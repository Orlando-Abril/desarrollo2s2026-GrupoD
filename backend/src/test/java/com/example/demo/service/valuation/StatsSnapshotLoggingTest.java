package com.example.demo.service.valuation;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.repository.PlayerStatsRepository;
import com.example.demo.repository.PlayerStatsSnapshotRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class StatsSnapshotLoggingTest {

    private final PlayerStatsRepository statsRepository = Mockito.mock(PlayerStatsRepository.class);
    private final PlayerStatsSnapshotRepository snapshotRepository = Mockito.mock(PlayerStatsSnapshotRepository.class);
    private final PlatformTransactionManager transactionManager = Mockito.mock(PlatformTransactionManager.class);
    private final Logger logger = (Logger) LoggerFactory.getLogger(StatsSnapshotService.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private StatsSnapshotService service;

    @BeforeEach
    void setUp() {
        MDC.clear();
        appender.start();
        logger.addAppender(appender);
        service = new StatsSnapshotService(statsRepository, snapshotRepository, Clock.systemUTC(), transactionManager);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        MDC.clear();
    }

    @Test
    void successKeepsExistingCorrelationIdAndLogsCountsOnce() {
        when(statsRepository.findAll()).thenReturn(List.of());
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-existing");
        UUID runId = UUID.randomUUID();

        assertThat(service.snapshotAll(runId)).isEqualTo(new SnapshotResult(0, 0));

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).contains("player_stats_snapshot_finished")
                    .contains("runId=" + runId).contains("saved=0").contains("skipped=0");
            assertThat(event.getMDCPropertyMap()).containsEntry(CorrelationIdFilter.MDC_KEY, "corr-existing");
        });
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("corr-existing");
    }

    @Test
    void missingCorrelationIdIsGeneratedForTheEventAndRemovedAfterwards() {
        when(statsRepository.findAll()).thenReturn(List.of());

        service.snapshotAll(UUID.randomUUID());

        String generated = appender.list.get(0).getMDCPropertyMap().get(CorrelationIdFilter.MDC_KEY);
        assertThatCodeIsUuid(generated);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void failureLogsErrorTypeWithoutPartialCountsAndRestoresMdc() {
        when(statsRepository.findAll()).thenThrow(new IllegalStateException("boom"));
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-error");
        UUID runId = UUID.randomUUID();

        assertThatThrownBy(() -> service.snapshotAll(runId)).isInstanceOf(IllegalStateException.class);

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("player_stats_snapshot_failed")
                    .contains("runId=" + runId).contains("errorType=IllegalStateException")
                    .doesNotContain("saved=").doesNotContain("skipped=");
            assertThat(event.getMDCPropertyMap()).containsEntry(CorrelationIdFilter.MDC_KEY, "corr-error");
        });
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("corr-error");
    }

    private void assertThatCodeIsUuid(String value) {
        assertThat(value).isNotBlank();
        assertThat(UUID.fromString(value)).isNotNull();
    }
}
