package com.example.demo.controller;

import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.service.market.MarketSeed;
import com.example.demo.service.market.MarketTestData;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.MarketMockMvcTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@MarketMockMvcTest
class OrderControllerSellTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ApplicationContext context;

    private MarketSeed seed;
    private Player player;
    private User seller;
    private String apiKey;

    @BeforeEach
    void setUp() {
        MarketTestData.clean(jdbcTemplate);
        seed = MarketSeed.from(context);
        player = seed.player("1.1000");
        seller = seed.user("seller-" + UUID.randomUUID(), "1000.0000");
        seed.holding(seller, player, 10);
        apiKey = AuthFixtures.apiKey(apiKeyRepository, seller);
    }

    @AfterEach
    void tearDown() {
        MarketTestData.clean(jdbcTemplate);
    }

    @Test
    void limitSellWithoutBuyersStaysOpen() throws Exception {
        sell("{\"playerId\":%d,\"quantity\":6,\"type\":\"LIMIT\",\"limitPrice\":1.2000}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.side").value("SELL"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.remainingQuantity").value(6));
    }

    @Test
    void marketSellAgainstABuyIsFilled() throws Exception {
        seed.buy(seed.user("buyer-" + UUID.randomUUID(), "1000.0000"), player, 5, "1.1000");

        sell("{\"playerId\":%d,\"quantity\":5,\"type\":\"MARKET\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.trades[0].total").value(5.5));
    }

    private org.springframework.test.web.servlet.ResultActions sell(String template) throws Exception {
        return mockMvc.perform(post("/orders/sell").header("X-API-KEY", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(template.formatted(player.getId())));
    }
}
