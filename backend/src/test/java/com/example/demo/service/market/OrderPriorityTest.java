package com.example.demo.service.market;

import com.example.demo.dto.market.OrderRequest;
import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderStatus;
import com.example.demo.model.market.OrderType;
import com.example.demo.model.market.Trade;
import com.example.demo.repository.market.MarketOrderRepository;
import com.example.demo.repository.market.TradeRepository;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@UnitTestProfile
class OrderPriorityTest {

    @Autowired private OrderService orderService;
    @Autowired private MarketOrderRepository orderRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private ApplicationContext context;

    private MarketSeed seed;
    private Player player;
    private User buyer;

    @BeforeEach
    void setUp() {
        MarketTestData.clean(jdbcTemplate);
        seed = MarketSeed.from(context);
        player = seed.player("1.1500");
        buyer = seed.user("buyer-" + UUID.randomUUID(), "1000.0000");
    }

    @AfterEach
    void tearDown() {
        MarketTestData.clean(jdbcTemplate);
    }

    @Test
    void sweepsTheBookByPriceAndThenByAge() {
        User c = seller("c");
        User d = seller("d");
        User superuser = seed.superuser();
        seed.holding(superuser, player, 50);
        MarketOrder sellC = seed.sell(c, player, 3, "1.1000");
        MarketOrder sellD = seed.sell(d, player, 3, "1.1000");
        MarketOrder sellSuperuser = seed.sell(superuser, player, 50, "1.2000");

        orderService.placeBuy(buyer.getUsername(), market(8));

        assertThat(trades()).extracting(t -> t.sellOrderId, t -> t.quantity, t -> t.price.setScale(4))
                .containsExactly(
                        tuple(sellC.getId(), 3, new BigDecimal("1.1000")),
                        tuple(sellD.getId(), 3, new BigDecimal("1.1000")),
                        tuple(sellSuperuser.getId(), 2, new BigDecimal("1.2000")));
    }

    @Test
    void marketSellTakesTheHighestBuyFirst() {
        User seller = seller("s");
        seed.buy(seed.user("low-" + UUID.randomUUID(), "1000.0000"), player, 5, "1.0000");
        MarketOrder high = seed.buy(seed.user("high-" + UUID.randomUUID(), "1000.0000"), player, 5, "1.1500");

        orderService.placeSell(seller.getUsername(), market(1));

        assertThat(trades()).singleElement().satisfies(t -> assertThat(t.buyOrderId).isEqualTo(high.getId()));
    }

    @Test
    void limitBuyOnlyExecutesAtItsLimitOrBetter() {
        User c = seller("c");
        User d = seller("d");
        seed.sell(c, player, 2, "1.1000");
        seed.sell(d, player, 3, "1.2000");

        OrderResult result = orderService.placeBuy(buyer.getUsername(), limit(5, "1.1000"));

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(result.order().getFilledQuantity()).isEqualTo(2);
        assertThat(orderRepository.findOpenBuys(player.getId())).extracting(MarketOrder::getId)
                .containsExactly(result.order().getId());
    }

    @Test
    void filledBookOrdersLeaveTheBookAndPartialOnesKeepTheirAge() {
        User c = seller("c");
        User d = seller("d");
        MarketOrder sellC = seed.sell(c, player, 3, "1.1000");
        MarketOrder sellD = seed.sell(d, player, 3, "1.1000");

        orderService.placeBuy(buyer.getUsername(), market(4));

        MarketOrder filledC = orderRepository.findById(sellC.getId()).orElseThrow();
        assertThat(filledC.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(filledC.getClosedAt()).isNotNull();
        MarketOrder partialD = orderRepository.findById(sellD.getId()).orElseThrow();
        assertThat(partialD.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(partialD.getCreatedAt()).isEqualTo(sellD.getCreatedAt());

        User e = seller("e");
        seed.sell(e, player, 3, "1.1000");
        assertThat(orderRepository.findOpenSells(player.getId())).extracting(MarketOrder::getId)
                .first().isEqualTo(sellD.getId());
    }

    @Test
    void neverTradesWithItself() {
        seed.holding(buyer, player, 5);
        seed.sell(buyer, player, 5, "1.1000");
        User other = seller("o");
        MarketOrder otherSell = seed.sell(other, player, 5, "1.2000");

        orderService.placeBuy(buyer.getUsername(), market(3));

        assertThat(trades()).singleElement().satisfies(t -> {
            assertThat(t.sellOrderId).isEqualTo(otherSell.getId());
            assertThat(t.buyerId).isNotEqualTo(t.sellerId);
        });
    }

    private User seller(String prefix) {
        User seller = seed.user(prefix + "-" + UUID.randomUUID(), "0");
        seed.holding(seller, player, 10);
        return seller;
    }

    private List<TradeRow> trades() {
        return transactionTemplate.execute(status -> tradeRepository.findByPlayerIdOrderByExecutedAtDescIdDesc(
                        player.getId()).stream()
                .sorted(Comparator.comparing(Trade::getId))
                .map(TradeRow::of)
                .toList());
    }

    private OrderRequest market(int quantity) {
        return new OrderRequest(player.getId(), quantity, OrderType.MARKET, null);
    }

    private OrderRequest limit(int quantity, String limitPrice) {
        return new OrderRequest(player.getId(), quantity, OrderType.LIMIT, new BigDecimal(limitPrice));
    }

    private record TradeRow(Long buyOrderId, Long sellOrderId, Long buyerId, Long sellerId, int quantity,
                            BigDecimal price) {

        static TradeRow of(Trade trade) {
            return new TradeRow(trade.getBuyOrder().getId(), trade.getSellOrder().getId(), trade.getBuyer().getId(),
                    trade.getSeller().getId(), trade.getQuantity(), trade.getPrice());
        }
    }
}
