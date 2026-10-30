package com.example.demo.repository.market;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.model.market.Holding;
import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderSide;
import com.example.demo.model.market.Trade;
import com.example.demo.support.UnitTestProfile;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@UnitTestProfile
class PortfolioQueryTest {

    @Autowired HoldingRepository holdings;
    @Autowired TradeRepository trades;
    @Autowired MarketOrderRepository orders;
    @Autowired TestEntityManager entityManager;

    @Test
    void portfolioProjectionIncludesPlayerAndUsesOneStatementRegardlessOfPositionCount() {
        User user = MarketFixtures.user(entityManager);
        Player zeta = player("Zeta", "2.5000");
        entityManager.persist(Holding.issue(user, zeta, 4, Instant.parse("2026-01-01T00:00:00Z")));
        entityManager.flush();
        entityManager.clear();

        SessionFactory factory = entityManager.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class);
        factory.getStatistics().clear();
        assertThat(holdings.findPortfolioRows(user.getId())).singleElement().satisfies(row -> {
            assertThat(row.playerName()).isEqualTo("Zeta");
            assertThat(row.marketValue()).isEqualByComparingTo("2.5000");
        });
        long onePositionStatements = factory.getStatistics().getPrepareStatementCount();

        Player alfa = player("Alfa", "1.2000");
        entityManager.persist(Holding.issue(entityManager.find(User.class, user.getId()), alfa, 3,
                Instant.parse("2026-01-01T00:00:01Z")));
        entityManager.flush();
        entityManager.clear();
        factory.getStatistics().clear();
        assertThat(holdings.findPortfolioRows(user.getId())).extracting(PortfolioHoldingRow::playerName)
                .containsExactly("Alfa", "Zeta");
        assertThat(factory.getStatistics().getPrepareStatementCount()).isEqualTo(onePositionStatements).isEqualTo(1);
    }

    @Test
    void costProjectionIsChronologicalAndContainsBothSides() {
        User target = MarketFixtures.user(entityManager);
        User other = MarketFixtures.user(entityManager);
        Player player = player("Jugador", "1.3000");
        saveTrade(target, other, player, Instant.parse("2026-01-01T00:01:00Z"));
        saveTrade(other, target, player, Instant.parse("2026-01-01T00:02:00Z"));
        entityManager.flush();
        entityManager.clear();

        assertThat(trades.findCostBasisRows(target.getId()))
                .extracting(UserTradeRow::buyerId)
                .containsExactly(target.getId(), other.getId());
    }

    private Player player(String name, String value) {
        return entityManager.persist(Player.builder().fullName(name).team("Equipo")
                .league(League.PREMIER_LEAGUE).marketValue(new BigDecimal(value)).build());
    }

    private void saveTrade(User buyer, User seller, Player player, Instant time) {
        MarketOrder buy = orders.save(MarketOrder.limit(buyer, player, OrderSide.BUY, 2,
                new BigDecimal("1.1000"), time.minusSeconds(1)));
        MarketOrder sell = orders.save(MarketOrder.limit(seller, player, OrderSide.SELL, 2,
                new BigDecimal("1.1000"), time.minusSeconds(1)));
        trades.save(Trade.of(buy, sell, 2, new BigDecimal("1.1000"), time));
    }
}
