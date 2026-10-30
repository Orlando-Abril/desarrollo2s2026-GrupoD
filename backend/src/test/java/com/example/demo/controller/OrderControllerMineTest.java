package com.example.demo.controller;

import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.Trade;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.market.MarketOrderRepository;
import com.example.demo.repository.market.TradeRepository;
import com.example.demo.service.market.MarketSeed;
import com.example.demo.service.market.MarketTestData;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.MarketMockMvcTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@MarketMockMvcTest
class OrderControllerMineTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private MarketOrderRepository orderRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ApplicationContext context;

    private MarketSeed seed;
    private Player player;
    private User owner;
    private String ownerKey;

    @BeforeEach
    void setUp() {
        MarketTestData.clean(jdbcTemplate);
        seed = MarketSeed.from(context);
        player = seed.player("1.1000");
        owner = seed.user("owner-" + UUID.randomUUID(), "1000.0000");
        ownerKey = AuthFixtures.apiKey(apiKeyRepository, owner);
    }

    @AfterEach
    void tearDown() {
        MarketTestData.clean(jdbcTemplate);
    }

    @Test
    void returnsOnlyTheActiveOrdersOfTheAuthenticatedUserNewestFirst() throws Exception {
        MarketOrder open = seed.buy(owner, player, 2, "1.0000");
        MarketOrder partial = partiallyFilledBuy();
        seed.fill(seed.buy(owner, player, 3, "1.0000"), 3);
        MarketOrder cancelled = seed.buy(owner, player, 5, "1.0000");
        cancelled.cancel(Instant.parse("2026-10-02T12:00:00Z"));
        orderRepository.save(cancelled);
        seed.buy(seed.user("other-" + UUID.randomUUID(), "1000.0000"), player, 5, "1.0000");

        mockMvc.perform(get("/orders/mine").header("X-API-KEY", ownerKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(partial.getId()))
                .andExpect(jsonPath("$[0].playerId").value(player.getId()))
                .andExpect(jsonPath("$[0].playerName").value("Jugador 027"))
                .andExpect(jsonPath("$[0].side").value("BUY"))
                .andExpect(jsonPath("$[0].type").value("LIMIT"))
                .andExpect(jsonPath("$[0].status").value("PARTIALLY_FILLED"))
                .andExpect(jsonPath("$[0].filledQuantity").value(4))
                .andExpect(jsonPath("$[0].remainingQuantity").value(6))
                .andExpect(jsonPath("$[0].averagePrice").value(1.0))
                .andExpect(jsonPath("$[0].trades.length()").value(0))
                .andExpect(jsonPath("$[1].id").value(open.getId()))
                .andExpect(jsonPath("$[1].status").value("OPEN"))
                .andExpect(jsonPath("$[1].averagePrice").value(nullValue()))
                .andExpect(jsonPath("$[1].playerName").value("Jugador 027"));
    }

    @Test
    void returnsAnEmptyListWithoutActiveOrders() throws Exception {
        seed.fill(seed.buy(owner, player, 3, "1.0000"), 3);

        mockMvc.perform(get("/orders/mine").header("X-API-KEY", ownerKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void requiresCredentials() throws Exception {
        mockMvc.perform(get("/orders/mine"))
                .andExpect(status().isUnauthorized());
    }

    /** Compra LIMIT de 10 a 1.1000 con 4 ejecutados a 1.0000 contra una venta sembrada, con su operación. */
    private MarketOrder partiallyFilledBuy() {
        User seller = seed.user("seller-" + UUID.randomUUID(), "0");
        seed.holding(seller, player, 4);
        MarketOrder sell = seed.fill(seed.sell(seller, player, 4, "1.0000"), 4);
        MarketOrder buy = seed.fill(seed.buy(owner, player, 10, "1.1000"), 4);
        tradeRepository.save(Trade.of(buy, sell, 4, new BigDecimal("1.0000"), Instant.parse("2026-10-01T13:00:00Z")));
        return buy;
    }
}
