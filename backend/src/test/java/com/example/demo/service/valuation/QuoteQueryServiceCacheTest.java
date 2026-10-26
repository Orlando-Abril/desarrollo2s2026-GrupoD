package com.example.demo.service.valuation;

import com.example.demo.dto.quote.RankingPageResponse;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.RankingRowProjection;
import com.example.demo.support.RankingFixtures;
import com.example.demo.support.UnitTestProfile;
import com.example.demo.adapter.footballdata.FootballDataAdapter;
import com.example.demo.adapter.whoscored.WhoScoredAdapter;
import com.example.demo.service.valuation.strategy.ValuationStrategyCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.clearInvocations;

class QuoteQueryServiceCacheTest {
    private final PlayerRepository players = mock(PlayerRepository.class);
    private final PlayerQuoteRepository quotes = mock(PlayerQuoteRepository.class);
    private final ValuationStrategyCatalog catalog = new ValuationStrategyCatalog("OFFICIAL");

    @Test
    void equivalentEffectiveStrategyHitsCacheAndEveryKeyComponentIsolated() {
        ConcurrentMapCacheManager caches = new ConcurrentMapCacheManager("quotes-ranking");
        QuoteQueryService service = service(caches);

        service.ranking(null, null, null, PageRequest.of(0, 20));
        service.ranking("OFFICIAL", null, null, PageRequest.of(0, 20));
        service.ranking("MOMENTUM", null, null, PageRequest.of(0, 20));
        service.ranking("OFFICIAL", League.LA_LIGA, null, PageRequest.of(0, 20));
        service.ranking("OFFICIAL", null, Position.FORWARD, PageRequest.of(0, 20));
        service.ranking("OFFICIAL", null, null, PageRequest.of(1, 20));
        service.ranking("OFFICIAL", null, null, PageRequest.of(0, 10));

        verify(quotes, times(6)).findRanking(anyString(), any(), any(), any());
    }

    @Test
    void cacheManagerReadAndWriteFailuresAreFailOpen() {
        CacheManager failing = mock(CacheManager.class);
        when(failing.getCache(anyString())).thenThrow(new IllegalStateException("redis down"));

        RankingPageResponse response = service(failing).ranking(null, null, null, PageRequest.of(0, 20));

        assertThat(response.items()).hasSize(1);
        verify(quotes).findRanking(anyString(), any(), any(), any());
    }

    private QuoteQueryService service(CacheManager cacheManager) {
        Player player = Player.builder().id(7L).fullName("Player").team("Club").league(League.LA_LIGA)
                .positions(Set.of(Position.FORWARD)).marketValue(BigDecimal.ONE).build();
        RankingRowProjection row = mock(RankingRowProjection.class);
        when(row.getRank()).thenReturn(1L);
        when(row.getPlayerId()).thenReturn(7L);
        when(row.getName()).thenReturn("Player");
        when(row.getTeam()).thenReturn("Club");
        when(row.getLeague()).thenReturn("LA_LIGA");
        when(row.getCurrentValue()).thenReturn(new BigDecimal("2.0000"));
        when(row.getPreviousValue()).thenReturn(new BigDecimal("1.0000"));
        when(row.getStrategy()).thenReturn("OFFICIAL");
        when(row.getVersion()).thenReturn(1);
        when(quotes.findRanking(anyString(), any(), any(), any())).thenAnswer(invocation ->
                new PageImpl<>(List.of(row), invocation.getArgument(3), 1));
        when(players.findAllById(any())).thenReturn(List.of(player));
        return new QuoteQueryService(players, quotes, catalog, cacheManager);
    }
}

@SpringBootTest
@UnitTestProfile
@Transactional
class QuoteQueryServiceRepositorySpyTest {
    @Autowired QuoteQueryService service;
    @Autowired PlayerRepository players;
    @Autowired CacheManager caches;
    @MockitoSpyBean PlayerQuoteRepository quotes;
    @MockitoBean QuoteCalculationService calculationService;
    @MockitoBean FootballDataAdapter footballDataAdapter;
    @MockitoBean WhoScoredAdapter whoScoredAdapter;

    @Test
    void omittedAndExplicitActiveStrategyShareTheSameRepositoryResult() {
        caches.getCache("quotes-ranking").clear();
        Player player = RankingFixtures.player(players, "Spy", "Club", League.LA_LIGA, Position.FORWARD);
        RankingFixtures.quote(quotes, player, "OFFICIAL", 1, "4.0000", "2026-10-01T00:00:00Z");
        clearInvocations(quotes);

        service.ranking(null, League.LA_LIGA, Position.FORWARD, PageRequest.of(0, 20));
        service.ranking("OFFICIAL", League.LA_LIGA, Position.FORWARD, PageRequest.of(0, 20));

        verify(quotes, times(1)).findRanking("OFFICIAL", "LA_LIGA", "FORWARD", PageRequest.of(0, 20));
    }
}
