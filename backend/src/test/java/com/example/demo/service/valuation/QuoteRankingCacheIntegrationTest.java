package com.example.demo.service.valuation;

import com.example.demo.adapter.footballdata.FootballDataAdapter;
import com.example.demo.adapter.whoscored.WhoScoredAdapter;
import com.example.demo.config.CacheConfig;
import com.example.demo.dto.quote.RankingPageResponse;
import com.example.demo.exception.RecalculationFailedException;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.support.RankingFixtures;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest
@UnitTestProfile
class QuoteRankingCacheIntegrationTest {
    @Autowired QuoteQueryService queryService;
    @Autowired QuoteCalculationService calculationService;
    @Autowired PlayerRepository playerRepository;
    @Autowired PlayerQuoteRepository quoteRepository;
    @Autowired CacheManager cacheManager;
    @MockitoBean StatsSnapshotService snapshotService;
    @MockitoBean FootballDataAdapter footballDataAdapter;
    @MockitoBean WhoScoredAdapter whoScoredAdapter;

    private Player player;

    @BeforeEach
    void setUp() {
        cacheManager.getCache(CacheConfig.QUOTES_RANKING_CACHE).clear();
        player = RankingFixtures.player(playerRepository, "Cache integration", "Club", League.LIGUE_1,
                Position.FORWARD);
        RankingFixtures.quote(quoteRepository, player, "OFFICIAL", 1, "100.0000", "2026-10-01T00:00:00Z");
    }

    @Test
    void successfulAndFailedAcceptedRunsInvalidateAndExposeCurrentPersistedQuotes() {
        assertTopValue("100.0000");
        RankingFixtures.quote(quoteRepository, player, "OFFICIAL", 1, "200.0000", "2026-10-02T00:00:00Z");
        assertTopValue("100.0000"); // todavía es el valor cacheado

        when(snapshotService.snapshotAll(any())).thenReturn(new SnapshotResult(0, 0));
        calculationService.recalculate(Optional.of("OFFICIAL"));
        assertTopValue("200.0000");

        RankingFixtures.quote(quoteRepository, player, "OFFICIAL", 1, "300.0000", "2027-10-03T00:00:00Z");
        assertTopValue("200.0000");
        doThrow(new IllegalStateException("snapshot down")).when(snapshotService).snapshotAll(any());

        assertThatThrownBy(() -> calculationService.recalculate(Optional.of("OFFICIAL")))
                .isInstanceOf(RecalculationFailedException.class);
        assertTopValue("300.0000");
    }

    private void assertTopValue(String expected) {
        RankingPageResponse page = queryService.ranking("OFFICIAL", League.LIGUE_1, Position.FORWARD,
                PageRequest.of(0, 20));
        assertThat(page.items()).isNotEmpty();
        assertThat(page.items().get(0).value()).isEqualByComparingTo(expected);
    }
}
