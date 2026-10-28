package com.example.demo.e2e;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderSide;
import com.example.demo.model.market.OrderStatus;
import com.example.demo.model.market.Trade;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.market.HoldingRepository;
import com.example.demo.repository.market.MarketOrderRepository;
import com.example.demo.repository.market.TradeRepository;
import com.example.demo.service.market.SuperuserAccount;
import com.example.demo.service.market.SuperuserInitializer;
import com.example.demo.service.market.TokenIssuanceService;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.E2ETest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PessimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Libro, índices, restricciones y lock del mercado en PostgreSQL (spec 026, US5). */
@E2ETest
@TestPropertySource(properties = "market.superuser.enabled=true")
class MarketBookQueryE2ETest {

    private static final Instant T1 = Instant.parse("2026-10-27T10:00:00Z");
    private static final String BOOK_JPQL = "select o from MarketOrder o where o.player.id = :playerId"
            + " and o.side = :side and o.type = com.example.demo.model.market.OrderType.LIMIT"
            + " and o.status in :active order by o.limitPrice asc, o.createdAt asc, o.id asc";

    @Autowired private SuperuserInitializer initializer;
    @Autowired private SuperuserAccount superuserAccount;
    @Autowired private TokenIssuanceService issuanceService;
    @Autowired private PlayerRepository playerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private MarketOrderRepository orderRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbc;

    private User superuser;

    @BeforeEach
    void setUp() {
        // E2EDatabaseCleaner trunca después de cada test: el superusuario se recrea antes de cada uno.
        initializer.run(null);
        superuser = userRepository.findById(superuserAccount.id()).orElseThrow();
    }

    @Test
    void theSchemaHasTheMarketIndexes() {
        List<String> indexes = jdbc.queryForList("SELECT indexname FROM pg_indexes"
                + " WHERE tablename IN ('orders', 'trades', 'holdings', 'audit_logs')", String.class);

        assertThat(indexes).contains("idx_orders_book", "idx_orders_user_created", "idx_trades_buyer_executed",
                "idx_trades_seller_executed", "idx_trades_player_executed", "uk_holdings_user_player",
                "idx_holdings_user", "idx_audit_logs_entity", "idx_audit_logs_occurred");
    }

    @Test
    void checkConstraintsExistAndRejectInvalidRows() {
        Trade trade = persistedTrade();

        assertThat(jdbc.queryForList("SELECT conname FROM pg_constraint WHERE contype = 'c'", String.class))
                .contains("ck_holdings_quantities", "ck_orders_quantities_and_price", "ck_trades_values",
                        "ck_audit_logs_actor");
        assertRejected("UPDATE holdings SET reserved_quantity = quantity + 1");
        assertRejected("UPDATE orders SET limit_price = NULL WHERE type = 'LIMIT'");
        assertRejected("UPDATE trades SET seller_id = buyer_id WHERE id = " + trade.getId());
        assertRejected("UPDATE audit_logs SET actor_type = 'USER' WHERE actor_user_id IS NULL");
    }

    @Test
    void auditStatesAreStoredAsJsonb() {
        assertThat(jdbc.queryForList("SELECT data_type FROM information_schema.columns WHERE table_name = 'audit_logs'"
                + " AND column_name IN ('before_state', 'after_state')", String.class))
                .containsExactly("jsonb", "jsonb");
    }

    @Test
    void theBookHasTheSameOrderAsInH2() {
        Player player = player("book");
        MarketOrder at110 = sell(player, "1.10", T1);
        MarketOrder at105Newer = sell(player, "1.05", T1.plusSeconds(60));
        MarketOrder at105Older = sell(player, "1.05", T1.minusSeconds(30));
        MarketOrder buyNewer = buy(player, "1.00", T1.plusSeconds(60));
        MarketOrder buyOlder = buy(player, "1.00", T1);
        MarketOrder buyLower = buy(player, "0.95", T1);

        assertThat(orderRepository.findOpenSells(player.getId())).extracting(MarketOrder::getId)
                .containsExactly(at105Older.getId(), at105Newer.getId(), at110.getId());
        assertThat(orderRepository.findOpenBuys(player.getId())).extracting(MarketOrder::getId)
                .containsExactly(buyOlder.getId(), buyNewer.getId(), buyLower.getId());
    }

