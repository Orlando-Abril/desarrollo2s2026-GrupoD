package com.example.demo.service.market;

import com.example.demo.dto.market.OrderRequest;
import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.model.market.OrderType;
import com.example.demo.repository.market.HoldingRepository;
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

/**
 * Escenario de la constitución §3.3: 4 usuarios operando sobre 5 jugadores, con compras al superusuario, ventas
 * LIMIT entre usuarios, calces parciales, una orden que queda reservando créditos y una cancelación. Los valores
 * esperados están calculados a mano en los comentarios de cada paso.
 */
@SpringBootTest
@UnitTestProfile
class MarketSimulationTest {

    @Autowired private OrderService orderService;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private MarketOrderRepository orderRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ApplicationContext context;

    private List<Player> players;
    private User superuser;
    private List<User> users;

    @BeforeEach
    void setUp() {
        MarketTestData.clean(jdbcTemplate);
        MarketSeed seed = MarketSeed.from(context);
        superuser = seed.superuser();
        players = List.of(seed.player("1.0000"), seed.player("1.0000"), seed.player("1.0000"),
                seed.player("1.0000"), seed.player("1.0000"));
        for (Player player : players) {
            seed.holding(superuser, player, 100);
            seed.sell(superuser, player, 100, "1.0000");
        }
        String run = UUID.randomUUID().toString().substring(0, 8);
        users = List.of(seed.user("u1-" + run, "1000.0000"), seed.user("u2-" + run, "1000.0000"),
                seed.user("u3-" + run, "1000.0000"), seed.user("u4-" + run, "1000.0000"));
    }

    @AfterEach
    void tearDown() {
        MarketTestData.clean(jdbcTemplate);
    }

    @Test
    void fourUsersTradingFivePlayersEndInTheExpectedState() {
        BigDecimal totalBefore = totalBalance();

        // Compras al superusuario a 1.0000: U1 10 de P1 y 5 de P2; U2 8 de P1 (LIMIT 1.1000, paga 1.0000) y 6 de P3;
        // U3 4 de P4 y 7 de P5; U4 3 de P2 y 2 de P5.
        buy(1, 1, market(10));
        buy(1, 2, market(5));
        buy(2, 1, limit(8, "1.1000"));
        buy(2, 3, market(6));
        buy(3, 4, market(4));
        buy(3, 5, market(7));
        buy(4, 2, market(3));
        buy(4, 5, market(2));

        // U1 vende 6 de P1 a 0.9500 y U2 vende 4 de P3 a 0.9000: quedan en el libro, debajo del superusuario.
        Long u1Sell = sell(1, 1, limit(6, "0.9500"));
        Long u2Sell = sell(2, 3, limit(4, "0.9000"));
        // U3 compra 4 de P1: le compra a U1 a 0.9500 (3.8000); a la venta de U1 le quedan 2.
        buy(3, 1, market(4));
        // U4 compra 3 de P3 con tope 0.9500: le compra a U2 a 0.9000 (2.7000); a la venta de U2 le queda 1.
        buy(4, 3, limit(3, "0.9500"));
        // U3 deja una compra de 5 de P4 a 0.8500 que no cruza: reserva 4.2500.
        buy(3, 4, limit(5, "0.8500"));
        // U1 cancela lo que le queda de su venta de P1.
        orderService.cancel(users.get(0).getUsername(), u1Sell);

        assertThat(balance(users.get(0))).isEqualByComparingTo("988.8000");   // 1000 - 10 - 5 + 3.8
        assertThat(balance(users.get(1))).isEqualByComparingTo("988.7000");   // 1000 - 8 - 6 + 2.7
        assertThat(balance(users.get(2))).isEqualByComparingTo("985.2000");   // 1000 - 4 - 7 - 3.8
        assertThat(balance(users.get(3))).isEqualByComparingTo("992.3000");   // 1000 - 3 - 2 - 2.7
        assertThat(balance(superuser)).isEqualByComparingTo("1045.0000");     // 1000 + 45 tokens a 1.0000
        assertThat(totalBalance()).isEqualByComparingTo(totalBefore);

        assertHolding(1, 1, 6, 0);
        assertHolding(1, 2, 5, 0);
        assertHolding(2, 1, 8, 0);
        assertHolding(2, 3, 3, 1);
        assertHolding(3, 1, 4, 0);
        assertHolding(3, 4, 4, 0);
        assertHolding(3, 5, 7, 0);
        assertHolding(4, 2, 3, 0);
        assertHolding(4, 3, 3, 0);
        assertHolding(4, 5, 2, 0);
        int[] superuserLeft = {82, 92, 94, 96, 91};
        for (int i = 0; i < players.size(); i++) {
            assertThat(holding(superuser, players.get(i))).containsExactly(superuserLeft[i], superuserLeft[i]);
            assertThat(holdingRepository.sumQuantityByPlayerId(players.get(i).getId())).isEqualTo(100);
        }

        assertThat(jdbcTemplate.queryForList("select concat(quantity, '@', price) from trades order by id",
                String.class)).containsExactly(
                "10@1.0000", "5@1.0000", "8@1.0000", "6@1.0000", "4@1.0000", "7@1.0000", "3@1.0000", "2@1.0000",
                "4@0.9500", "3@0.9000");
        assertThat(orderRepository.sumReservedCredits(users.get(2).getId())).isEqualByComparingTo("4.2500");
        for (User user : List.of(users.get(0), users.get(1), users.get(3))) {
            assertThat(orderRepository.sumReservedCredits(user.getId())).isEqualByComparingTo("0");
        }
        assertThat(orderRepository.findById(u2Sell)).get()
                .satisfies(order -> assertThat(order.remaining()).isEqualTo(1));
    }

    private void buy(int user, int player, Request request) {
        orderService.placeBuy(users.get(user - 1).getUsername(), request.to(players.get(player - 1)));
    }

    private Long sell(int user, int player, Request request) {
        return orderService.placeSell(users.get(user - 1).getUsername(), request.to(players.get(player - 1)))
                .order().getId();
    }

    private static Request market(int quantity) {
        return new Request(quantity, OrderType.MARKET, null);
    }

    private static Request limit(int quantity, String price) {
        return new Request(quantity, OrderType.LIMIT, new BigDecimal(price));
    }

    private void assertHolding(int user, int player, int quantity, int reserved) {
        assertThat(holding(users.get(user - 1), players.get(player - 1))).containsExactly(quantity, reserved);
    }

    private List<Integer> holding(User user, Player player) {
        return jdbcTemplate.queryForObject(
                "select quantity, reserved_quantity from holdings where user_id = ? and player_id = ?",
                (rs, row) -> List.of(rs.getInt(1), rs.getInt(2)), user.getId(), player.getId());
    }

    private BigDecimal balance(User user) {
        return jdbcTemplate.queryForObject("select balance from users where id = ?", BigDecimal.class, user.getId());
    }

    private BigDecimal totalBalance() {
        return jdbcTemplate.queryForObject("select sum(balance) from users", BigDecimal.class);
    }

    private record Request(int quantity, OrderType type, BigDecimal limitPrice) {

        OrderRequest to(Player player) {
            return new OrderRequest(player.getId(), quantity, type, limitPrice);
        }
    }
}
