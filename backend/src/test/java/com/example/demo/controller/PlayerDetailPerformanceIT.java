package com.example.demo.controller;

import com.example.demo.adapter.footballdata.FootballDataAdapter;
import com.example.demo.adapter.whoscored.WhoScoredAdapter;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.PlayerQuote;
import com.example.demo.model.PlayerStats;
import com.example.demo.model.Position;
import com.example.demo.model.QuoteStatus;
import com.example.demo.model.Role;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.PlayerStatsRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
@Transactional
class PlayerDetailPerformanceIT {

    @Autowired MockMvc mockMvc;
    @Autowired PlayerRepository playerRepository;
    @Autowired PlayerStatsRepository statsRepository;
    @Autowired PlayerQuoteRepository quoteRepository;
    @Autowired UserRepository userRepository;
    @Autowired ApiKeyRepository apiKeyRepository;

    @MockitoBean FootballDataAdapter footballDataAdapter;
    @MockitoBean WhoScoredAdapter whoScoredAdapter;

    @Test
    void atLeast95Of100SequentialRequestsFinishUnderOneSecond() throws Exception {
        List<Player> catalog = new ArrayList<>(2_500);
        for (int index = 0; index < 2_500; index++) {
            catalog.add(Player.builder()
                    .externalId("perf-" + index)
                    .fullName("Performance Player " + index)
                    .team("Performance FC")
                    .league(League.PREMIER_LEAGUE)
                    .positions(Set.of(Position.MIDFIELDER))
                    .nationality("Argentina")
                    .age(25)
                    .marketValue(new BigDecimal("1.0000"))
                    .build());
        }
        Player measured = playerRepository.saveAll(catalog).get(1_249);

        PlayerStats stats = new PlayerStats(measured);
        stats.setMinutesPlayed(1_800);
        stats.setGoals(10);
        stats.setFetchedAt(Instant.parse("2026-10-20T12:00:00Z"));
        statsRepository.save(stats);
        quoteRepository.save(PlayerQuote.builder()
                .player(measured)
                .strategyCode("OFFICIAL")
                .strategyVersion(1)
                .value(new BigDecimal("1.2500"))
                .score(new BigDecimal("8.0000"))
                .status(QuoteStatus.CALCULATED)
                .calculatedAt(Instant.parse("2026-10-20T18:00:00Z"))
                .runId(UUID.randomUUID())
                .correlationId(UUID.randomUUID().toString())
                .build());

        String apiKey = AuthFixtures.apiKey(apiKeyRepository,
                AuthFixtures.user(userRepository, "performance-user-" + UUID.randomUUID(), Role.USER));
        String path = "/players/" + measured.getId();

        for (int warmup = 0; warmup < 5; warmup++) {
            mockMvc.perform(get(path).header("X-API-KEY", apiKey)).andExpect(status().isOk());
        }

        List<Long> durationsMs = new ArrayList<>(100);
        for (int request = 0; request < 100; request++) {
            long started = System.nanoTime();
            mockMvc.perform(get(path).header("X-API-KEY", apiKey)).andExpect(status().isOk());
            durationsMs.add(Duration.ofNanos(System.nanoTime() - started).toMillis());
        }

        long underOneSecond = durationsMs.stream().filter(duration -> duration < 1_000).count();
        System.out.println("PERFORMANCE_DURATIONS_MS=" + durationsMs);
        System.out.println("PERFORMANCE_UNDER_1S=" + underOneSecond + "/100");
        assertThat(underOneSecond).isGreaterThanOrEqualTo(95);
    }
}
