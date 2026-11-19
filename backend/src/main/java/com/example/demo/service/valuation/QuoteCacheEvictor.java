package com.example.demo.service.valuation;

import com.example.demo.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class QuoteCacheEvictor {
    private static final Logger log = LoggerFactory.getLogger(QuoteCacheEvictor.class);
    private final CacheManager cacheManager;
    private final QuoteQueryService queries;
    private final RankingWarmer warmer;

    @Autowired
    public QuoteCacheEvictor(CacheManager cacheManager, QuoteQueryService queries, RankingWarmer warmer) {
        this.cacheManager = cacheManager;
        this.queries = queries;
        this.warmer = warmer;
    }

    /** Preserva el constructor usado por las pruebas de la entrega anterior. */
    public QuoteCacheEvictor(CacheManager cacheManager) { this(cacheManager, null, null); }

    @EventListener
    public void onQuoteRunCompleted(QuoteRunCompletedEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { invalidate("runId=" + event.runId()); }
            });
        } else {
            invalidate("runId=" + event.runId());
        }
    }

    /** Spec 031: el ranking sin estrategia pasa a usar la nueva activa; se vacía tras confirmar el cambio. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActiveStrategyChanged(ActiveStrategyChangedEvent event) {
        invalidate("activeStrategy=" + event.to());
    }

    private void invalidate(String source) {
        if (queries != null) queries.advanceRankingGeneration();
        try {
            Cache cache = cacheManager.getCache(CacheConfig.QUOTES_RANKING_CACHE);
            if (cache != null) cache.clear();
        } catch (RuntimeException ex) {
            log.warn("quotes_ranking_cache_evict_failed source={} errorType={}", source, ex.getClass().getSimpleName());
        }
        if (warmer != null) warmer.warm();
    }
}
