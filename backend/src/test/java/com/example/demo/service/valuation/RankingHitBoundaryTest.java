package com.example.demo.service.valuation;

import com.example.demo.dto.quote.RankingPageResponse;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.service.valuation.strategy.ValuationStrategyCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RankingHitBoundaryTest {
    @Test
    void cachedAliasAndExplicitRequestsDoNotEnterLoaderOrRepository() throws Exception {
        PlayerRepository players = mock(PlayerRepository.class);
        PlayerQuoteRepository quotes = mock(PlayerQuoteRepository.class);
        RankingLoader loader = mock(RankingLoader.class);
        RankingPageResponse page = new RankingPageResponse(List.of(), 0, 20, 0, 0);
        when(loader.load(isNull(), isNull(), isNull(), any()))
                .thenReturn(new RankingLoader.LoadedRanking("OFFICIAL", page));
        ConcurrentMapCache cache = spy(new ConcurrentMapCache("quotes-ranking"));
        CacheManager manager = mock(CacheManager.class);
        when(manager.getCache("quotes-ranking")).thenReturn(cache);
        QuoteQueryService service = new QuoteQueryService(players, quotes, new ValuationStrategyCatalog("OFFICIAL"),
                manager, loader);
        PageRequest request = PageRequest.of(0, 20);
        service.ranking(null, null, null, request);
        clearInvocations(loader, players, quotes, cache);

        assertThat(service.ranking(null, null, null, request)).isSameAs(page);
        assertThat(service.ranking("OFFICIAL", null, null, request)).isSameAs(page);
        verifyNoInteractions(loader, players, quotes);
        verify(cache, times(2)).get(any(), eq(RankingPageResponse.class));
        verify(cache, never()).get(any(), any(java.util.concurrent.Callable.class));
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(QuoteQueryService.class.getMethod("ranking", String.class,
                com.example.demo.model.League.class, com.example.demo.model.Position.class,
                org.springframework.data.domain.Pageable.class).isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(RankingLoader.class.getMethod("load", String.class,
                com.example.demo.model.League.class, com.example.demo.model.Position.class,
                org.springframework.data.domain.Pageable.class).getAnnotation(Transactional.class).readOnly()).isTrue();
    }
}
