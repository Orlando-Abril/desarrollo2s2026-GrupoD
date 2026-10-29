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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SC-008 de la spec 027: una compra que barre 20 niveles del libro responde en menos de 500 ms y el libro de un
 * jugador en menos de 200 ms, en promedio después de calentar. Como {@code PlayerDetailPerformanceIT}, no corre en
 * la suite normal: se ejecuta con {@code -Dtest=OrderPerformanceIT}.
 */
@MarketMockMvcTest
class OrderPerformanceIT {

    private static final int LEVELS = 20;

    @Autowired private MockMvc mockMvc;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ApplicationContext context;

    private MarketSeed seed;
    private Player player;
    private String apiKey;

    @BeforeEach
    void setUp() {
        MarketTestData.clean(jdbcTemplate);
        seed = MarketSeed.from(context);
        player = seed.player("1.0000");
        apiKey = AuthFixtures.apiKey(apiKeyRepository, seed.user("perf-" + UUID.randomUUID(), "100000.0000"));
    }

    @AfterEach
    void tearDown() {
        MarketTestData.clean(jdbcTemplate);
    }

    @Test
    void aBuySweepingTwentyLevelsTakesLessThanHalfASecond() throws Exception {
        List<Long> durationsMs = new ArrayList<>();
        for (int run = 0; run < 12; run++) {
            seedLevels();
            long started = System.nanoTime();
            mockMvc.perform(post("/orders/buy").header("X-API-KEY", apiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"playerId\":%d,\"quantity\":%d,\"type\":\"MARKET\"}"
                                    .formatted(player.getId(), LEVELS)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.trades.length()").value(LEVELS));
            durationsMs.add(Duration.ofNanos(System.nanoTime() - started).toMillis());
        }
        double average = durationsMs.subList(2, durationsMs.size()).stream().mapToLong(Long::longValue)
                .average().orElseThrow();
        System.out.println("ORDER_SWEEP_DURATIONS_MS=" + durationsMs);
        assertThat(average).isLessThan(500);
    }

    @Test
    void theBookOfAPlayerTakesLessThanTwoHundredMilliseconds() throws Exception {
        seedLevels();
        for (int warmup = 0; warmup < 5; warmup++) {
            book();
        }
        List<Long> durationsMs = new ArrayList<>();
        for (int request = 0; request < 50; request++) {
            long started = System.nanoTime();
            book();
            durationsMs.add(Duration.ofNanos(System.nanoTime() - started).toMillis());
        }
        double average = durationsMs.stream().mapToLong(Long::longValue).average().orElseThrow();
        System.out.println("ORDER_BOOK_DURATIONS_MS=" + durationsMs);
        assertThat(average).isLessThan(200);
    }

    /** 20 vendedores, cada uno con 1 token a un precio distinto entre 1.0000 y 1.0019. */
    private void seedLevels() {
        for (int level = 0; level < LEVELS; level++) {
            User seller = seed.user("seller-" + UUID.randomUUID(), "0");
            seed.holding(seller, player, 1);
            seed.sell(seller, player, 1, "1.%04d".formatted(level));
        }
    }

    private void book() throws Exception {
        mockMvc.perform(get("/orders/book").param("playerId", String.valueOf(player.getId()))
                        .header("X-API-KEY", apiKey))
                .andExpect(status().isOk());
    }
}
