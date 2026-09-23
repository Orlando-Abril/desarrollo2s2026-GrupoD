package com.example.demo.scheduler;

import com.example.demo.config.FootballDataProperties;
import com.example.demo.exception.SuperuserUnavailableException;
import com.example.demo.service.CatalogSyncAuditService;
import com.example.demo.service.PlayerCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.time.Duration;
import java.time.ZonedDateTime;

@Component
@EnableScheduling
public class PlayerCatalogScheduler {
    private static final Logger log = LoggerFactory.getLogger(PlayerCatalogScheduler.class);
    private final PlayerCatalogService service;
    private final CatalogSyncAuditService auditService;
    private final FootballDataProperties properties;
    private final AtomicBoolean running = new AtomicBoolean();

    public PlayerCatalogScheduler(PlayerCatalogService service, CatalogSyncAuditService auditService,
                                  FootballDataProperties properties) {
        this.service = service;
        this.auditService = auditService;
        this.properties = properties;
        validateMinimumInterval(properties.syncCron());
        validateBootstrapRetryInterval(properties.bootstrapRetryInterval());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void synchronizeOnStartup() {
        if (properties.enabled() && !auditService.hasSuccessfulSnapshot()) runOnce();
    }

    @Scheduled(cron = "${football-data.sync-cron}")
    public void synchronizeOnSchedule() {
        if (properties.enabled()) runOnce();
    }

    /**
     * Reintento de arranque (FR-016): hasta el primer snapshot exitoso reintenta ante cualquier
     * falla, p. ej. un superusuario creado después de levantar la aplicación.
     */
    @Scheduled(fixedDelayString = "${football-data.bootstrap-retry-interval}",
            initialDelayString = "${football-data.bootstrap-retry-interval}")
    public void retryUntilFirstSuccessfulSnapshot() {
        if (properties.enabled() && !auditService.hasSuccessfulSnapshot()) runOnce();
    }

    void runOnce() {
        if (!running.compareAndSet(false, true)) {
            log.info("catalog_sync_skipped reason=already_running");
            return;
        }
        try {
            PlayerCatalogService.SyncResult result = service.synchronizeCatalog();
            if (result != null && "FAILED".equals(result.status())) {
                log.warn("catalog_sync_attempt_failed result=FAILED correlationId={} failedLeagues={} failedPlayers={}",
                        result.correlationId(), result.failedLeagues(), result.failedPlayers());
            }
        } catch (SuperuserUnavailableException ex) {
            log.warn("catalog_sync_attempt_failed code=superuser_unavailable action=\"MARKET_SUPERUSER_USERNAME "
                    + "must reference an existing ADMIN user; synchronization will be retried\"");
        } catch (RuntimeException ex) {
            log.warn("catalog_sync_attempt_failed code=controlled_failure cause={}", ex.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }

    private void validateBootstrapRetryInterval(Duration interval) {
        if (interval == null || interval.compareTo(FootballDataProperties.MIN_SYNC_INTERVAL) < 0) {
            throw new IllegalArgumentException("football-data.bootstrap-retry-interval must be at least one minute");
        }
    }

    private void validateMinimumInterval(String cron) {
        CronExpression expression = CronExpression.parse(cron);
        ZonedDateTime first = expression.next(ZonedDateTime.now());
        ZonedDateTime second = first == null ? null : expression.next(first);
        if (first == null || second == null || Duration.between(first, second).compareTo(Duration.ofMinutes(1)) < 0) {
            throw new IllegalArgumentException("football-data.sync-cron must have an interval of at least one minute");
        }
    }
}
