package com.example.demo.support;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.model.market.Holding;
import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderSide;
import com.example.demo.model.market.Trade;

import java.math.BigDecimal;
import java.time.Instant;

public final class PortfolioFixtures {

    public static final Instant BASE_TIME = Instant.parse("2026-01-15T12:00:00Z");

    private PortfolioFixtures() {
    }

    public static User user(long id, String username, Role role, String balance) {
        return User.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .passwordHash("test-hash")
                .role(role)
                .balance(new BigDecimal(balance))
                .build();
    }

    public static Player player(long id, String name, String marketValue) {
        return player(id, name, "Test FC", marketValue);
    }

    public static Player player(long id, String name, String team, String marketValue) {
        return Player.builder()
                .id(id)
                .fullName(name)
                .team(team)
                .league(League.PREMIER_LEAGUE)
                .marketValue(new BigDecimal(marketValue))
                .build();
    }

    public static Holding holding(User user, Player player, int quantity) {
        return Holding.issue(user, player, quantity, BASE_TIME);
    }

    public static MarketOrder order(User user, Player player, OrderSide side, int quantity, String price,
                                    long seconds) {
        return MarketOrder.limit(user, player, side, quantity, new BigDecimal(price),
                BASE_TIME.plusSeconds(seconds));
    }

    public static Trade trade(User buyer, User seller, Player player, int quantity, String price, long seconds) {
        MarketOrder buy = order(buyer, player, OrderSide.BUY, quantity, price, seconds - 1);
        MarketOrder sell = order(seller, player, OrderSide.SELL, quantity, price, seconds - 1);
        return Trade.of(buy, sell, quantity, new BigDecimal(price), BASE_TIME.plusSeconds(seconds));
    }
}
