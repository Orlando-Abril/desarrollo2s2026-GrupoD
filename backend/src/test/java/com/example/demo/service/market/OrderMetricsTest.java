package com.example.demo.service.market;

import com.example.demo.dto.market.OrderRequest;
import com.example.demo.exception.InsufficientBalanceException;
import com.example.demo.exception.InsufficientTokensException;
import com.example.demo.exception.InvalidOrderException;
import com.example.demo.exception.PriceOutOfBandException;
import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.model.market.OrderType;
import com.example.demo.support.UnitTestProfile;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Spec 036, US1: métricas de mercado de {@link OrderService}, comparando valores antes y después de cada orden. */
@SpringBootTest
@UnitTestProfile
class OrderMetricsTest {

    @Autowired private OrderService orderService;
    @Autowired private MeterRegistry registry;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private ApplicationContext context;

    private MarketSeed seed;
    private Player player;
    private User superuser;

    @BeforeEach
    void setUp() {
        MarketTestData.clean(jdbcTemplate);
        seed = MarketSeed.from(context);
        player = seed.player("1.2000");
        superuser = seed.superuser();
    }

    @AfterEach
    void tearDown() {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
        MarketTestData.clean(jdbcTemplate);
    }

    @Test
    void marketBuyThatCrossesCountsTheOrderAndTheTrade() {
        seed.holding(superuser, player, 100);
        seed.sell(superuser, player, 100, "1.2000");
        User buyer = user("1000.0000");
        Snapshot before = snapshot();

        orderService.placeBuy(buyer.getUsername(), market(10));

        Snapshot after = snapshot();
        assertThat(after.created("BUY", "MARKET") - before.created("BUY", "MARKET")).isEqualTo(1);
        assertThat(after.createdTotal() - before.createdTotal()).isEqualTo(1);
        assertThat(after.trades() - before.trades()).isEqualTo(1);
        assertThat(after.tokens() - before.tokens()).isEqualTo(10);
        assertThat(after.volume() - before.volume()).isEqualTo(12.0);
    }

    @Test
    void limitSellWithoutCrossCountsOnlyTheOrder() {
        User seller = user("0");
        seed.holding(seller, player, 5);
        Snapshot before = snapshot();

        orderService.placeSell(seller.getUsername(), limit(5, "1.3000"));

        Snapshot after = snapshot();
        assertThat(after.created("SELL", "LIMIT") - before.created("SELL", "LIMIT")).isEqualTo(1);
        assertThat(after.trades()).isEqualTo(before.trades());
        assertThat(after.tokens()).isEqualTo(before.tokens());
    }

    @Test
    void orderThatCrossesTwoBookOrdersCountsTwoTrades() {
        User c = user("0");
        User d = user("0");
        seed.holding(c, player, 3);
        seed.holding(d, player, 3);
        seed.sell(c, player, 3, "1.2000");
        seed.sell(d, player, 3, "1.2000");
        User buyer = user("1000.0000");
        Snapshot before = snapshot();

        orderService.placeBuy(buyer.getUsername(), market(6));

        Snapshot after = snapshot();
        assertThat(after.trades() - before.trades()).isEqualTo(2);
        assertThat(after.tokens() - before.tokens()).isEqualTo(6);
        assertThat(after.volume() - before.volume()).isCloseTo(7.2, within(1e-9));
        assertThat(after.createdTotal() - before.createdTotal()).isEqualTo(1);
    }

    @Test
    void insufficientBalanceIsCountedAsRejection() {
        seed.holding(superuser, player, 100);
        seed.sell(superuser, player, 100, "1.2000");
        User poor = user("1.0000");
        Snapshot before = snapshot();

        assertThatThrownBy(() -> orderService.placeBuy(poor.getUsername(), market(10)))
                .isInstanceOf(InsufficientBalanceException.class);

        assertRejectedOnly(before, "insufficient_balance");
    }

