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
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RankingRedisFallbackTest {
    @Test
    void readFailureLoadsFromDatabaseOnce() {
        Fixture fixture = new Fixture();
        when(fixture.cache.get(any(), eq(RankingPageResponse.class))).thenThrow(new IllegalStateException("redis read"));
        when(fixture.cache.get(any(), any(Callable.class))).thenThrow(new IllegalStateException("redis read"));
        assertThat(fixture.service.ranking(null, null, null, PageRequest.of(0, 20))).isSameAs(fixture.page);
        verify(fixture.loader, times(1)).load(isNull(), isNull(), isNull(), any());
    }

    @Test
    void writeFailureAfterCallableReturnsCapturedPageWithoutSecondLoad() {
        Fixture fixture = new Fixture();
        when(fixture.cache.get(any(), any(Callable.class))).thenAnswer(invocation -> {
            Callable<?> loader = invocation.getArgument(1);
            loader.call();
            throw new IllegalStateException("redis write");
        });
        assertThat(fixture.service.ranking(null, null, null, PageRequest.of(0, 20))).isSameAs(fixture.page);
        verify(fixture.loader, times(1)).load(isNull(), isNull(), isNull(), any());
    }

    @Test
    void loaderFailureIsNotRetriedAsRedisFailure() {
        Fixture fixture = new Fixture();
        when(fixture.cache.get(any(), any(Callable.class))).thenAnswer(invocation -> {
            Callable<?> loader = invocation.getArgument(1);
            throw new Cache.ValueRetrievalException("key", loader, new IllegalStateException("database"));
        });
        assertThatThrownBy(() -> fixture.service.ranking(null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(Cache.ValueRetrievalException.class).hasRootCauseInstanceOf(IllegalStateException.class);
        verify(fixture.loader, never()).load(any(), any(), any(), any());
    }

    private static class Fixture {
        final RankingLoader loader = mock(RankingLoader.class);
        final Cache cache = mock(Cache.class);
        final CacheManager manager = mock(CacheManager.class);
        final RankingPageResponse page = new RankingPageResponse(List.of(), 0, 20, 0, 0);
        final QuoteQueryService service;

        Fixture() {
            when(manager.getCache("quotes-ranking")).thenReturn(cache);
            when(loader.load(isNull(), isNull(), isNull(), any()))
                    .thenReturn(new RankingLoader.LoadedRanking("OFFICIAL", page));
            service = new QuoteQueryService(mock(PlayerRepository.class), mock(PlayerQuoteRepository.class),
                    new ValuationStrategyCatalog("OFFICIAL"), manager, loader);
        }
    }
}
