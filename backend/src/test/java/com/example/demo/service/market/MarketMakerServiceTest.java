package com.example.demo.service.market;

import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderSide;
import com.example.demo.model.market.OrderStatus;
import com.example.demo.repository.market.MarketOrderRepository;
import com.example.demo.repository.market.MarketPlayerRepository;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@UnitTestProfile
@TestPropertySource(properties = "market.superuser.enabled=true")
class MarketMakerServiceTest {

    @Autowired private MarketMakerService marketMakerService;
    @Autowired private MarketOrderRepository orderRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ApplicationContext context;
    @MockitoSpyBean private MarketPlayerRepository playerRepository;

    private MarketSeed seed;
    private User superuser;

    @BeforeEach
    void setUp() {
        MarketTestData.clean(jdbcTemplate);
        seed = MarketSeed.from(context);
        superuser = seed.superuser();
    }

    @AfterEach
    void tearDown() {
        MarketTestData.clean(jdbcTemplate);
    }

    @Test
    void publishesOneSellWithAllTokensAtMarketValueForEachPlayer() {
        Player first = issued("1.2000");
        Player second = issued("0.9500");

        assertThat(marketMakerService.syncAll()).isEqualTo(new SyncResult(2, 0, 0));

        assertSuperuserSell(first, 100, "1.2000");
        assertSuperuserSell(second, 100, "0.9500");
        assertThat(holding(first)).containsExactly(100, 100);
    }

    @Test
    void publishesAtStartupThroughApplicationReady() throws NoSuchMethodException {
        Player player = issued("1.1000");

        marketMakerService.onApplicationReady();

        assertSuperuserSell(player, 100, "1.1000");
        EventListener listener = MarketMakerService.class.getMethod("onApplicationReady")
                .getAnnotation(EventListener.class);
        assertThat(listener).isNotNull();
        assertThat(listener.value()).contains(ApplicationReadyEvent.class);
    }

    @Test
    void repricesWithTheRemainingTokensAndAuditsAsSystem() {
        Player player = issued("1.2000");
        marketMakerService.syncAll();
        MarketOrder previous = superuserSells(player).get(0);
        seed.fill(previous, 30);
        jdbcTemplate.update("delete from audit_logs");
        jdbcTemplate.update("update players set market_value = 1.3500 where id = ?", player.getId());

        marketMakerService.syncAll();

        MarketOrder cancelled = orderRepository.findById(previous.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getClosedAt()).isNotNull();
        assertSuperuserSell(player, 70, "1.3500");
        assertThat(holding(player)).containsExactly(70, 70);
        List<Map<String, Object>> audits = jdbcTemplate.queryForList(
                "select action, actor_type from audit_logs order by id");
        assertThat(audits).extracting(row -> row.get("ACTION"))
                .containsExactly("ORDER_CANCELLED", "HOLDING_CHANGED", "ORDER_CREATED", "HOLDING_CHANGED");
        assertThat(audits).allSatisfy(row -> assertThat(row.get("ACTOR_TYPE")).isEqualTo("SYSTEM"));
    }

    @Test
    void doesNothingWhenPriceAndQuantityAreAlreadyCurrent() {
        Player player = issued("1.2000");
        marketMakerService.syncAll();
        Long orderId = superuserSells(player).get(0).getId();
        jdbcTemplate.update("delete from audit_logs");

        assertThat(marketMakerService.syncAll()).isEqualTo(new SyncResult(0, 1, 0));

        assertThat(superuserSells(player)).extracting(MarketOrder::getId).containsExactly(orderId);
        assertThat(jdbcTemplate.queryForObject("select count(*) from audit_logs", Long.class)).isZero();
    }

    @Test
    void createsNoOrderWhenTheSuperuserHasNoTokens() {
        Player player = issued("1.2000");
        marketMakerService.syncAll();
        seed.fill(superuserSells(player).get(0), 100);

        marketMakerService.syncAll();

        assertThat(superuserSells(player)).isEmpty();
    }

    @Test
    void ignoresTheBandForTheSuperuser() {
        Player player = issued("1.0000");
        marketMakerService.syncAll();
        jdbcTemplate.update("update players set market_value = 1.5000 where id = ?", player.getId());

        marketMakerService.syncAll();

        assertSuperuserSell(player, 100, "1.5000");
    }

    @Test
    void repeatedPassesKeepASingleSellAndNoBuys() {
        Player player = issued("1.2000");

        marketMakerService.syncAll();
        marketMakerService.syncAll();
        marketMakerService.syncAll();

        assertThat(superuserSells(player)).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders where user_id = ? and side = 'BUY'",
                Long.class, superuser.getId())).isZero();
    }

    @Test
    void aFailingPlayerDoesNotStopTheOthers() {
        Player failing = issued("1.2000");
        Player healthy = issued("1.2000");
        marketMakerService.syncAll();
        MarketOrder failingBefore = superuserSells(failing).get(0);
        jdbcTemplate.update("update players set market_value = 1.3000 where id in (?, ?)", failing.getId(),
                healthy.getId());
        doThrow(new IllegalStateException("lock_failed")).when(playerRepository).findLockedById(failing.getId());

        SyncResult result = marketMakerService.syncAll();

        assertThat(result.failed()).isEqualTo(1);
        assertSuperuserSell(healthy, 100, "1.3000");
        assertThat(superuserSells(failing)).singleElement().satisfies(order -> {
            assertThat(order.getId()).isEqualTo(failingBefore.getId());
            assertThat(order.getLimitPrice()).isEqualByComparingTo("1.2000");
        });
    }

    private Player issued(String marketValue) {
        Player player = seed.player(marketValue);
        seed.holding(superuser, player, 100);
        return player;
    }

    private List<MarketOrder> superuserSells(Player player) {
        return orderRepository.findOpenSells(player.getId()).stream()
                .filter(order -> order.getSide() == OrderSide.SELL)
                .filter(order -> jdbcTemplate.queryForObject("select user_id from orders where id = ?", Long.class,
                        order.getId()).equals(superuser.getId()))
                .toList();
    }

    private void assertSuperuserSell(Player player, int quantity, String price) {
        assertThat(superuserSells(player)).singleElement().satisfies(order -> {
            assertThat(order.remaining()).isEqualTo(quantity);
            assertThat(order.getLimitPrice()).isEqualByComparingTo(price);
        });
    }

    private List<Integer> holding(Player player) {
        return jdbcTemplate.queryForObject(
                "select quantity, reserved_quantity from holdings where user_id = ? and player_id = ?",
                (rs, row) -> List.of(rs.getInt(1), rs.getInt(2)), superuser.getId(), player.getId());
    }
}
