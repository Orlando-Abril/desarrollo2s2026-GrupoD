package com.example.demo.service.valuation;

import com.example.demo.dto.quote.RankingPageResponse;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.service.valuation.strategy.ValuationStrategyCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RankingWarmerRaceTest {
    @Test
    void warmerSwallowsLoadFailureSoOriginatingEventIsUnaffected() {
        QuoteQueryService queries = mock(QuoteQueryService.class);
        when(queries.ranking(isNull(), isNull(), isNull(), any()))
                .thenThrow(new IllegalStateException("database unavailable"));
        assertThatCode(() -> new RankingWarmer(queries).warm()).doesNotThrowAnyException();
    }

    @Test
    void oldLoadFinishingAfterClearCannotPublishIntoCurrentGeneration() throws Exception {
        RankingLoader loader = mock(RankingLoader.class);
        RankingPageResponse oldPage = new RankingPageResponse(List.of(), 0, 20, 0, 0);
        RankingPageResponse newPage = new RankingPageResponse(List.of(), 0, 20, 0, 0);
        CountDownLatch oldStarted = new CountDownLatch(1);
        CountDownLatch releaseOld = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(loader.load(isNull(), isNull(), isNull(), any())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                oldStarted.countDown();
                if (!releaseOld.await(10, TimeUnit.SECONDS)) throw new AssertionError("old load timed out");
                return new RankingLoader.LoadedRanking("OFFICIAL", oldPage);
            }
            return new RankingLoader.LoadedRanking("OFFICIAL", newPage);
        });
        Map<Object, RankingPageResponse> entries = new ConcurrentHashMap<>();
        Cache cache = mock(Cache.class);
        CacheManager caches = mock(CacheManager.class);
        when(caches.getCache("quotes-ranking")).thenReturn(cache);
        when(cache.get(any(), eq(RankingPageResponse.class)))
                .thenAnswer(invocation -> entries.get(invocation.getArgument(0)));
        when(cache.get(any(), any(Callable.class))).thenAnswer(invocation -> {
            Object key = invocation.getArgument(0);
            RankingPageResponse existing = entries.get(key);
            if (existing != null) return existing;
            RankingPageResponse result = ((Callable<RankingPageResponse>) invocation.getArgument(1)).call();
            entries.put(key, result);
            return result;
        });
        doAnswer(invocation -> { entries.put(invocation.getArgument(0), invocation.getArgument(1)); return null; })
                .when(cache).put(any(), any());
        doAnswer(invocation -> { entries.remove(invocation.getArgument(0)); return null; })
                .when(cache).evict(any());
        doAnswer(invocation -> { entries.clear(); return null; }).when(cache).clear();
        QuoteQueryService service = new QuoteQueryService(mock(PlayerRepository.class), mock(PlayerQuoteRepository.class),
                new ValuationStrategyCatalog("OFFICIAL"), caches, loader);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<RankingPageResponse> stale = pool.submit(() -> service.ranking(null, null, null, PageRequest.of(0, 20)));
            assertThat(oldStarted.await(10, TimeUnit.SECONDS)).isTrue();
            long before = service.currentRankingGeneration();
            new QuoteCacheEvictor(caches, service, mock(RankingWarmer.class))
                    .onQuoteRunCompleted(new QuoteRunCompletedEvent(UUID.randomUUID()));
            assertThat(service.currentRankingGeneration()).isEqualTo(before + 1);
            assertThat(service.ranking(null, null, null, PageRequest.of(0, 20))).isSameAs(newPage);
            releaseOld.countDown();
            assertThat(stale.get(10, TimeUnit.SECONDS)).isSameAs(oldPage);

            assertThat(entries.entrySet())
                    .allSatisfy(entry -> {
                        assertThat(entry.getKey().toString()).contains("gen=").endsWith(":" + service.currentRankingGeneration());
                        assertThat(entry.getValue()).isSameAs(newPage);
                    });
            assertThat(entries).hasSize(2); // alias y código explícito vigentes
            verify(loader, times(2)).load(isNull(), isNull(), isNull(), any());
        } finally {
            releaseOld.countDown();
            pool.shutdownNow();
        }
    }
}
