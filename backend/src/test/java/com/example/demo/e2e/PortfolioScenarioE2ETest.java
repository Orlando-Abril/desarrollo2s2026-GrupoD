package com.example.demo.e2e;

import com.example.demo.dto.market.PortfolioResponse;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.model.market.Holding;
import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderSide;
import com.example.demo.model.market.Trade;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.market.HoldingRepository;
import com.example.demo.repository.market.MarketOrderRepository;
import com.example.demo.repository.market.TradeRepository;
import com.example.demo.service.market.PortfolioService;
import com.example.demo.service.market.SuperuserAccount;
import com.example.demo.service.market.SuperuserInitializer;
import com.example.demo.service.market.TokenIssuanceService;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.E2ETest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@E2ETest
@TestPropertySource(properties = "market.superuser.enabled=true")
class PortfolioScenarioE2ETest {

    private static final Instant T0 = Instant.parse("2026-10-29T12:00:00Z");

    @Autowired SuperuserInitializer initializer;
    @Autowired SuperuserAccount superuserAccount;
    @Autowired TokenIssuanceService issuance;
    @Autowired UserRepository users;
    @Autowired PlayerRepository players;
    @Autowired HoldingRepository holdings;
    @Autowired MarketOrderRepository orders;
    @Autowired TradeRepository trades;
    @Autowired PortfolioService portfolioService;

    private User superuser;

    @BeforeEach
    void setUp() {
        initializer.run(null);
        superuser = users.findById(superuserAccount.id()).orElseThrow();
    }

    @Test
    void fourUsersTradeFivePlayersAndReceiveCorrectPortfolios() {
        List<User> participants = List.of(user("one"), user("two"), user("three"), user("four"));
        List<Player> assets = List.of(player("A", "1.3000"), player("B", "0.9000"),
                player("C", "1.1000"), player("D", "1.5000"), player("E", "2.0000"));
        issuance.issueMissing();

        User one = participants.get(0);
        User two = participants.get(1);
        trade(one, superuser, assets.get(0), 10, "1.0000", 1);
        trade(one, superuser, assets.get(0), 5, "1.2000", 2);
        trade(two, one, assets.get(0), 6, "1.5000", 3);
        trade(one, superuser, assets.get(0), 4, "1.1000", 4);
        trade(participants.get(2), superuser, assets.get(1), 2, "0.9000", 5);
        trade(participants.get(3), superuser, assets.get(2), 3, "1.1000", 6);
        trade(participants.get(2), superuser, assets.get(3), 1, "1.5000", 7);
        trade(participants.get(3), superuser, assets.get(4), 1, "2.0000", 8);

        Holding oneHolding = Holding.issue(one, assets.get(0), 13, T0.plusSeconds(10));
        oneHolding.reserve(2, T0.plusSeconds(11));
        holdings.save(oneHolding);
        holdings.save(Holding.issue(two, assets.get(0), 6, T0.plusSeconds(10)));
        holdings.save(Holding.issue(participants.get(2), assets.get(1), 2, T0.plusSeconds(10)));
        holdings.save(Holding.issue(participants.get(2), assets.get(3), 1, T0.plusSeconds(10)));
        holdings.save(Holding.issue(participants.get(3), assets.get(2), 3, T0.plusSeconds(10)));
        holdings.save(Holding.issue(participants.get(3), assets.get(4), 1, T0.plusSeconds(10)));

        PortfolioResponse response = portfolioService.portfolio(one.getId(), admin());

        assertThat(response.positions()).singleElement().satisfies(position -> {
            assertThat(position.quantity()).isEqualTo(13);
            assertThat(position.reservedQuantity()).isEqualTo(2);
            assertThat(position.averagePurchasePrice()).isEqualByComparingTo("1.0769");
            assertThat(position.costBasis()).isEqualByComparingTo("14.0000");
            assertThat(position.currentValue()).isEqualByComparingTo("16.9000");
            assertThat(position.profitLoss()).isEqualByComparingTo("2.9000");
        });
        assertThat(portfolioService.portfolio(participants.get(2).getId(), admin()).positions()).hasSize(2);
        assertThat(portfolioService.portfolio(participants.get(3).getId(), admin()).positions()).hasSize(2);
    }

    private User user(String suffix) {
        return AuthFixtures.user(users, "portfolio-" + suffix + "-" + UUID.randomUUID(), Role.USER);
    }

    private Player player(String suffix, String value) {
        return players.save(Player.builder().externalId("portfolio-" + suffix + "-" + UUID.randomUUID())
                .fullName("Player " + suffix).team("Club").league(League.PREMIER_LEAGUE)
                .marketValue(new BigDecimal(value)).build());
    }

    private void trade(User buyer, User seller, Player player, int quantity, String price, long second) {
        Instant time = T0.plusSeconds(second);
        MarketOrder buy = orders.save(MarketOrder.limit(buyer, player, OrderSide.BUY, quantity,
                new BigDecimal(price), time.minusMillis(1)));
        MarketOrder sell = orders.save(MarketOrder.limit(seller, player, OrderSide.SELL, quantity,
                new BigDecimal(price), time.minusMillis(1)));
        trades.save(Trade.of(buy, sell, quantity, new BigDecimal(price), time));
    }

    private UsernamePasswordAuthenticationToken admin() {
        return new UsernamePasswordAuthenticationToken("e2e-admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
