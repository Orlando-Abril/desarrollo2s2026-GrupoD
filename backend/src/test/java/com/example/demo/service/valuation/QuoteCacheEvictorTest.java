package com.example.demo.service.valuation;

import com.example.demo.config.CacheConfig;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuoteCacheEvictorTest {
    @Test
    void completionRemovesEveryPreviouslyStoredRankingKey() {
        ConcurrentMapCacheManager manager = new ConcurrentMapCacheManager(CacheConfig.QUOTES_RANKING_CACHE);
        Cache cache = manager.getCache(CacheConfig.QUOTES_RANKING_CACHE);
        cache.put("OFFICIAL|-|-|0|20", "one");
        cache.put("MOMENTUM|LA_LIGA|FORWARD|1|10", "two");

        new QuoteCacheEvictor(manager).onQuoteRunCompleted(new QuoteRunCompletedEvent(UUID.randomUUID()));

        org.assertj.core.api.Assertions.assertThat(cache.get("OFFICIAL|-|-|0|20")).isNull();
        org.assertj.core.api.Assertions.assertThat(cache.get("MOMENTUM|LA_LIGA|FORWARD|1|10")).isNull();
    }

    @Test
    void completionClearsTheWholeRankingCacheSynchronously() {
        CacheManager manager = mock(CacheManager.class);
        Cache cache = mock(Cache.class);
        when(manager.getCache(CacheConfig.QUOTES_RANKING_CACHE)).thenReturn(cache);

        new QuoteCacheEvictor(manager).onQuoteRunCompleted(new QuoteRunCompletedEvent(UUID.randomUUID()));

        verify(cache).clear();
    }

    @Test
    void lookupAndClearFailuresDoNotEscape() {
        CacheManager lookupFailure = mock(CacheManager.class);
        when(lookupFailure.getCache(CacheConfig.QUOTES_RANKING_CACHE))
                .thenThrow(new IllegalStateException("redis down"));
        assertThatCode(() -> new QuoteCacheEvictor(lookupFailure)
                .onQuoteRunCompleted(new QuoteRunCompletedEvent(UUID.randomUUID()))).doesNotThrowAnyException();

        CacheManager manager = mock(CacheManager.class);
        Cache cache = mock(Cache.class);
        when(manager.getCache(CacheConfig.QUOTES_RANKING_CACHE)).thenReturn(cache);
        doThrow(new IllegalStateException("redis down")).when(cache).clear();
        assertThatCode(() -> new QuoteCacheEvictor(manager)
                .onQuoteRunCompleted(new QuoteRunCompletedEvent(UUID.randomUUID()))).doesNotThrowAnyException();
    }
}
