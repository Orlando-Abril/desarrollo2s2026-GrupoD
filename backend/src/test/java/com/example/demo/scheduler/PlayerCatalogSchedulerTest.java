package com.example.demo.scheduler;

import com.example.demo.config.FootballDataProperties;
import com.example.demo.exception.SuperuserUnavailableException;
import com.example.demo.service.CatalogSyncAuditService;
import com.example.demo.service.PlayerCatalogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class PlayerCatalogSchedulerTest {
    private final PlayerCatalogService service = mock(PlayerCatalogService.class);
    private final CatalogSyncAuditService audit = mock(CatalogSyncAuditService.class);
    private final FootballDataProperties enabled = properties("0 * * * * *", Duration.ofMinutes(1));

    @Test
    void startsWhenNoSuccessfulSnapshotExists() {
        when(audit.hasSuccessfulSnapshot()).thenReturn(false);
        new PlayerCatalogScheduler(service, audit, enabled).synchronizeOnStartup();
        verify(service).synchronizeCatalog();
    }

    @Test
    void skipsStartupWhenSnapshotExists() {
        when(audit.hasSuccessfulSnapshot()).thenReturn(true);
        new PlayerCatalogScheduler(service, audit, enabled).synchronizeOnStartup();
        verify(service, never()).synchronizeCatalog();
    }

    @Test
    void scheduledInvocationRunsSynchronization() {
        new PlayerCatalogScheduler(service, audit, enabled).synchronizeOnSchedule();
        verify(service).synchronizeCatalog();
    }

    @Test
    void rejectsSchedulesMoreFrequentThanOneMinute() {
        assertThatThrownBy(() -> new PlayerCatalogScheduler(service, audit,
                properties("*/10 * * * * *", Duration.ofMinutes(1))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least one minute");
    }

    @Test
    void bootstrapRetryRunsSynchronizationWhileNoSuccessfulSnapshotExists() {
        when(audit.hasSuccessfulSnapshot()).thenReturn(false);
        var scheduler = new PlayerCatalogScheduler(service, audit, enabled);
        scheduler.retryUntilFirstSuccessfulSnapshot();
        scheduler.retryUntilFirstSuccessfulSnapshot();
        verify(service, times(2)).synchronizeCatalog();
    }

    @Test
    void bootstrapRetryStopsOnceASuccessfulSnapshotExists() {
        when(audit.hasSuccessfulSnapshot()).thenReturn(true);
        new PlayerCatalogScheduler(service, audit, enabled).retryUntilFirstSuccessfulSnapshot();
        verify(service, never()).synchronizeCatalog();
    }

    @Test
    void bootstrapRetryDoesNothingWhenSynchronizationIsDisabled() {
        var disabled = new FootballDataProperties(URI.create("https://test"), "token", Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofMinutes(1), "0 * * * * *", Duration.ofMinutes(1), false);
        new PlayerCatalogScheduler(service, audit, disabled).retryUntilFirstSuccessfulSnapshot();
        verify(service, never()).synchronizeCatalog();
    }

    @Test
    void rejectsBootstrapRetryIntervalsShorterThanOneMinute() {
        assertThatThrownBy(() -> new PlayerCatalogScheduler(service, audit,
                properties("0 * * * * *", Duration.ofSeconds(30))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bootstrap-retry-interval must be at least one minute");
    }

    @Test
    void bootstrapRetryDoesNotOverlapARunningSynchronization() {
        when(audit.hasSuccessfulSnapshot()).thenReturn(false);
        var scheduler = new PlayerCatalogScheduler(service, audit, enabled);
        AtomicReference<Boolean> nested = new AtomicReference<>();
        when(service.synchronizeCatalog()).thenAnswer(invocation -> {
            scheduler.retryUntilFirstSuccessfulSnapshot();
            nested.set(true);
            return new PlayerCatalogService.SyncResult("COMPLETED", 0, 0, 0, UUID.randomUUID());
        });
        scheduler.synchronizeOnSchedule();
        assertThat(nested.get()).isTrue();
        verify(service, times(1)).synchronizeCatalog();
    }

    @Test
    void missingSuperuserLogsActionableWarningWithoutThrowing(CapturedOutput output) {
        when(audit.hasSuccessfulSnapshot()).thenReturn(false);
        when(service.synchronizeCatalog()).thenThrow(new SuperuserUnavailableException());
        new PlayerCatalogScheduler(service, audit, enabled).retryUntilFirstSuccessfulSnapshot();
        assertThat(output).contains("WARN").contains("code=superuser_unavailable")
                .contains("MARKET_SUPERUSER_USERNAME");
    }

    @Test
    void failedRunResultLogsWarning(CapturedOutput output) {
        when(audit.hasSuccessfulSnapshot()).thenReturn(false);
        when(service.synchronizeCatalog())
                .thenReturn(new PlayerCatalogService.SyncResult("FAILED", 0, 5, 0, UUID.randomUUID()));
        new PlayerCatalogScheduler(service, audit, enabled).retryUntilFirstSuccessfulSnapshot();
        assertThat(output).contains("WARN").contains("catalog_sync_attempt_failed").contains("result=FAILED");
    }

    private static FootballDataProperties properties(String cron, Duration bootstrapRetryInterval) {
        return new FootballDataProperties(URI.create("https://test"), "token", Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofMinutes(1), cron, bootstrapRetryInterval, true);
    }
}
