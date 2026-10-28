package com.example.demo.service.market;

import com.example.demo.service.valuation.QuoteRunCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Emite los tokens de los jugadores que todavía no los tienen al terminar cada recálculo de cotizaciones
 * (spec 026, research D13). El evento se publica de forma sincrónica mientras la guardia de corrida sigue
 * tomada, así que la primera emisión después de una sincronización grande alarga el recálculo; las
 * auditorías heredan el correlation id de la corrida. Nunca propaga una falla al recálculo.
 */
@Component
public class TokenIssuanceListener {

    private static final Logger log = LoggerFactory.getLogger(TokenIssuanceListener.class);

    private final TokenIssuanceService tokenIssuanceService;

    public TokenIssuanceListener(TokenIssuanceService tokenIssuanceService) {
        this.tokenIssuanceService = tokenIssuanceService;
    }

    @EventListener
    public void onQuoteRunCompleted(QuoteRunCompletedEvent event) {
        try {
            tokenIssuanceService.issueMissing();
        } catch (IllegalStateException ex) {
            if (!SuperuserAccount.MISSING.equals(ex.getMessage())) {
                logFailure(event, ex);
                return;
            }
            log.info("market_issuance_skipped reason={} runId={}", SuperuserAccount.MISSING, event.runId());
        } catch (RuntimeException ex) {
            logFailure(event, ex);
        }
    }

    private static void logFailure(QuoteRunCompletedEvent event, RuntimeException ex) {
        log.warn("market_issuance_after_quote_run_failed runId={} errorType={}",
                event.runId(), ex.getClass().getSimpleName());
    }
}
