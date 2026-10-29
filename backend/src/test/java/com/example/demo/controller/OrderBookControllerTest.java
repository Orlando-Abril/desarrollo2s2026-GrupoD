package com.example.demo.controller;

import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderSide;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.market.MarketOrderRepository;
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
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@MarketMockMvcTest
class OrderBookControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private MarketOrderRepository orderRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ApplicationContext context;

    private MarketSeed seed;
    private Player player;
    private String apiKey;

    @BeforeEach
    void setUp() {
        MarketTestData.clean(jdbcTemplate);
        seed = MarketSeed.from(context);
        player = seed.player("1.2000");
        apiKey = AuthFixtures.apiKey(apiKeyRepository, seed.user("viewer-" + UUID.randomUUID(), "1000.0000"));
    }

    @AfterEach
    void tearDown() {
        MarketTestData.clean(jdbcTemplate);
    }

    @Test
    void aggregatesEachSideByPriceWithTheMarketValue() throws Exception {
        sell(3, "1.1000");
        sell(4, "1.1000");
        User superuser = seed.superuser();
        seed.holding(superuser, player, 50);
        seed.sell(superuser, player, 50, "1.2000");
        seed.buy(user(), player, 5, "1.0500");
        seed.buy(user(), player, 2, "1.0000");

        book(player.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value(player.getId()))
                .andExpect(jsonPath("$.marketValue").value(1.2))
                .andExpect(jsonPath("$.sells.length()").value(2))
                .andExpect(jsonPath("$.sells[0].price").value(1.1))
                .andExpect(jsonPath("$.sells[0].quantity").value(7))
                .andExpect(jsonPath("$.sells[1].price").value(1.2))
                .andExpect(jsonPath("$.sells[1].quantity").value(50))
                .andExpect(jsonPath("$.buys.length()").value(2))
                .andExpect(jsonPath("$.buys[0].price").value(1.05))
                .andExpect(jsonPath("$.buys[0].quantity").value(5))
                .andExpect(jsonPath("$.buys[1].price").value(1.0))
                .andExpect(jsonPath("$.sells[0].orderId").doesNotExist())
                .andExpect(jsonPath("$.sells[0].userId").doesNotExist())
                .andExpect(jsonPath("$.sells[0].username").doesNotExist());
    }

    @Test
    void sumsOnlyWhatIsPendingAndSkipsClosedAndMarketOrders() throws Exception {
        seed.fill(sell(10, "1.1000"), 4);
        MarketOrder filled = sell(2, "1.1500");
        seed.fill(filled, 2);
        MarketOrder cancelled = sell(3, "1.1800");
        cancelled.cancel(Instant.now());
        orderRepository.save(cancelled);
        User marketUser = user();
        orderRepository.save(MarketOrder.market(marketUser, player, OrderSide.BUY, 5, Instant.now()));

        book(player.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sells.length()").value(1))
                .andExpect(jsonPath("$.sells[0].quantity").value(6))
                .andExpect(jsonPath("$.buys.length()").value(0));
    }

    @Test
    void emptyBookHasEmptySides() throws Exception {
        book(player.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sells").isArray())
                .andExpect(jsonPath("$.sells.length()").value(0))
                .andExpect(jsonPath("$.buys.length()").value(0));
    }

    @Test
    void validatesThePlayerId() throws Exception {
        mockMvc.perform(get("/orders/book").header("X-API-KEY", apiKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        mockMvc.perform(get("/orders/book").param("playerId", "abc").header("X-API-KEY", apiKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        book(999_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("player_not_found"));
    }

    private MarketOrder sell(int quantity, String price) {
        User seller = user();
        seed.holding(seller, player, quantity);
        return seed.sell(seller, player, quantity, price);
    }

    private User user() {
        return seed.user("u-" + UUID.randomUUID(), "1000.0000");
    }

    private ResultActions book(Long playerId) throws Exception {
        return mockMvc.perform(get("/orders/book").param("playerId", String.valueOf(playerId))
                .header("X-API-KEY", apiKey));
    }
}
