package com.example.demo.scheduler;

import com.example.demo.config.SchedulingConfig;
import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.service.PlayerStatsService;
import com.example.demo.service.StatsUpdateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class PlayerStatsSchedulerTest {

    private final PlayerStatsService service = mock(PlayerStatsService.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(PlayerStatsService.class, () -> service)
            .withUserConfiguration(SchedulingConfig.class, PlayerStatsScheduler.class)
            .withPropertyValues("whoscored.sync.cron=0 0 4 * * MON", "whoscored.sync.zone=UTC");

    @Test
    void delegatesToTheServiceAndLogsStartAndSummary(CapturedOutput output) {
        AtomicReference<String> correlationId = new AtomicReference<>();
        when(service.updateAllStats()).thenAnswer(invocation -> {
            correlationId.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            return new StatsUpdateResult(10, 6, 2, 3, 1);
        });

        new PlayerStatsScheduler(service).run();

        verify(service, times(1)).updateAllStats();
        assertThat(correlationId.get()).startsWith("whoscored-");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
        assertThat(output).contains("whoscored_stats_update_started")
                .contains("whoscored_stats_update_finished processed=10 updated=6 fromCache=2 unmatched=3 failed=1");
    }

    @Test
    void unexpectedErrorIsLoggedAndNotPropagated(CapturedOutput output) {
        when(service.updateAllStats()).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> new PlayerStatsScheduler(service).run()).doesNotThrowAnyException();

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
        assertThat(output).contains("whoscored_stats_update_crashed");
    }

    @Test
    void isNotCreatedByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(PlayerStatsScheduler.class);
            assertThat(context).doesNotHaveBean(SchedulingConfig.class);
            assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
        });
    }

    @Test
    void isNotCreatedWhenExplicitlyDisabled() {
        contextRunner.withPropertyValues("whoscored.sync.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(PlayerStatsScheduler.class);
            assertThat(context).doesNotHaveBean(SchedulingConfig.class);
        });
    }

    @Test
    void isCreatedWhenEnabledAndDoesNotRunOnStartup() {
        contextRunner.withPropertyValues("whoscored.sync.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(PlayerStatsScheduler.class);
            assertThat(context).hasSingleBean(SchedulingConfig.class);
            assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
            verify(service, never()).updateAllStats();
        });
    }
}
