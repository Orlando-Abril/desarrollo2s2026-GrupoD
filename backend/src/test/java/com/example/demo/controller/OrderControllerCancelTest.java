package com.example.demo.controller;

import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.model.market.MarketOrder;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@MarketMockMvcTest
class OrderControllerCancelTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApiKeyRepository apiKeyRepository;
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
    void cancelsAnOwnOpenOrder() throws Exception {
        MarketOrder buy = seed.fill(seed.buy(owner, player, 10, "1.1000"), 4);

        cancel(buy.getId(), ownerKey)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(buy.getId()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.filledQuantity").value(4))
                .andExpect(jsonPath("$.closedAt").isNotEmpty())
                .andExpect(jsonPath("$.trades.length()").value(0));
    }

    @Test
    void theAveragePriceAfterCancellingMatchesTheOneAtCreation() throws Exception {
        User c = seed.user("c-" + UUID.randomUUID(), "0");
        User d = seed.user("d-" + UUID.randomUUID(), "0");
        seed.holding(c, player, 1);
        seed.holding(d, player, 3);
        seed.sell(c, player, 1, "1.0000");
        seed.sell(d, player, 3, "1.1000");
        String created = mockMvc.perform(post("/orders/buy").header("X-API-KEY", ownerKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":%d,\"quantity\":10,\"type\":\"LIMIT\",\"limitPrice\":1.1000}"
                                .formatted(player.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PARTIALLY_FILLED"))
                .andExpect(jsonPath("$.averagePrice").value(1.075))
                .andReturn().getResponse().getContentAsString();
        long orderId = com.jayway.jsonpath.JsonPath.parse(created).read("$.id", Long.class);

        cancel(orderId, ownerKey)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filledQuantity").value(4))
                .andExpect(jsonPath("$.averagePrice").value(1.075))
                .andExpect(jsonPath("$.trades.length()").value(0));
    }

    @Test
    void unknownOrderIsNotFound() throws Exception {
        rejected(cancel(999_999_999L, ownerKey), 404, "order_not_found");
    }

    @Test
    void anotherUsersOrderIsForbiddenAndStaysOpen() throws Exception {
        User other = seed.user("other-" + UUID.randomUUID(), "1000.0000");
        MarketOrder buy = seed.buy(other, player, 5, "1.1000");

        rejected(cancel(buy.getId(), ownerKey), 403, "order_forbidden");

        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "select status from orders where id = ?", String.class, buy.getId())).isEqualTo("OPEN");
    }

    @Test
    void theMarketMakerOrderIsForbiddenForUsers() throws Exception {
        User superuser = seed.superuser();
        seed.holding(superuser, player, 100);
        MarketOrder sell = seed.sell(superuser, player, 100, "1.1000");

        rejected(cancel(sell.getId(), ownerKey), 403, "order_forbidden");
    }

    @Test
    void theSuperuserCannotCancelThroughTheEndpoint() throws Exception {
        User superuser = seed.superuser();
        seed.holding(superuser, player, 100);
        MarketOrder sell = seed.sell(superuser, player, 100, "1.1000");

        rejected(cancel(sell.getId(), AuthFixtures.apiKey(apiKeyRepository, superuser)), 403, "superuser_not_allowed");
    }

    @Test
    void closedOrdersConflict() throws Exception {
        MarketOrder filled = seed.fill(seed.buy(owner, player, 2, "1.1000"), 2);
        MarketOrder cancelled = seed.buy(owner, player, 2, "1.1000");
        cancel(cancelled.getId(), ownerKey).andExpect(status().isOk());

        rejected(cancel(filled.getId(), ownerKey), 409, "order_closed");
        rejected(cancel(cancelled.getId(), ownerKey), 409, "order_closed");
    }

    @Test
    void nonNumericIdIsAValidationError() throws Exception {
        rejected(mockMvc.perform(delete("/orders/abc").header("X-API-KEY", ownerKey)), 400, "validation_error");
    }

    private ResultActions cancel(Long id, String apiKey) throws Exception {
        return mockMvc.perform(delete("/orders/{id}", id).header("X-API-KEY", apiKey));
    }

    private static void rejected(ResultActions actions, int status, String error) throws Exception {
        actions.andExpect(status().is(status)).andExpect(jsonPath("$.error").value(error));
    }
}
