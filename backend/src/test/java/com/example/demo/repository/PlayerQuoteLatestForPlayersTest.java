package com.example.demo.repository;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.PlayerQuote;
import com.example.demo.model.Position;
import com.example.demo.support.RankingFixtures;
import com.example.demo.support.UnitTestProfile;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@UnitTestProfile
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class PlayerQuoteLatestForPlayersTest {

    @Autowired PlayerQuoteRepository quoteRepository;
    @Autowired PlayerRepository playerRepository;
    @Autowired TestEntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void returnsOnlyTheLatestQuoteOfTheRequestedStrategyPerPlayer() {
        Player first = player("First");
        Player second = player("Second");
        Player withoutQuote = player("No quote");
        Player notRequested = player("Not requested");
        RankingFixtures.quote(quoteRepository, first, "MOMENTUM", 1, "1.1000", "2026-10-01T00:00:00Z");
        RankingFixtures.quote(quoteRepository, first, "MOMENTUM", 1, "1.2000", "2026-10-02T00:00:00Z");
        RankingFixtures.quote(quoteRepository, first, "OFFICIAL", 1, "9.0000", "2026-10-03T00:00:00Z");
        RankingFixtures.quote(quoteRepository, second, "MOMENTUM", 1, "2.0000", "2026-10-02T00:00:00Z");
        RankingFixtures.quote(quoteRepository, notRequested, "MOMENTUM", 1, "5.0000", "2026-10-02T00:00:00Z");

        List<PlayerQuote> latest = quoteRepository.findLatestForPlayers("MOMENTUM",
                List.of(first.getId(), second.getId(), withoutQuote.getId()));

        assertThat(latest).extracting(quote -> quote.getPlayer().getId(), PlayerQuote::getValue)
                .containsExactlyInAnyOrder(
                        tuple(first.getId(), new BigDecimal("1.2000")),
                        tuple(second.getId(), new BigDecimal("2.0000")));
    }

    @Test
    void breaksTiesOnCalculatedAtByHighestId() {
        Player player = player("Tie");
        RankingFixtures.quote(quoteRepository, player, "MOMENTUM", 1, "1.1000", "2026-10-02T00:00:00Z");
        PlayerQuote later = RankingFixtures.quote(quoteRepository, player, "MOMENTUM", 1, "1.3000",
                "2026-10-02T00:00:00Z");

        assertThat(quoteRepository.findLatestForPlayers("MOMENTUM", List.of(player.getId())))
                .extracting(PlayerQuote::getId).containsExactly(later.getId());
    }

    @Test
    void executesAConstantNumberOfStatementsRegardlessOfHowManyIdsAreRequested() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Player player = player("P" + i);
            RankingFixtures.quote(quoteRepository, player, "MOMENTUM", 1, "1.0000", "2026-10-02T00:00:00Z");
            ids.add(player.getId());
        }
        entityManager.flush();
        SessionFactory factory = entityManagerFactory.unwrap(SessionFactory.class);

        long few = measure(factory, ids.subList(0, 5));
        long many = measure(factory, ids);

        assertThat(many).isEqualTo(few);
    }

    private long measure(SessionFactory factory, List<Long> ids) {
        entityManager.clear();
        factory.getStatistics().clear();
        quoteRepository.findLatestForPlayers("MOMENTUM", ids).forEach(quote -> quote.getPlayer().getFullName());
        return factory.getStatistics().getPrepareStatementCount();
    }

    private Player player(String name) {
        return RankingFixtures.player(playerRepository, name, "Club", League.LA_LIGA, Position.FORWARD);
    }
}
