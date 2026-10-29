package com.example.demo.service.market;

import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import com.example.demo.model.User;
import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderStatus;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.market.MarketOrderRepository;
import com.example.demo.service.valuation.QuoteCalculationService;
import com.example.demo.service.valuation.SnapshotResult;
import com.example.demo.service.valuation.StatsSnapshotService;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Reprecio del creador de mercado después de un recálculo real de cotizaciones (spec 027, US7). */
@SpringBootTest
@UnitTestProfile
@TestPropertySource(properties = "market.superuser.enabled=true")
class MarketMakerRecalculationTest {

    @Autowired private QuoteCalculationService calculationService;
    @Autowired private MarketMakerService marketMakerService;
    @Autowired private OrderMatchingEngineService engine;
    @Autowired private MarketOrderRepository orderRepository;
    @Autowired private PlayerRepository playerRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ApplicationContext context;
    @MockitoBean private StatsSnapshotService snapshotService;

    private MarketSeed seed;
    private User superuser;

    @BeforeEach
    void setUp() {
        MarketTestData.clean(jdbcTemplate);
        seed = MarketSeed.from(context);
        superuser = seed.superuser();
        when(snapshotService.snapshotAll(any())).thenReturn(new SnapshotResult(0, 0));
    }

    @AfterEach
    void tearDown() {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
        MarketTestData.clean(jdbcTemplate);
    }

    @Test
    void afterARecalculationTheSuperuserSellFollowsTheNewMarketValue() {
        Player issued = forward("1.2345");
        seed.holding(superuser, issued, 100);
        marketMakerService.syncAll();
        seed.fill(superuserSell(issued), 20);
        Player fresh = forward("1.2345");
        User seller = seed.user("seller-" + UUID.randomUUID(), "1000.0000");
        seed.holding(seller, issued, 10);
        MarketOrder userSell = seed.sell(seller, issued, 10, "1.4800");
        jdbcTemplate.update("delete from audit_logs");
        String correlationId = UUID.randomUUID().toString();
        MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);

        calculationService.recalculate(Optional.empty());

        BigDecimal newValue = marketValue(issued);
        assertThat(newValue).isNotEqualByComparingTo("1.2345");
        assertThat(superuserSell(issued)).satisfies(order -> {
            assertThat(order.getLimitPrice()).isEqualByComparingTo(newValue);
            assertThat(order.remaining()).isEqualTo(80);
        });
        assertThat(superuserSell(fresh)).satisfies(order -> {
            assertThat(order.getLimitPrice()).isEqualByComparingTo(marketValue(fresh));
            assertThat(order.remaining()).isEqualTo(100);
        });
        assertThat(jdbcTemplate.queryForList(
                "select distinct correlation_id from audit_logs where actor_type = 'SYSTEM' and action = 'ORDER_CREATED'",
                String.class)).containsExactly(correlationId);

        assertThat(engine.isWithinBand(userSell.getLimitPrice(), newValue))
                .as("la venta del usuario quedó fuera de la banda nueva").isFalse();
        MarketOrder userSellAfter = orderRepository.findById(userSell.getId()).orElseThrow();
        assertThat(userSellAfter.getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(userSellAfter.getLimitPrice()).isEqualByComparingTo("1.4800");
        assertThat(userSellAfter.remaining()).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject(
                "select reserved_quantity from holdings where user_id = ? and player_id = ?", Integer.class,
                seller.getId(), issued.getId())).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_logs where entity_type = 'ORDER' and entity_id = ?", Long.class,
                String.valueOf(userSell.getId()))).isZero();
    }

    private Player forward(String marketValue) {
        Player player = seed.player(marketValue);
        player.setPositions(Set.of(Position.FORWARD));
        return playerRepository.save(player);
    }

    private MarketOrder superuserSell(Player player) {
        List<MarketOrder> sells = orderRepository.findOpenSells(player.getId()).stream()
                .filter(order -> superuser.getId().equals(jdbcTemplate.queryForObject(
                        "select user_id from orders where id = ?", Long.class, order.getId())))
                .toList();
        assertThat(sells).hasSize(1);
        return sells.get(0);
    }

    private BigDecimal marketValue(Player player) {
        return jdbcTemplate.queryForObject("select market_value from players where id = ?", BigDecimal.class,
                player.getId());
    }
}
