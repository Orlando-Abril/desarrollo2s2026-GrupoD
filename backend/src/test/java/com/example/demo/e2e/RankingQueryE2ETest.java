package com.example.demo.e2e;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.PlayerQuote;
import com.example.demo.model.Position;
import com.example.demo.model.QuoteStatus;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.RankingRowProjection;
import com.example.demo.support.E2EFixtures;
import com.example.demo.support.E2ETest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@E2ETest
class RankingQueryE2ETest {
    @Autowired E2EFixtures fixtures;
    @Autowired PlayerQuoteRepository quotes;

    @Test
    void nativeRankingHasTheSameHistoryFilterRankAndPaginationSemanticsOnPostgreSql() {
        E2EFixtures.FixtureSet set = fixtures.createPlayersFromFiveLeagues();
        int value = 50;
        for (Player player : set.all()) {
            quote(player, "PORTABLE", 1, value - 10, "2026-10-01T00:00:00Z");
            quote(player, "PORTABLE", 2, value, "2026-10-02T00:00:00Z");
            value -= 5;
        }

        Page<RankingRowProjection> first = quotes.findRanking("PORTABLE", null, null, PageRequest.of(0, 2));
        Page<RankingRowProjection> second = quotes.findRanking("PORTABLE", null, null, PageRequest.of(1, 2));
        Page<RankingRowProjection> multiPosition = quotes.findRanking("PORTABLE", "LA_LIGA", "MIDFIELDER",
                PageRequest.of(0, 20));

        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getContent()).extracting(RankingRowProjection::getRank).containsExactly(1L, 2L);
        assertThat(first.getContent().get(0).getPreviousValue()).isEqualByComparingTo("40.0000");
        assertThat(second.getContent()).extracting(RankingRowProjection::getRank).containsExactly(3L, 4L);
        assertThat(multiPosition.getContent()).singleElement()
                .satisfies(row -> assertThat(row.getLeague()).isEqualTo(League.LA_LIGA.name()));
    }

    private void quote(Player player, String strategy, int version, int value, String calculatedAt) {
        quotes.save(PlayerQuote.builder().player(player).strategyCode(strategy).strategyVersion(version)
                .value(new BigDecimal(value).setScale(4)).score(BigDecimal.ONE).status(QuoteStatus.CALCULATED)
                .calculatedAt(Instant.parse(calculatedAt)).runId(UUID.randomUUID())
                .correlationId("ranking-e2e").build());
    }
}
