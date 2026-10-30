package com.example.demo.e2e;

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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

@E2ETest
@TestPropertySource(properties = "market.superuser.enabled=true")
class PortfolioConsistencyE2ETest {

    private static final Instant T0 = Instant.parse("2026-10-29T12:00:00Z");

    @Autowired SuperuserInitializer initializer;
    @Autowired SuperuserAccount superuserAccount;
    @Autowired TokenIssuanceService issuance;
    @Autowired UserRepository users;
    @Autowired PlayerRepository players;
    @MockitoSpyBean HoldingRepository holdings;
    @Autowired MarketOrderRepository orders;
    @Autowired TradeRepository trades;
    @Autowired PortfolioService portfolioService;
    @Autowired TransactionTemplate transactions;

    private User buyer;
    private User seller;
    private Player player;

    @BeforeEach
    void setUp() {
        initializer.run(null);
        seller = users.findById(superuserAccount.id()).orElseThrow();
        buyer = AuthFixtures.user(users, "snapshot-" + UUID.randomUUID(), Role.USER);
        player = players.save(Player.builder().externalId("snapshot-" + UUID.randomUUID()).fullName("Snapshot")
                .team("Club").league(League.LA_LIGA).marketValue(new BigDecimal("1.5000")).build());
        issuance.issueMissing();
        transactions.executeWithoutResult(status -> {
            trade(10, "1.0000", T0);
            holdings.save(Holding.issue(buyer, player, 10, T0));
        });
    }

    @Test
    void aTradeCommittedBetweenPortfolioQueriesNeverProducesAMixedResponse() throws Exception {
        CountDownLatch holdingRead = new CountDownLatch(1);
        CountDownLatch writerCommitted = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object rows = invocation.callRealMethod();
            holdingRead.countDown();
            assertThat(writerCommitted.await(10, TimeUnit.SECONDS)).isTrue();
            return rows;
        }).when(holdings).findPortfolioRows(buyer.getId());

        CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> {
            await(holdingRead);
            transactions.executeWithoutResult(status -> {
                Holding holding = holdings.findForUpdate(buyer.getId(), player.getId()).orElseThrow();
                holding.credit(5, T0.plusSeconds(2));
                holdings.save(holding);
                trade(5, "1.2000", T0.plusSeconds(2));
            });
            writerCommitted.countDown();
        });

        var authentication = new UsernamePasswordAuthenticationToken("service", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        var response = portfolioService.portfolio(buyer.getId(), authentication);
        writer.get(10, TimeUnit.SECONDS);

        assertThat(response.positions()).singleElement().satisfies(position -> {
            assertThat(position.quantity()).isEqualTo(10);
            assertThat(position.costBasis()).isEqualByComparingTo("10.0000");
            assertThat(position.averagePurchasePrice()).isEqualByComparingTo("1.0000");
        });
        assertThat(portfolioService.portfolio(buyer.getId(), authentication).positions().get(0).quantity())
                .isEqualTo(15);
    }

    private void trade(int quantity, String price, Instant time) {
        MarketOrder buy = orders.save(MarketOrder.limit(buyer, player, OrderSide.BUY, quantity,
                new BigDecimal(price), time.minusMillis(1)));
        MarketOrder sell = orders.save(MarketOrder.limit(seller, player, OrderSide.SELL, quantity,
                new BigDecimal(price), time.minusMillis(1)));
        trades.save(Trade.of(buy, sell, quantity, new BigDecimal(price), time));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
