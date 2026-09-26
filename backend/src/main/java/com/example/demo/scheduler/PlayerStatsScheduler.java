package com.example.demo.scheduler;

import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.service.PlayerStatsService;
import com.example.demo.service.StatsUpdateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Disparador periódico de la actualización de estadísticas de WhoScored. Sólo existe con
 * {@code whoscored.sync.enabled=true}; no tiene lógica propia: delega la actualización completa en {@link PlayerStatsService}.
 */
@Component
@ConditionalOnProperty(name = "whoscored.sync.enabled", havingValue = "true")
public class PlayerStatsScheduler {
    private static final Logger log = LoggerFactory.getLogger(PlayerStatsScheduler.class);

    private final PlayerStatsService service;

    public PlayerStatsScheduler(PlayerStatsService service) {
        this.service = service;
    }

    @Scheduled(cron = "${whoscored.sync.cron}", zone = "${whoscored.sync.zone:UTC}")
    public void run() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "whoscored-" + UUID.randomUUID());
        try {
            log.info("whoscored_stats_update_started");
            StatsUpdateResult result = service.updateAllStats();
            log.info("whoscored_stats_update_finished processed={} updated={} fromCache={} unmatched={} failed={}",
                    result.processed(), result.updated(), result.fromCache(), result.unmatched(), result.failed());
        } catch (RuntimeException ex) {
            log.error("whoscored_stats_update_crashed", ex);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}