    @Test
    void insufficientTokensIsCountedAsRejection() {
        User seller = user("0");
        Snapshot before = snapshot();

        assertThatThrownBy(() -> orderService.placeSell(seller.getUsername(), limit(5, "1.2000")))
                .isInstanceOf(InsufficientTokensException.class);

        assertRejectedOnly(before, "insufficient_tokens");
    }

    @Test
    void limitPriceOutOfBandIsCountedAsRejection() {
        User buyer = user("1000.0000");
        Snapshot before = snapshot();

        assertThatThrownBy(() -> orderService.placeBuy(buyer.getUsername(), limit(1, "2.0000")))
                .isInstanceOf(PriceOutOfBandException.class);

        assertRejectedOnly(before, "price_out_of_band");
    }

    @Test
    void otherValidationErrorsAreNotCounted() {
        User buyer = user("1000.0000");
        Snapshot before = snapshot();

        assertThatThrownBy(() -> orderService.placeBuy(buyer.getUsername(),
                new OrderRequest(player.getId(), 1, OrderType.LIMIT, null)))
                .isInstanceOf(InvalidOrderException.class);

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void ordersAndTradesOfARolledBackTransactionAreNotCounted() {
        seed.holding(superuser, player, 100);
        seed.sell(superuser, player, 100, "1.2000");
        User buyer = user("1000.0000");
        Snapshot before = snapshot();

        transactionTemplate.executeWithoutResult(status -> {
            orderService.placeBuy(buyer.getUsername(), market(10));
            status.setRollbackOnly();
        });

        assertThat(snapshot()).isEqualTo(before);
    }

    private void assertRejectedOnly(Snapshot before, String reason) {
        Snapshot after = snapshot();
        for (String each : new String[] {"insufficient_balance", "insufficient_tokens", "price_out_of_band"}) {
            assertThat(after.rejected(each) - before.rejected(each)).as(each).isEqualTo(each.equals(reason) ? 1 : 0);
        }
        assertThat(after.createdTotal()).isEqualTo(before.createdTotal());
        assertThat(after.trades()).isEqualTo(before.trades());
    }

    private User user(String balance) {
        return seed.user("metrics-" + UUID.randomUUID(), balance);
    }

    private OrderRequest market(int quantity) {
        return new OrderRequest(player.getId(), quantity, OrderType.MARKET, null);
    }

    private OrderRequest limit(int quantity, String limitPrice) {
        return new OrderRequest(player.getId(), quantity, OrderType.LIMIT, new BigDecimal(limitPrice));
    }

    private Snapshot snapshot() {
        return new Snapshot(
                created("BUY", "MARKET"), created("BUY", "LIMIT"), created("SELL", "MARKET"), created("SELL", "LIMIT"),
                rejected("insufficient_balance"), rejected("insufficient_tokens"), rejected("price_out_of_band"),
                registry.get("gdmarket.trades.executed").counter().count(),
                registry.get("gdmarket.trades.tokens").counter().count(),
                registry.get("gdmarket.trades.volume.credits").counter().count());
    }

    private double created(String side, String type) {
        return registry.get("gdmarket.orders.placed").tags("side", side, "type", type).counter().count();
    }

    private double rejected(String reason) {
        return registry.get("gdmarket.orders.rejected").tag("reason", reason).counter().count();
    }

    private record Snapshot(double buyMarket, double buyLimit, double sellMarket, double sellLimit,
                            double balance, double tokensRejected, double band,
                            double trades, double tokens, double volume) {

        double created(String side, String type) {
            return switch (side + type) {
                case "BUYMARKET" -> buyMarket;
                case "BUYLIMIT" -> buyLimit;
                case "SELLMARKET" -> sellMarket;
                default -> sellLimit;
            };
        }

        double createdTotal() {
            return buyMarket + buyLimit + sellMarket + sellLimit;
        }

        double rejected(String reason) {
            return switch (reason) {
                case "insufficient_balance" -> balance;
                case "insufficient_tokens" -> tokensRejected;
                default -> band;
            };
        }
    }
}
