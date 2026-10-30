package com.example.demo.service.market;

import com.example.demo.dto.market.OrderRequest;
import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderStatus;
import com.example.demo.model.market.OrderType;
import com.example.demo.repository.market.HoldingRepository;
import com.example.demo.repository.market.MarketOrderRepository;
import com.example.demo.repository.market.TradeRepository;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

@SpringBootTest
@UnitTestProfile
class OrderServiceBuyTest {

    @Autowired private OrderService orderService;
    @Autowired private MarketOrderRepository orderRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ApplicationContext context;
    @MockitoSpyBean private TradeRepository tradeRepository;

    private MarketSeed seed;
    private Player player;
    private User superuser;
    private User buyer;

    @BeforeEach
    void setUp() {
        MarketTestData.clean(jdbcTemplate);
        seed = MarketSeed.from(context);
        player = seed.player("1.2000");
        superuser = seed.superuser();
        buyer = seed.user("buyer-" + UUID.randomUUID(), "1000.0000");
    }

    @AfterEach
    void tearDown() {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
        MarketTestData.clean(jdbcTemplate);
    }

    @Test
    void marketBuyFillsAgainstTheSuperuserAtMarketValue() {
        seed.holding(superuser, player, 100);
        MarketOrder superuserSell = seed.sell(superuser, player, 100, "1.2000");

        OrderResult result = orderService.placeBuy(buyer.getUsername(), market(10));

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(result.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.getQuantity()).isEqualTo(10);
            assertThat(trade.getPrice()).isEqualByComparingTo("1.2000");
            assertThat(trade.getTotal()).isEqualByComparingTo("12.0000");
        });
        assertThat(balance(buyer)).isEqualByComparingTo("988.0000");
        assertThat(balance(superuser)).isEqualByComparingTo("1012.0000");
        assertThat(holding(buyer)).containsExactly(10, 0);
        assertThat(holding(superuser)).containsExactly(90, 90);
        assertThat(orderRepository.findById(superuserSell.getId())).get().satisfies(order -> {
            assertThat(order.remaining()).isEqualTo(90);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        });
    }

    @Test
    void limitBuyPaysThePriceOfTheRestingSell() {
        seed.holding(superuser, player, 100);
        seed.sell(superuser, player, 100, "1.2000");

        OrderResult result = orderService.placeBuy(buyer.getUsername(), limit(10, "1.3000"));

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(result.trades()).singleElement()
                .satisfies(trade -> assertThat(trade.getPrice()).isEqualByComparingTo("1.2000"));
        assertThat(balance(buyer)).isEqualByComparingTo("988.0000");
    }

    @Test
    void limitBuyThatDoesNotCrossRestsAndReservesCredits() {
        seed.holding(superuser, player, 100);
        seed.sell(superuser, player, 100, "1.2000");

        OrderResult result = orderService.placeBuy(buyer.getUsername(), limit(10, "1.1000"));

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(result.trades()).isEmpty();
        assertThat(balance(buyer)).isEqualByComparingTo("1000.0000");
        assertThat(orderRepository.sumReservedCredits(buyer.getId())).isEqualByComparingTo("11.0000");
    }

    @Test
    void limitBuyPartiallyFilledReservesTheRestAtItsLimit() {
        seed.holding(superuser, player, 4);
        seed.sell(superuser, player, 4, "1.2000");

        OrderResult result = orderService.placeBuy(buyer.getUsername(), limit(10, "1.3000"));

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(result.order().getFilledQuantity()).isEqualTo(4);
        assertThat(orderRepository.sumReservedCredits(buyer.getId())).isEqualByComparingTo("7.8000");
        assertThat(balance(buyer)).isEqualByComparingTo("995.2000");
    }

    @Test
    void marketBuyCancelsWhatItCannotFill() {
        seed.holding(superuser, player, 4);
        seed.sell(superuser, player, 4, "1.2000");

        OrderResult result = orderService.placeBuy(buyer.getUsername(), market(10));

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.order().getFilledQuantity()).isEqualTo(4);
        assertThat(result.order().getClosedAt()).isNotNull();
        assertThat(orderRepository.sumReservedCredits(buyer.getId())).isEqualByComparingTo("0");
    }

    @Test
    void eachFillIsAuditedWithTheSameCorrelationIdAndTheBuyerAsAuthor() {
        seed.holding(superuser, player, 100);
        seed.sell(superuser, player, 100, "1.2000");
        String correlationId = UUID.randomUUID().toString();
        MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);

        orderService.placeBuy(buyer.getUsername(), market(10));

        List<Map<String, Object>> audits = jdbcTemplate.queryForList(
                "select action, entity_type, actor_type, actor_user_id, correlation_id, before_state"
                        + " from audit_logs order by id");
        assertThat(audits).extracting(row -> row.get("ACTION")).containsExactly(
                "ORDER_CREATED", "TRADE_EXECUTED", "ORDER_FILLED", "ORDER_FILLED",
                "BALANCE_CHANGED", "BALANCE_CHANGED", "HOLDING_CHANGED", "HOLDING_CHANGED");
        assertThat(audits).allSatisfy(row -> {
            assertThat(row.get("CORRELATION_ID")).isEqualTo(correlationId);
            assertThat(row.get("ACTOR_TYPE")).isEqualTo("USER");
            assertThat(((Number) row.get("ACTOR_USER_ID")).longValue()).isEqualTo(buyer.getId());
        });
        Map<String, Object> buyerHoldingCreated = audits.get(6);
        assertThat(buyerHoldingCreated.get("BEFORE_STATE")).isNull();
    }

    @Test
    void conservesBalancesAndTheHundredTokens() {
        seed.holding(superuser, player, 100);
        seed.sell(superuser, player, 100, "1.2000");
        BigDecimal totalBefore = totalBalance();

        orderService.placeBuy(buyer.getUsername(), market(7));
        orderService.placeBuy(buyer.getUsername(), limit(5, "1.3000"));

        assertThat(totalBalance()).isEqualByComparingTo(totalBefore);
        assertThat(holdingRepository.sumQuantityByPlayerId(player.getId())).isEqualTo(100);
    }

    @Test
    void aFailureInTheSecondFillLeavesNothingBehind() {
        User c = seed.user("c-" + UUID.randomUUID(), "0");
        User d = seed.user("d-" + UUID.randomUUID(), "0");
        seed.holding(c, player, 3);
        seed.holding(d, player, 3);
        MarketOrder sellC = seed.sell(c, player, 3, "1.1000");
        MarketOrder sellD = seed.sell(d, player, 3, "1.1000");
        // El repositorio es una interfaz: la primera llamada se delega a la respuesta por defecto del spy, que la
        // reenvía al repositorio real; la segunda falla.
        Answer<?> delegate = mockingDetails(tradeRepository).getMockCreationSettings().getDefaultAnswer();
        AtomicInteger saves = new AtomicInteger();
        doAnswer(invocation -> {
            if (saves.incrementAndGet() == 2) {
                throw new IllegalStateException("second_trade_failed");
            }
            return delegate.answer(invocation);
        }).when(tradeRepository).save(any());

        assertThatThrownBy(() -> orderService.placeBuy(buyer.getUsername(), market(6)))
                .hasMessage("second_trade_failed");

        assertThat(jdbcTemplate.queryForObject("select count(*) from trades", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from audit_logs", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders where user_id = ?", Long.class,
                buyer.getId())).isZero();
        assertThat(balance(buyer)).isEqualByComparingTo("1000.0000");
        assertThat(balance(c)).isEqualByComparingTo("0");
        assertThat(holding(c)).containsExactly(3, 3);
        assertThat(holding(d)).containsExactly(3, 3);
        assertThat(holdingRepository.findByUserIdAndPlayerId(buyer.getId(), player.getId())).isEmpty();
        assertThat(orderRepository.findById(sellC.getId())).get()
                .satisfies(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.OPEN));
        assertThat(orderRepository.findById(sellD.getId())).get()
                .satisfies(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.OPEN));
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

    private BigDecimal totalBalance() {
        return jdbcTemplate.queryForObject("select sum(balance) from users", BigDecimal.class);
    }
}
