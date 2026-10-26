package com.example.demo.support;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.PlayerQuote;
import com.example.demo.model.Position;
import com.example.demo.model.QuoteStatus;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class RankingFixtures {
    private RankingFixtures() { }

    public static Player player(PlayerRepository repository, String name, String team, League league,
                                Position... positions) {
        return repository.save(Player.builder().externalId(UUID.randomUUID().toString()).fullName(name).team(team)
                .league(league).positions(Set.of(positions)).marketValue(new BigDecimal("1.0000")).build());
    }

    public static PlayerQuote quote(PlayerQuoteRepository repository, Player player, String strategy, int version,
                                    String value, String calculatedAt) {
        return repository.save(PlayerQuote.builder().player(player).strategyCode(strategy).strategyVersion(version)
                .value(new BigDecimal(value)).score(new BigDecimal("1.0000")).status(QuoteStatus.CALCULATED)
                .calculatedAt(Instant.parse(calculatedAt)).runId(UUID.randomUUID())
                .correlationId("ranking-fixture").build());
    }
}
