package com.example.demo.service.valuation;

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
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;

class RankingSingleFlightFailureTest {
    @Test
    void loaderFailureReleasesFollowersAndNextRequestRetries() throws Exception {
        PlayerQuoteRepository quotes = mock(PlayerQuoteRepository.class);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(quotes.findRanking(anyString(), any(), any(), any())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                loading.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("load timed out");
                throw new IllegalStateException("database failure");
            }
            return new PageImpl<>(List.of(), invocation.getArgument(3), 0);
        });
        QuoteQueryService service = new QuoteQueryService(mock(PlayerRepository.class), quotes,
                new ValuationStrategyCatalog("OFFICIAL"), new ConcurrentMapCacheManager("quotes-ranking"));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> leader = pool.submit(() -> service.ranking(null, null, null, PageRequest.of(0, 20)));
            assertThat(loading.await(10, TimeUnit.SECONDS)).isTrue();
            AtomicReference<Thread> followerThread = new AtomicReference<>();
            Future<?> follower = pool.submit(() -> {
                followerThread.set(Thread.currentThread());
                return service.ranking(null, null, null, PageRequest.of(0, 20));
            });
            await().atMost(Duration.ofSeconds(10)).until(() -> followerThread.get() != null
                    && followerThread.get().getState() == Thread.State.WAITING);
            release.countDown();
            assertThatThrownBy(() -> leader.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasRootCauseInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> follower.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasRootCauseInstanceOf(IllegalStateException.class);
            assertThat(calls).hasValue(1);
            assertThat(service.ranking(null, null, null, PageRequest.of(0, 20)).items()).isEmpty();
            assertThat(calls).hasValue(2);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
