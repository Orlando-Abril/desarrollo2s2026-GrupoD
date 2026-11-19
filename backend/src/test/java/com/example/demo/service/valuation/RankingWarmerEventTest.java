package com.example.demo.service.valuation;

import com.example.demo.adapter.footballdata.FootballDataAdapter;
import com.example.demo.adapter.whoscored.WhoScoredAdapter;
import com.example.demo.config.CacheConfig;
import com.example.demo.dto.quote.RankingPageResponse;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@UnitTestProfile
class RankingWarmerEventTest {
    @Autowired ApplicationEventPublisher events;
    @Autowired CacheManager caches;
    @Autowired PlatformTransactionManager transactions;
    @Autowired QuoteQueryService queries;
    @MockitoSpyBean RankingLoader loader;
    @MockitoBean QuoteCalculationService calculationService;
    @MockitoBean FootballDataAdapter footballDataAdapter;
    @MockitoBean WhoScoredAdapter whoScoredAdapter;

    @BeforeEach void clear() { caches.getCache(CacheConfig.QUOTES_RANKING_CACHE).clear(); }

    @Test
    void runEventOutsideTransactionWarmsCurrentActivePage() {
        long before = queries.currentRankingGeneration();
        events.publishEvent(new QuoteRunCompletedEvent(UUID.randomUUID()));
        assertThat(queries.currentRankingGeneration()).isEqualTo(before + 1);
        await().untilAsserted(this::assertCurrentActivePageCached);
    }

    @Test
    void publishingEventReturnsWhileWarmerIsStillLoading() throws Exception {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            loading.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("warm timed out");
            return invocation.callRealMethod();
        }).when(loader).load(isNull(), isNull(), isNull(), any());
        try {
            events.publishEvent(new QuoteRunCompletedEvent(UUID.randomUUID()));
            assertThat(loading.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(release.getCount()).isEqualTo(1);
        } finally {
            release.countDown();
        }
        await().untilAsserted(this::assertCurrentActivePageCached);
    }

    @Test
    void runEventInsideTransactionWaitsForCommit() {
        var cache = caches.getCache(CacheConfig.QUOTES_RANKING_CACHE);
        cache.put("before-commit", new RankingPageResponse(List.of(), 0, 20, 0, 0));
        long before = queries.currentRankingGeneration();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            events.publishEvent(new QuoteRunCompletedEvent(UUID.randomUUID()));
            assertThat(queries.currentRankingGeneration()).isEqualTo(before);
            assertThat(cache.get("before-commit")).isNotNull();
        });
        assertThat(queries.currentRankingGeneration()).isEqualTo(before + 1);
        await().untilAsserted(this::assertCurrentActivePageCached);
    }

    @Test
    void strategyChangeOnlyWarmsAfterCommit() {
        long before = queries.currentRankingGeneration();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            events.publishEvent(new ActiveStrategyChangedEvent("MOMENTUM", "OFFICIAL"));
            assertThat(queries.currentRankingGeneration()).isEqualTo(before);
        });
        assertThat(queries.currentRankingGeneration()).isEqualTo(before + 1);
        await().untilAsserted(this::assertCurrentActivePageCached);
    }

    private void assertCurrentActivePageCached() {
        ConcurrentMapCache cache = (ConcurrentMapCache) caches.getCache(CacheConfig.QUOTES_RANKING_CACHE);
        assertThat(cache.getNativeCache().keySet()).anySatisfy(key ->
                assertThat(key.toString()).startsWith("active|-|-|0|20|gen=")
                        .endsWith(":" + queries.currentRankingGeneration()));
    }
}
