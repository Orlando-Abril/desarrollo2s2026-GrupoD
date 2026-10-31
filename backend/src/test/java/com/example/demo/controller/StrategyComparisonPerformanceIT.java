package com.example.demo.controller;

import com.example.demo.adapter.footballdata.FootballDataAdapter;
import com.example.demo.adapter.whoscored.WhoScoredAdapter;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.market.HoldingRepository;
import com.example.demo.repository.market.MarketOrderRepository;
import com.example.demo.repository.market.TradeRepository;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.example.demo.support.StrategyComparisonFixtures.bought;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 031, SC-007: con 2.500 jugadores y su historial de ambas estrategias, cada consulta responde en menos de 2 s. */
@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
@Transactional
class StrategyComparisonPerformanceIT {

    private static final int PLAYERS = 2_500;
    private static final Duration LIMIT = Duration.ofSeconds(2);

    @Autowired MockMvc mvc;
    @Autowired PlayerRepository players;
    @Autowired UserRepository users;
    @Autowired ApiKeyRepository apiKeys;
    @Autowired MarketOrderRepository orders;
    @Autowired TradeRepository trades;
    @Autowired HoldingRepository holdings;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean FootballDataAdapter footballDataAdapter;
    @MockitoBean WhoScoredAdapter whoScoredAdapter;

    @Test
    void opportunitiesLatestQuotesAndPortfolioStayUnderTwoSeconds() throws Exception {
        List<Player> catalog = seedCatalog();
        User owner = AuthFixtures.user(users, "perf-owner-" + UUID.randomUUID(), Role.USER);
        User seller = AuthFixtures.user(users, "perf-seller-" + UUID.randomUUID(), Role.USER);
        for (Player player : catalog.subList(0, 20)) {
            bought(orders, trades, holdings, owner, seller, player, 3, "1.0000");
        }
        String key = AuthFixtures.apiKey(apiKeys, owner);
        String hundredIds = catalog.subList(1_000, 1_100).stream().map(player -> player.getId().toString())
                .collect(Collectors.joining(","));

        List<MockHttpServletRequestBuilder> requests = List.of(
                get("/quotes/opportunities").header("X-API-KEY", key),
                get("/quotes/latest").param("playerIds", hundredIds).header("X-API-KEY", key),
                get("/users/" + owner.getId() + "/portfolio").param("strategy", "MOMENTUM").header("X-API-KEY", key));
        for (MockHttpServletRequestBuilder request : requests) {
            mvc.perform(request).andExpect(status().isOk());
            for (int i = 0; i < 5; i++) {
                assertTimeout(LIMIT, () -> mvc.perform(request).andExpect(status().isOk()));
            }
        }
    }

    private List<Player> seedCatalog() {
        List<Player> catalog = new ArrayList<>(PLAYERS);
        for (int index = 0; index < PLAYERS; index++) {
            catalog.add(Player.builder().externalId("comparison-perf-" + index)
                    .fullName("Comparison Player " + index).team("Performance FC")
                    .league(League.values()[index % League.values().length])
                    .positions(Set.of(Position.values()[index % Position.values().length]))
                    .marketValue(new BigDecimal("1.0000")).build());
        }
        List<Player> saved = players.saveAll(catalog);
        List<Object[]> rows = new ArrayList<>(PLAYERS * 4);
        Instant first = Instant.parse("2026-10-20T06:00:00Z");
        Instant second = Instant.parse("2026-10-27T06:00:00Z");
        for (Player player : saved) {
            for (Instant at : List.of(first, second)) {
                rows.add(row(player, "OFFICIAL", "1.0000", at));
                rows.add(row(player, "MOMENTUM", (1 + player.getId() % 50 / 100.0) + "", at));
            }
        }
        jdbc.batchUpdate("insert into player_quotes (player_id, strategy_code, strategy_version, quote_value, score,"
                + " status, calculated_at, run_id, correlation_id) values (?, ?, 1, ?, 1.0000, 'CALCULATED', ?, ?,"
                + " 'comparison-perf')", rows);
        return saved;
    }

    private static Object[] row(Player player, String strategy, String value, Instant at) {
        return new Object[]{player.getId(), strategy, new BigDecimal(value), Timestamp.from(at), UUID.randomUUID()};
    }
}
