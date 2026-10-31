package com.example.demo.service.valuation;

import com.example.demo.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

@Component
public class QuoteCacheEvictor {
    private static final Logger log = LoggerFactory.getLogger(QuoteCacheEvictor.class);
    private final CacheManager cacheManager;

    public QuoteCacheEvictor(CacheManager cacheManager) { this.cacheManager = cacheManager; }

    @EventListener
    public void onQuoteRunCompleted(QuoteRunCompletedEvent event) {
        try {
            Cache cache = cacheManager.getCache(CacheConfig.QUOTES_RANKING_CACHE);
            if (cache != null) cache.clear();
        } catch (RuntimeException ex) {
            log.warn("quotes_ranking_cache_evict_failed runId={} errorType={}", event.runId(), ex.getClass().getSimpleName());
        }
    }

    /** Spec 031: el ranking sin estrategia pasa a usar la nueva activa; se vacía tras confirmar el cambio. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActiveStrategyChanged(ActiveStrategyChangedEvent event) {
        try {
            Cache cache = cacheManager.getCache(CacheConfig.QUOTES_RANKING_CACHE);
            if (cache != null) cache.clear();
        } catch (RuntimeException ex) {
            log.warn("quotes_ranking_cache_evict_failed activeStrategy={} errorType={}", event.to(),
                    ex.getClass().getSimpleName());
        }
    }
}