    @Test
    void theBookIsFastAndUsesItsIndexWithTenThousandOrders() {
        List<Player> players = new ArrayList<>();
        IntStream.range(0, 500).forEach(i -> players.add(Player.builder().externalId("book-bench-" + i)
                .fullName("Player " + i).team("Club").league(League.PREMIER_LEAGUE).marketValue(BigDecimal.ONE).build()));
        List<Player> saved = playerRepository.saveAllAndFlush(players);
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            Player player = saved.get(i % saved.size());
            String side = i % 2 == 0 ? "SELL" : "BUY";
            Timestamp createdAt = Timestamp.from(T1.plusSeconds(i));
            rows.add(new Object[]{superuser.getId(), player.getId(), side,
                    BigDecimal.ONE.add(BigDecimal.valueOf(i % 20, 2)), createdAt, createdAt});
        }
        jdbc.batchUpdate("INSERT INTO orders (user_id, player_id, side, type, limit_price, quantity, filled_quantity,"
                + " status, created_at, updated_at) VALUES (?, ?, ?, 'LIMIT', ?, 5, 0, 'OPEN', ?, ?)", rows);
        jdbc.execute("ANALYZE orders");
        Long playerId = saved.get(7).getId();

        assertThat(medianMillis(() -> orderRepository.findOpenSells(playerId))).isLessThan(100);
        assertThat(medianMillis(() -> orderRepository.findOpenBuys(playerId))).isLessThan(100);
        List<String> plan = jdbc.queryForList("EXPLAIN SELECT * FROM orders WHERE player_id = " + playerId
                + " AND side = 'SELL' AND type = 'LIMIT' AND status IN ('OPEN', 'PARTIALLY_FILLED')"
                + " ORDER BY limit_price ASC, created_at ASC, id ASC", String.class);
        assertThat(plan).anyMatch(line -> line.contains("idx_orders_book"));
    }

    @Test
    void lockingTheBookMakesANowaitLockFail() throws Exception {
        Player player = player("lock");
        sell(player, "1.00", T1);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(status -> {
            orderRepository.findOpenSellsForUpdate(player.getId());
            locked.countDown();
            await(release);
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> nowaitBook(player)))
                .isInstanceOf(PessimisticLockException.class);

        release.countDown();
        holder.get(10, TimeUnit.SECONDS);
        transactionTemplate.executeWithoutResult(status -> assertThat(nowaitBook(player)).hasSize(1));
    }

    @Test
    void everyIssuedPlayerHasOneHundredTokens() {
        List<Player> players = List.of(player("issued-1"), player("issued-2"), player("issued-3"));

        issuanceService.issueMissing();

        players.forEach(player -> assertThat(holdingRepository.sumQuantityByPlayerId(player.getId())).isEqualTo(100));
    }

    private List<MarketOrder> nowaitBook(Player player) {
        return entityManager.createQuery(BOOK_JPQL, MarketOrder.class)
                .setParameter("playerId", player.getId())
                .setParameter("side", OrderSide.SELL)
                .setParameter("active", OrderStatus.ACTIVE)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint("jakarta.persistence.lock.timeout", 0)
                .getResultList();
    }

    private Trade persistedTrade() {
        User buyer = AuthFixtures.user(userRepository, "market-buyer-" + UUID.randomUUID(), Role.USER);
        Player player = player("trade");
        issuanceService.issueMissing();
        return transactionTemplate.execute(status -> {
            MarketOrder buyOrder = orderRepository.save(MarketOrder.limit(buyer, player, OrderSide.BUY, 1, BigDecimal.ONE, T1));
            MarketOrder sellOrder = orderRepository.save(MarketOrder.limit(superuser, player, OrderSide.SELL, 1, BigDecimal.ONE, T1));
            return tradeRepository.save(Trade.of(buyOrder, sellOrder, 1, BigDecimal.ONE, T1));
        });
    }

    private Player player(String name) {
        return playerRepository.save(Player.builder().externalId("026-" + name + "-" + UUID.randomUUID())
                .fullName(name).team("Club").league(League.LA_LIGA).marketValue(BigDecimal.ONE).build());
    }

    private MarketOrder sell(Player player, String price, Instant createdAt) {
        return orderRepository.save(MarketOrder.limit(superuser, player, OrderSide.SELL, 5, new BigDecimal(price), createdAt));
    }

    private MarketOrder buy(Player player, String price, Instant createdAt) {
        return orderRepository.save(MarketOrder.limit(superuser, player, OrderSide.BUY, 5, new BigDecimal(price), createdAt));
    }

    private void assertRejected(String sql) {
        assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static long medianMillis(Runnable query) {
        List<Long> millis = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            long started = System.nanoTime();
            query.run();
            millis.add((System.nanoTime() - started) / 1_000_000);
        }
        return millis.stream().sorted().toList().get(2);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
