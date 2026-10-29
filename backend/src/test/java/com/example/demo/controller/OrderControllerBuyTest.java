package com.example.demo.controller;

import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.security.JwtUtil;
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

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@MarketMockMvcTest
class OrderControllerBuyTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ApplicationContext context;

    private Player player;
    private User buyer;
    private String apiKey;

    @BeforeEach
    void setUp() {
        MarketTestData.clean(jdbcTemplate);
        MarketSeed seed = MarketSeed.from(context);
        player = seed.player("1.2000");
        User superuser = seed.superuser();
        seed.holding(superuser, player, 100);
        seed.sell(superuser, player, 100, "1.2000");
        buyer = seed.user("buyer-" + UUID.randomUUID(), "1000.0000");
        apiKey = AuthFixtures.apiKey(apiKeyRepository, buyer);
    }

    @AfterEach
    void tearDown() {
        MarketTestData.clean(jdbcTemplate);
    }

    @Test
    void marketBuyWithApiKeyReturnsTheFilledOrder() throws Exception {
        mockMvc.perform(post("/orders/buy").header("X-API-KEY", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"playerId\":%d,\"quantity\":10,\"type\":\"MARKET\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.playerId").value(player.getId()))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.type").value("MARKET"))
                .andExpect(jsonPath("$.limitPrice").value(nullValue()))
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.filledQuantity").value(10))
                .andExpect(jsonPath("$.remainingQuantity").value(0))
                .andExpect(jsonPath("$.averagePrice").value(1.2))
                .andExpect(jsonPath("$.closedAt").isNotEmpty())
                .andExpect(jsonPath("$.trades.length()").value(1))
                .andExpect(jsonPath("$.trades[0].quantity").value(10))
                .andExpect(jsonPath("$.trades[0].price").value(1.2))
                .andExpect(jsonPath("$.trades[0].total").value(12.0));
    }

    @Test
    void limitBuyWithJwtReturnsCreated() throws Exception {
        mockMvc.perform(post("/orders/buy").header("Authorization", "Bearer " + jwtUtil.generateToken(buyer.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"playerId\":%d,\"quantity\":5,\"type\":\"LIMIT\",\"limitPrice\":1.1000}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.limitPrice").value(1.1))
                .andExpect(jsonPath("$.averagePrice").value(nullValue()))
                .andExpect(jsonPath("$.trades.length()").value(0));
    }

    private String body(String template) {
        return template.formatted(player.getId());
    }
}
