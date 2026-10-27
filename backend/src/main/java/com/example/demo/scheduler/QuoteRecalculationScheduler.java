package com.example.demo.scheduler;

import com.example.demo.exception.RecalculationInProgressException;
import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.service.valuation.QuoteCalculationService;
import com.example.demo.service.valuation.QuoteRunResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;

/** Disparador periódico del recálculo de cotizaciones para todas las estrategias. */
@Component
@ConditionalOnProperty(name = "valuation.schedule.enabled", havingValue = "true")
public class QuoteRecalculationScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuoteRecalculationScheduler.class);

    private final QuoteCalculationService calculationService;

    public QuoteRecalculationScheduler(QuoteCalculationService calculationService) {
        this.calculationService = calculationService;
    }

    @Scheduled(
            cron = "${valuation.schedule.cron:0 0 6 * * MON}",
            zone = "${valuation.schedule.zone:UTC}")
    public void run() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "valuation-" + UUID.randomUUID());
        try {
            log.info("quote_scheduled_recalculation_started");
            QuoteRunResult result = calculationService.recalculate(Optional.empty());
            log.info("quote_scheduled_recalculation_finished runId={} calculated={} skipped={} processed={} "
                            + "failed={} activeStrategy={} marketValueUpdated={}",
                    result.runId(), result.calculated(), result.skipped(), result.playersProcessed(),
                    result.playersFailed(), result.activeStrategy(), result.marketValueUpdated());
        } catch (RecalculationInProgressException exception) {
            log.warn("quote_scheduled_recalculation_skipped reason=recalculation_in_progress");
        } catch (RuntimeException exception) {
            log.error("quote_scheduled_recalculation_failed errorType={}",
                    exception.getClass().getSimpleName());
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}
