package com.example.demo.service.valuation;

import com.example.demo.dto.quote.RankingPageResponse;
import com.example.demo.model.League;
import com.example.demo.model.Position;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.service.valuation.strategy.ValuationStrategyCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RankingSingleFlightTest {
    @Test
    void twentyConcurrentRequestsToOneInstanceCalculateOnce() throws Exception {
        PlayerQuoteRepository quotes = mock(PlayerQuoteRepository.class);
        PlayerRepository players = mock(PlayerRepository.class);
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger queries = new AtomicInteger();
        when(quotes.findRanking(anyString(), any(), any(), any())).thenAnswer(invocation -> {
            queries.incrementAndGet();
            loading.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("load timed out");
            return new PageImpl<>(List.of(), invocation.getArgument(3), 0);
        });
        QuoteQueryService service = new QuoteQueryService(players, quotes, new ValuationStrategyCatalog("OFFICIAL"),
                new ConcurrentMapCacheManager("quotes-ranking"));
        ExecutorService pool = Executors.newFixedThreadPool(20);
        try {
            CountDownLatch ready = new CountDownLatch(20);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<RankingPageResponse>> results = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(i -> pool.submit(() -> {
                        ready.countDown();
                        start.await();
                        return service.ranking(null, null, null, PageRequest.of(0, 20));
                    })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(loading.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            for (Future<RankingPageResponse> future : results) assertThat(future.get(10, TimeUnit.SECONDS).items()).isEmpty();
            assertThat(queries).hasValue(1);

            service.ranking(null, League.LA_LIGA, null, PageRequest.of(0, 20));
            service.ranking(null, null, Position.FORWARD, PageRequest.of(0, 20));
            service.ranking(null, null, null, PageRequest.of(1, 20));
            service.ranking(null, null, null, PageRequest.of(0, 10));
            assertThat(queries).hasValue(5);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
