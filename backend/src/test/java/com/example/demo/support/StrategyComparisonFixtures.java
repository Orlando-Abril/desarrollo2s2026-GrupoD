package com.example.demo.support;

import com.example.demo.model.Player;
import com.example.demo.model.PlayerQuote;
import com.example.demo.model.QuoteStatus;
import com.example.demo.model.User;
import com.example.demo.model.market.Holding;
import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderSide;
import com.example.demo.model.market.Trade;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.market.HoldingRepository;
import com.example.demo.repository.market.MarketOrderRepository;
import com.example.demo.repository.market.TradeRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Datos cargados a mano para los tests del comparador de estrategias (spec 031). */
public final class StrategyComparisonFixtures {

    public static final Instant TRADED_AT = Instant.parse("2026-10-01T12:00:00Z");

    private StrategyComparisonFixtures() {
    }

    public static PlayerQuote quote(PlayerQuoteRepository repository, Player player, String strategy, String value,
                                    QuoteStatus status, String calculatedAt) {
        return repository.save(PlayerQuote.builder().player(player).strategyCode(strategy).strategyVersion(1)
                .value(new BigDecimal(value))
                .score(status == QuoteStatus.CALCULATED ? new BigDecimal("1.0000") : null)
                .status(status).calculatedAt(Instant.parse(calculatedAt)).runId(UUID.randomUUID())
                .correlationId("strategy-comparison-fixture").build());
    }

    /** Compra ejecutada de {@code quantity} tokens a {@code price}, con la tenencia coherente del comprador. */
    public static void bought(MarketOrderRepository orders, TradeRepository trades, HoldingRepository holdings,
                              User buyer, User seller, Player player, int quantity, String price) {
        BigDecimal unit = new BigDecimal(price);
        MarketOrder buy = orders.save(MarketOrder.limit(buyer, player, OrderSide.BUY, quantity, unit, TRADED_AT));
        MarketOrder sell = orders.save(MarketOrder.limit(seller, player, OrderSide.SELL, quantity, unit, TRADED_AT));
        trades.save(Trade.of(buy, sell, quantity, unit, TRADED_AT.plusSeconds(1)));
        holdings.save(Holding.issue(buyer, player, quantity, TRADED_AT));
    }

    /** Fotografía de los datos que una consulta de la spec 031 nunca debe modificar (FR-028, SC-009). */
    public static Map<String, Object> dataSnapshot(JdbcTemplate jdbc) {
        return Map.of(
                "quotes", count(jdbc, "player_quotes"),
                "orders", count(jdbc, "orders"),
                "trades", count(jdbc, "trades"),
                "holdings", jdbc.queryForList("select user_id, player_id, quantity, reserved_quantity from holdings"
                        + " order by user_id, player_id"),
                "auditLogs", count(jdbc, "audit_logs"),
                "balances", jdbc.queryForList("select id, balance from users order by id"),
                "marketValues", jdbc.queryForList("select id, market_value from players order by id"));
    }

    private static Long count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }
}
