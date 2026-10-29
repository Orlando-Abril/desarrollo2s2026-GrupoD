package com.example.demo.service.market;

import com.example.demo.dto.market.OrderRequest;
import com.example.demo.exception.InsufficientTokensException;
import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderStatus;
import com.example.demo.model.market.OrderType;
import com.example.demo.repository.market.MarketOrderRepository;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@UnitTestProfile
class OrderServiceSellTest {

    @Autowired private OrderService orderService;
    @Autowired private MarketOrderRepository orderRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ApplicationContext context;

    private MarketSeed seed;
    private Player player;
    private User a;
    private User b;

    @BeforeEach
    void setUp() {
        MarketTestData.clean(jdbcTemplate);
        seed = MarketSeed.from(context);
        player = seed.player("1.1000");
        seed.superuser();
        a = seed.user("a-" + UUID.randomUUID(), "1000.0000");
        b = seed.user("b-" + UUID.randomUUID(), "1000.0000");
        seed.holding(a, player, 10);
    }

    @AfterEach
    void tearDown() {
        MarketTestData.clean(jdbcTemplate);
    }

    @Test
    void marketSellFillsAgainstARestingBuy() {
        seed.buy(b, player, 5, "1.1000");

        OrderResult result = orderService.placeSell(a.getUsername(), market(5));

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(result.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.getPrice()).isEqualByComparingTo("1.1000");
            assertThat(trade.getTotal()).isEqualByComparingTo("5.5000");
        });
        assertThat(balance(a)).isEqualByComparingTo("1005.5000");
        assertThat(balance(b)).isEqualByComparingTo("994.5000");
        assertThat(holding(a)).containsExactly(5, 0);
        assertThat(holding(b)).containsExactly(5, 0);
        assertThat(orderRepository.sumReservedCredits(b.getId())).isEqualByComparingTo("0");
    }

    @Test
    void limitSellWithoutBuyersRestsAndReservesTokens() {
        OrderResult result = orderService.placeSell(a.getUsername(), limit(6, "1.2000"));

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(holding(a)).containsExactly(10, 6);
        assertThat(jdbcTemplate.queryForList("select action from audit_logs order by id", String.class))
                .containsExactly("ORDER_CREATED", "HOLDING_CHANGED");
    }

    @Test
    void sellingReservedTokensIsRejectedWithoutTrace() {
        seed.sell(a, player, 6, "1.2000");

        assertThatThrownBy(() -> orderService.placeSell(a.getUsername(), limit(5, "1.2000")))
                .isInstanceOf(InsufficientTokensException.class);

        assertThat(holding(a)).containsExactly(10, 6);
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from trades", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from audit_logs", Long.class)).isZero();
    }

    @Test
    void marketSellWithoutBuyersIsCancelledBecauseTheSuperuserNeverBuys() {
        OrderResult result = orderService.placeSell(a.getUsername(), market(5));

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.order().getFilledQuantity()).isZero();
        assertThat(result.trades()).isEmpty();
        assertThat(holding(a)).containsExactly(10, 0);
    }

    @Test
    void aBuyerTakesARestingUserSellAtItsPrice() {
        MarketOrder sell = seed.sell(a, player, 6, "1.2000");

        OrderResult result = orderService.placeBuy(b.getUsername(), market(4));

        assertThat(result.trades()).singleElement()
                .satisfies(trade -> assertThat(trade.getPrice()).isEqualByComparingTo("1.2000"));
        assertThat(holding(a)).containsExactly(6, 2);
        assertThat(orderRepository.findById(sell.getId())).get()
                .satisfies(order -> assertThat(order.remaining()).isEqualTo(2));
    }

    @Test
    void sellingWithoutAHoldingIsRejected() {
        assertThatThrownBy(() -> orderService.placeSell(b.getUsername(), market(1)))
                .isInstanceOf(InsufficientTokensException.class);
    }

    private OrderRequest market(int quantity) {
        return new OrderRequest(player.getId(), quantity, OrderType.MARKET, null);
    }

    private OrderRequest limit(int quantity, String limitPrice) {
        return new OrderRequest(player.getId(), quantity, OrderType.LIMIT, new BigDecimal(limitPrice));
    }

    private BigDecimal balance(User user) {
        return jdbcTemplate.queryForObject("select balance from users where id = ?", BigDecimal.class, user.getId());
    }

    private List<Integer> holding(User user) {
        return jdbcTemplate.queryForObject(
                "select quantity, reserved_quantity from holdings where user_id = ? and player_id = ?",
                (rs, row) -> List.of(rs.getInt(1), rs.getInt(2)), user.getId(), player.getId());
    }
}
