package com.example.demo.service.market;

import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderStatus;
import com.example.demo.repository.market.MarketOrderRepository;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@UnitTestProfile
class OrderServiceCancelTest {

    @Autowired private OrderService orderService;
    @Autowired private MarketOrderRepository orderRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ApplicationContext context;

    private MarketSeed seed;
    private Player player;
    private User owner;

    @BeforeEach
    void setUp() {
        MarketTestData.clean(jdbcTemplate);
        seed = MarketSeed.from(context);
        player = seed.player("1.1000");
        owner = seed.user("owner-" + UUID.randomUUID(), "1000.0000");
    }

    @AfterEach
    void tearDown() {
        MarketTestData.clean(jdbcTemplate);
    }

    @Test
    void cancellingAPartiallyFilledBuyReleasesItsCredits() {
        MarketOrder buy = seed.fill(seed.buy(owner, player, 10, "1.1000"), 4);
        assertThat(orderRepository.sumReservedCredits(owner.getId())).isEqualByComparingTo("6.6000");

        OrderResult result = orderService.cancel(owner.getUsername(), buy.getId());

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.order().getFilledQuantity()).isEqualTo(4);
        assertThat(result.order().getClosedAt()).isNotNull();
        assertThat(orderRepository.sumReservedCredits(owner.getId())).isEqualByComparingTo("0");
        assertThat(orderRepository.findOpenBuys(player.getId())).isEmpty();
    }

    @Test
    void cancellingASellReleasesItsTokensAndIsAudited() {
        seed.holding(owner, player, 10);
        MarketOrder sell = seed.sell(owner, player, 6, "1.2000");

        orderService.cancel(owner.getUsername(), sell.getId());

        assertThat(jdbcTemplate.queryForObject(
                "select reserved_quantity from holdings where user_id = ? and player_id = ?", Integer.class,
                owner.getId(), player.getId())).isZero();
        List<Map<String, Object>> audits = jdbcTemplate.queryForList(
                "select action, actor_user_id, before_state from audit_logs order by id");
        assertThat(audits).extracting(row -> row.get("ACTION")).containsExactly("ORDER_CANCELLED", "HOLDING_CHANGED");
        assertThat(audits).allSatisfy(row -> {
            assertThat(((Number) row.get("ACTOR_USER_ID")).longValue()).isEqualTo(owner.getId());
            assertThat(row.get("BEFORE_STATE")).isNotNull();
        });
    }
}
