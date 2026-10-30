package com.example.demo.controller;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.model.market.Holding;
import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderSide;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.market.HoldingRepository;
import com.example.demo.repository.market.MarketOrderRepository;
import com.example.demo.service.market.PortfolioService;
import com.example.demo.service.market.SuperuserAccount;
import com.example.demo.service.market.SuperuserInitializer;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.E2ETest;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

@E2ETest
@TestPropertySource(properties = {"market.superuser.enabled=true",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
class UserMarketPerformanceTest {

    private static final int TRADE_COUNT = 10_000;
    private static final Duration LIMIT = Duration.ofSeconds(2);

    @Autowired SuperuserInitializer initializer;
    @Autowired SuperuserAccount superuserAccount;
    @Autowired UserRepository users;
    @Autowired PlayerRepository players;
    @Autowired HoldingRepository holdings;
    @Autowired MarketOrderRepository orders;
    @Autowired PortfolioService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;

    private User buyer;
    private UsernamePasswordAuthenticationToken admin;

    @BeforeEach
    void seed() {
        initializer.run(null);
        User seller = users.findById(superuserAccount.id()).orElseThrow();
        buyer = AuthFixtures.user(users, "performance-" + UUID.randomUUID(), Role.USER);
        Player player = players.save(Player.builder().externalId("performance-" + UUID.randomUUID())
                .fullName("Performance").team("Club").league(League.SERIE_A)
                .marketValue(new BigDecimal("1.2500")).build());
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        MarketOrder buy = orders.save(MarketOrder.limit(buyer, player, OrderSide.BUY, TRADE_COUNT,
                BigDecimal.ONE, now));
        MarketOrder sell = orders.save(MarketOrder.limit(seller, player, OrderSide.SELL, TRADE_COUNT,
                BigDecimal.ONE, now));
        holdings.save(Holding.issue(buyer, player, TRADE_COUNT, now));
        List<Object[]> rows = new ArrayList<>(TRADE_COUNT);
        for (int i = 0; i < TRADE_COUNT; i++) {
            rows.add(new Object[]{player.getId(), buyer.getId(), seller.getId(), buy.getId(), sell.getId(),
                    Timestamp.from(now.plusSeconds(i))});
        }
        jdbc.batchUpdate("insert into trades (player_id,buyer_id,seller_id,buy_order_id,sell_order_id,quantity,"
                + "price,total,executed_at) values (?,?,?,?,?,1,1.0000,1.0000,?)", rows);
        admin = new UsernamePasswordAuthenticationToken("performance-admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void portfolioAndHistoryStayUnderTwoSecondsWithConstantStatementCounts() {
        service.portfolio(buyer.getId(), admin);
        service.transactions(buyer.getId(), admin, 0, 100);
        SessionFactory factory = entityManagerFactory.unwrap(SessionFactory.class);
        long portfolioStatements = measure(factory, () -> service.portfolio(buyer.getId(), admin));
        long transactionStatements = measure(factory, () -> service.transactions(buyer.getId(), admin, 0, 100));
        for (int i = 0; i < 4; i++) {
            int page = i;
            assertThat(measure(factory, () -> service.portfolio(buyer.getId(), admin)))
                    .isEqualTo(portfolioStatements);
            assertThat(measure(factory, () -> service.transactions(buyer.getId(), admin, page, 100)))
                    .isEqualTo(transactionStatements);
        }
    }

    private long measure(SessionFactory factory, Runnable operation) {
        factory.getStatistics().clear();
        assertTimeout(LIMIT, operation::run);
        return factory.getStatistics().getPrepareStatementCount();
    }
}
