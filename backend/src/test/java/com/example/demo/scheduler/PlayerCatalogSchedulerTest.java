package com.example.demo.scheduler;

import com.example.demo.config.FootballDataProperties;
import com.example.demo.service.CatalogSyncAuditService;
import com.example.demo.service.PlayerCatalogService;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerCatalogSchedulerTest {
    private final PlayerCatalogService service = mock(PlayerCatalogService.class);
    private final CatalogSyncAuditService audit = mock(CatalogSyncAuditService.class);
    private final FootballDataProperties enabled = new FootballDataProperties(URI.create("https://test"), "token",
            Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMinutes(1), "0 * * * * *", true);

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
        FootballDataProperties tooFrequent = new FootballDataProperties(URI.create("https://test"), "token",
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMinutes(1), "*/10 * * * * *", true);
        assertThatThrownBy(() -> new PlayerCatalogScheduler(service, audit, tooFrequent))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least one minute");
    }
}
