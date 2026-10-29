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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cada código de error de crear órdenes, con el shape {@code {error, message}} y sin dejar rastro (spec 027, US4). */
@MarketMockMvcTest
class OrderControllerErrorsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ApplicationContext context;

    private MarketSeed seed;
    private Player player;
    private User superuser;
    private User user;
    private String apiKey;
    private List<Long> rowsBefore;

    @BeforeEach
    void setUp() {
        MarketTestData.clean(jdbcTemplate);
        seed = MarketSeed.from(context);
        player = seed.player("1.0000");
        superuser = seed.superuser();
        seed.holding(superuser, player, 100);
        seed.sell(superuser, player, 100, "1.0000");
        user = seed.user("user-" + UUID.randomUUID(), "1000.0000");
        apiKey = AuthFixtures.apiKey(apiKeyRepository, user);
        rowsBefore = rows();
    }

    @AfterEach
    void tearDown() {
        MarketTestData.clean(jdbcTemplate);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"playerId\":%d,\"quantity\":0,\"type\":\"MARKET\"}",
            "{\"playerId\":%d,\"quantity\":-1,\"type\":\"MARKET\"}",
            "{\"playerId\":%d,\"quantity\":1.5,\"type\":\"MARKET\"}",
            "{\"playerId\":%d,\"type\":\"MARKET\"}",
            "{\"quantity\":1,\"type\":\"MARKET\",\"ignored\":%d}",
            "{\"playerId\":%d,\"quantity\":1}",
            "{\"playerId\":%d,\"quantity\":1,\"type\":\"FOO\"}",
            "{\"playerId\":%d,\"quantity\":1,\"type\":\"LIMIT\"}",
            "{\"playerId\":%d,\"quantity\":1,\"type\":\"MARKET\",\"limitPrice\":1.0000}",
            "{\"playerId\":%d,\"quantity\":1,\"type\":\"LIMIT\",\"limitPrice\":0}",
            "{\"playerId\":%d,\"quantity\":1,\"type\":\"LIMIT\",\"limitPrice\":-1.0000}",
            "{\"playerId\":%d,\"quantity\":1,\"type\":\"LIMIT\",\"limitPrice\":1.23456}"})
    void invalidOrdersAreRejectedWithValidationError(String template) throws Exception {
        rejected(buy(template), 400, "validation_error");
    }

    @Test
    void unreadableBodyIsAValidationError() throws Exception {
        rejected(mockMvc.perform(post("/orders/buy").header("X-API-KEY", apiKey)
                .contentType(MediaType.APPLICATION_JSON).content("{")), 400, "validation_error");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.7999", "1.2001"})
    void limitPriceOutsideTheBandIsRejected(String price) throws Exception {
        rejected(buy("{\"playerId\":%d,\"quantity\":1,\"type\":\"LIMIT\",\"limitPrice\":" + price + "}"),
                400, "price_out_of_band");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.8000", "1.2000"})
    void limitPriceAtTheEdgesOfTheBandIsAccepted(String price) throws Exception {
        buy("{\"playerId\":%d,\"quantity\":1,\"type\":\"LIMIT\",\"limitPrice\":" + price + "}")
                .andExpect(status().isCreated());
    }

    @Test
    void unknownPlayerIsNotFound() throws Exception {
        rejected(mockMvc.perform(authenticated(post("/orders/buy"))
                        .content("{\"playerId\":999999999,\"quantity\":1,\"type\":\"MARKET\"}")),
                404, "player_not_found");
    }

    @Test
    void marketBuyAboveTheAvailableBalanceIsRejected() throws Exception {
        User poor = seed.user("poor-" + UUID.randomUUID(), "10.0000");
        String poorKey = AuthFixtures.apiKey(apiKeyRepository, poor);
        rowsBefore = rows();

        rejected(mockMvc.perform(post("/orders/buy").header("X-API-KEY", poorKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":%d,\"quantity\":11,\"type\":\"MARKET\"}".formatted(player.getId()))),
                422, "insufficient_balance");
    }

    @Test
    void restingLimitBuyAboveTheCreditsLeftByOtherReservationsIsRejected() throws Exception {
        User poor = seed.user("poor-" + UUID.randomUUID(), "10.0000");
        seed.buy(poor, player, 8, "0.9000");
        String poorKey = AuthFixtures.apiKey(apiKeyRepository, poor);
        rowsBefore = rows();

        rejected(mockMvc.perform(post("/orders/buy").header("X-API-KEY", poorKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":%d,\"quantity\":4,\"type\":\"LIMIT\",\"limitPrice\":0.9000}"
                                .formatted(player.getId()))),
                422, "insufficient_balance");
    }

    @Test
    void sellingWithoutTokensIsRejected() throws Exception {
        rejected(mockMvc.perform(authenticated(post("/orders/sell"))
                        .content("{\"playerId\":%d,\"quantity\":1,\"type\":\"MARKET\"}".formatted(player.getId()))),
                422, "insufficient_tokens");
    }

    @Test
    void theSuperuserCannotPlaceOrders() throws Exception {
        String superuserKey = AuthFixtures.apiKey(apiKeyRepository, superuser);

        rejected(mockMvc.perform(post("/orders/buy").header("X-API-KEY", superuserKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":%d,\"quantity\":1,\"type\":\"MARKET\"}".formatted(player.getId()))),
                403, "superuser_not_allowed");
    }

    @Test
    void aServiceKeyWithoutOwnerCannotPlaceOrders() throws Exception {
        String serviceKey = AuthFixtures.apiKey(apiKeyRepository, null);

        rejected(mockMvc.perform(post("/orders/buy").header("X-API-KEY", serviceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":%d,\"quantity\":1,\"type\":\"MARKET\"}".formatted(player.getId()))),
                403, "user_required");
    }

    @Test
    void everyOrderEndpointRequiresCredentials() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(post("/orders/buy"), post("/orders/sell"),
                delete("/orders/1"), get("/orders/book").param("playerId", "1"))) {
            mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/orders/buy").header("X-API-KEY", "invalid-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions buy(String template) throws Exception {
        return mockMvc.perform(authenticated(post("/orders/buy")).content(template.formatted(player.getId())));
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request.header("X-API-KEY", apiKey).contentType(MediaType.APPLICATION_JSON);
    }

    private void rejected(ResultActions actions, int status, String error) throws Exception {
        actions.andExpect(status().is(status))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").value(not(emptyOrNullString())));
        assertThat(rows()).as("no deja órdenes, operaciones ni auditoría").isEqualTo(rowsBefore);
    }

    private List<Long> rows() {
        return List.of(
                jdbcTemplate.queryForObject("select count(*) from orders", Long.class),
                jdbcTemplate.queryForObject("select count(*) from trades", Long.class),
                jdbcTemplate.queryForObject("select count(*) from audit_logs", Long.class));
    }
}
