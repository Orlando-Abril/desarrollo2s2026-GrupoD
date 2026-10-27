package com.example.demo.repository.market;

import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderSide;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@UnitTestProfile
class OrderBookQueryTest {

    private static final Instant T1 = Instant.parse("2026-10-27T10:00:00Z");
    private static final Instant T1_EARLIER = T1.minusSeconds(30);
    private static final Instant T2 = T1.plusSeconds(60);

    @Autowired private MarketOrderRepository repository;
    @Autowired private TestEntityManager entityManager;

    private User user;
    private Player player;

    @BeforeEach
    void setUp() {
        user = MarketFixtures.user(entityManager);
        player = MarketFixtures.player(entityManager);
    }

    private MarketOrder limit(Player of, OrderSide side, String price, Instant createdAt) {
        return repository.save(MarketOrder.limit(user, of, side, 5, new BigDecimal(price), createdAt));
    }

    @Test
    void sellsGoFromLowestToHighestPriceAndOldestFirst() {
        MarketOrder at110 = limit(player, OrderSide.SELL, "1.10", T1);
        MarketOrder at105Newer = limit(player, OrderSide.SELL, "1.05", T2);
        MarketOrder at105Older = limit(player, OrderSide.SELL, "1.05", T1_EARLIER);
        MarketOrder at120 = limit(player, OrderSide.SELL, "1.20", T1);
        limit(MarketFixtures.player(entityManager), OrderSide.SELL, "1.00", T1);

        assertThat(repository.findOpenSells(player.getId())).containsExactly(at105Older, at105Newer, at110, at120);
    }

    @Test
    void buysGoFromHighestToLowestPriceAndOldestFirst() {
        MarketOrder at095 = limit(player, OrderSide.BUY, "0.95", T1);
        MarketOrder at100Newer = limit(player, OrderSide.BUY, "1.00", T2);
        MarketOrder at100Older = limit(player, OrderSide.BUY, "1.00", T1);
        MarketOrder at090 = limit(player, OrderSide.BUY, "0.90", T1);

        assertThat(repository.findOpenBuys(player.getId())).containsExactly(at100Older, at100Newer, at095, at090);
    }

    @Test
    void leavesOutClosedMarketAndOtherPlayersOrders() {
        MarketOrder open = limit(player, OrderSide.SELL, "1.00", T1);
        MarketOrder partial = limit(player, OrderSide.SELL, "1.01", T1);
        partial.fill(2, T2);
        limit(player, OrderSide.SELL, "1.02", T1).fill(5, T2);
        limit(player, OrderSide.SELL, "1.03", T1).cancel(T2);
        repository.save(MarketOrder.market(user, player, OrderSide.SELL, 5, T1));
        limit(MarketFixtures.player(entityManager), OrderSide.SELL, "1.00", T1);

        assertThat(repository.findOpenSells(player.getId())).containsExactly(open, partial);
        assertThat(repository.findOpenSellsForUpdate(player.getId())).containsExactly(open, partial);
    }

    @Test
    void theSmallerIdBreaksTiesAtTheSameInstant() {
        MarketOrder first = limit(player, OrderSide.BUY, "1.00", T1);
        MarketOrder second = limit(player, OrderSide.BUY, "1.00", T1);

        assertThat(repository.findOpenBuys(player.getId())).containsExactly(first, second);
        assertThat(repository.findOpenBuysForUpdate(player.getId())).containsExactly(first, second);
    }
}
