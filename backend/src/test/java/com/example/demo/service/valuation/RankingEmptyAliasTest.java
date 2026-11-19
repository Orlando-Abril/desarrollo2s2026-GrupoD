package com.example.demo.service.valuation;

import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.service.valuation.strategy.ValuationStrategyCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RankingEmptyAliasTest {
    @Test
    void emptyActiveLoadAlsoPublishesExplicitKeyWithoutAnotherQuery() {
        PlayerRepository players = mock(PlayerRepository.class);
        PlayerQuoteRepository quotes = mock(PlayerQuoteRepository.class);
        when(quotes.findRanking(eq("OFFICIAL"), isNull(), isNull(), any()))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(3), 0));
        ConcurrentMapCacheManager caches = new ConcurrentMapCacheManager("quotes-ranking");
        QuoteQueryService service = new QuoteQueryService(players, quotes,
                new ValuationStrategyCatalog("OFFICIAL"), caches);
        PageRequest page = PageRequest.of(0, 20);

        var fromAlias = service.ranking(null, null, null, page);
        var fromExplicit = service.ranking("OFFICIAL", null, null, page);

        assertThat(fromAlias.items()).isEmpty();
        assertThat(fromExplicit).isSameAs(fromAlias);
        verify(quotes, times(1)).findRanking(eq("OFFICIAL"), isNull(), isNull(), eq(page));
        verifyNoInteractions(players);
        Cache cache = caches.getCache("quotes-ranking");
        assertThat(cache.getNativeCache().toString()).contains("active|-|-|0|20|gen=")
                .contains("OFFICIAL|-|-|0|20|gen=");
    }
}
