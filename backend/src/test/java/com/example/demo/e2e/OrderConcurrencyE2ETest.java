package com.example.demo.e2e;

import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.market.HoldingRepository;
import com.example.demo.service.market.MarketMakerService;
import com.example.demo.service.market.MarketSeed;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.E2ETest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Órdenes simultáneas contra PostgreSQL con los locks reales (spec 027, US8): sin sobreventa, sin saldos ni
 * tenencias negativos, y sin deadlocks (cada caso termina dentro de un timeout).
 */
@E2ETest
@TestPropertySource(properties = "market.superuser.enabled=true")
class OrderConcurrencyE2ETest {

    private static final int BUYERS = 50;
    private static final long TIMEOUT_SECONDS = 60;

    @LocalServerPort private int port;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private MarketMakerService marketMakerService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ApplicationContext context;

    private RestClient client;
    private MarketSeed seed;
    private User superuser;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        seed = MarketSeed.from(context);
        superuser = seed.superuser();
    }

    @RepeatedTest(10)
    void fiftySimultaneousBuyersShareExactlyTheTenTokensOnOffer() throws Exception {
        Player player = seed.player("1.0000");
        seed.holding(superuser, player, 10);
        seed.sell(superuser, player, 10, "1.0000");
        seed.holding(seed.user("holder", "1000.0000"), player, 90);
        List<String> keys = IntStream.range(0, BUYERS)
                .mapToObj(i -> AuthFixtures.apiKey(apiKeyRepository, seed.user("buyer-" + i, "1000.0000")))
                .toList();
        BigDecimal totalBefore = totalBalance();

        List<Reply> replies = concurrently(keys.stream()
                .<Callable<Reply>>map(key -> () -> post("/orders/buy", key, market(player, 1)))
                .toList());

        assertThat(replies).allSatisfy(reply -> assertThat(reply.status()).isEqualTo(201));
        assertThat(replies).filteredOn(reply -> "FILLED".equals(reply.body().get("status"))).hasSize(10);
        assertThat(replies).filteredOn(reply -> "CANCELLED".equals(reply.body().get("status")))
                .hasSize(BUYERS - 10)
                .allSatisfy(reply -> assertThat(reply.body().get("filledQuantity")).isEqualTo(0));
        assertThat(holdingRepository.sumQuantityByPlayerId(player.getId())).isEqualTo(100);
        assertNothingNegative();
        assertThat(totalBalance()).isEqualByComparingTo(totalBefore);
    }

    @Test
    void simultaneousBuysOfOneUserNeverSpendMoreThanItsBalance() throws Exception {
        Player first = offered("1.0000");
        Player second = offered("1.0000");
        User user = seed.user("user", "10.0000");
        String key = AuthFixtures.apiKey(apiKeyRepository, user);

        List<Reply> replies = concurrently(List.of(
                () -> post("/orders/buy", key, market(first, 8)),
                () -> post("/orders/buy", key, market(second, 8))));

        assertThat(replies).extracting(Reply::status).containsExactlyInAnyOrder(201, 422);
        assertThat(replies).filteredOn(reply -> reply.status() == 422)
                .singleElement().satisfies(reply -> assertThat(reply.body().get("error")).isEqualTo("insufficient_balance"));
        assertThat(balance(user)).isEqualByComparingTo("2.0000");
        assertNothingNegative();
    }

    @Test
    void simultaneousSellsOfOneUserNeverSellMoreThanItHas() throws Exception {
        Player player = seed.player("1.0000");
        User user = seed.user("user", "1000.0000");
        seed.holding(user, player, 5);
        String key = AuthFixtures.apiKey(apiKeyRepository, user);
        String sell = "{\"playerId\":%d,\"quantity\":5,\"type\":\"LIMIT\",\"limitPrice\":1.0000}"
                .formatted(player.getId());

        List<Reply> replies = concurrently(List.of(
                () -> post("/orders/sell", key, sell),
                () -> post("/orders/sell", key, sell)));

        assertThat(replies).extracting(Reply::status).containsExactlyInAnyOrder(201, 422);
        assertThat(replies).filteredOn(reply -> reply.status() == 422)
                .singleElement().satisfies(reply -> assertThat(reply.body().get("error")).isEqualTo("insufficient_tokens"));
        assertThat(jdbcTemplate.queryForObject(
                "select reserved_quantity from holdings where user_id = ? and player_id = ?", Integer.class,
                user.getId(), player.getId())).isEqualTo(5);
    }

    @Test
    void repricingWhileUsersBuyNeverOffersTokensAlreadySold() throws Exception {
        Player player = offered("1.0000");
        List<String> keys = IntStream.range(0, 20)
                .mapToObj(i -> AuthFixtures.apiKey(apiKeyRepository, seed.user("buyer-" + i, "1000.0000")))
                .toList();
        List<Callable<Reply>> calls = new ArrayList<>(keys.stream()
                .<Callable<Reply>>map(key -> () -> post("/orders/buy", key, market(player, 1)))
                .toList());
        for (int i = 0; i < 5; i++) {
            String price = "1.%04d".formatted(100 + i);
            calls.add(() -> {
                jdbcTemplate.update("update players set market_value = " + price + " where id = ?", player.getId());
                marketMakerService.syncAll();
                return new Reply(200, Map.of());
            });
        }

        List<Reply> replies = concurrently(calls);

        assertThat(replies).allSatisfy(reply -> assertThat(reply.status()).isIn(200, 201));
        List<Map<String, Object>> active = jdbcTemplate.queryForList(
                "select quantity - filled_quantity as pending from orders where user_id = ? and side = 'SELL'"
                        + " and status in ('OPEN', 'PARTIALLY_FILLED')", superuser.getId());
        assertThat(active).hasSizeLessThanOrEqualTo(1);
        Map<String, Object> holding = jdbcTemplate.queryForMap(
                "select quantity, reserved_quantity from holdings where user_id = ? and player_id = ?",
                superuser.getId(), player.getId());
        int pending = active.isEmpty() ? 0 : ((Number) active.get(0).get("pending")).intValue();
        assertThat(((Number) holding.get("quantity")).intValue()).isEqualTo(pending);
        assertThat(((Number) holding.get("reserved_quantity")).intValue()).isEqualTo(pending);
        assertThat(holdingRepository.sumQuantityByPlayerId(player.getId())).isEqualTo(100);
        assertNothingNegative();
    }

    /** Un jugador con los 100 tokens del superusuario ofrecidos a {@code price}. */
    private Player offered(String price) {
        Player player = seed.player(price);
        seed.holding(superuser, player, 100);
        seed.sell(superuser, player, 100, price);
        return player;
    }

    private static String market(Player player, int quantity) {
        return "{\"playerId\":%d,\"quantity\":%d,\"type\":\"MARKET\"}".formatted(player.getId(), quantity);
    }

    @SuppressWarnings("unchecked")
    private Reply post(String path, String apiKey, String body) {
        return client.post().uri(path)
                .header("X-API-KEY", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> new Reply(response.getStatusCode().value(),
                        response.bodyTo(Map.class)));
    }

    /** Arranca todas las llamadas a la vez y espera a que terminen; un deadlock haría vencer el timeout. */
    private static List<Reply> concurrently(List<Callable<Reply>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Reply>> futures = calls.stream()
                    .map(call -> pool.submit(() -> {
                        start.await();
                        return call.call();
                    }))
                    .toList();
            start.countDown();
            List<Reply> replies = new ArrayList<>();
            for (Future<Reply> future : futures) {
                replies.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return replies;
        } finally {
            pool.shutdownNow();
        }
    }

    private void assertNothingNegative() {
        assertThat(jdbcTemplate.queryForObject("select count(*) from users where balance < 0", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from holdings where quantity < 0 or reserved_quantity < 0", Long.class)).isZero();
    }

    private BigDecimal totalBalance() {
        return jdbcTemplate.queryForObject("select sum(balance) from users", BigDecimal.class);
    }

    private BigDecimal balance(User user) {
        return jdbcTemplate.queryForObject("select balance from users where id = ?", BigDecimal.class, user.getId());
    }

    private record Reply(int status, Map<String, Object> body) {
    }
}
