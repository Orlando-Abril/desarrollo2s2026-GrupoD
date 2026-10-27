package com.example.demo.controller;

import com.example.demo.adapter.footballdata.FootballDataAdapter;
import com.example.demo.adapter.whoscored.WhoScoredAdapter;
import com.example.demo.filter.CorrelationIdFilter;
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
import com.example.demo.security.JwtUtil;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
@Transactional
class PlayerDetailControllerTest {

    static final String OFFICIAL = "OFFICIAL";
    static final String MOMENTUM = "MOMENTUM";

    @Autowired MockMvc mockMvc;
    @Autowired PlayerRepository playerRepository;
    @Autowired PlayerStatsRepository statsRepository;
    @Autowired PlayerQuoteRepository quoteRepository;
    @Autowired UserRepository userRepository;
    @Autowired ApiKeyRepository apiKeyRepository;
    @Autowired JwtUtil jwtUtil;

    @MockitoBean FootballDataAdapter footballDataAdapter;
    @MockitoBean WhoScoredAdapter whoScoredAdapter;

    String userKey;

    @BeforeEach
    void setUpAuthentication() {
        userKey = AuthFixtures.apiKey(apiKeyRepository,
                AuthFixtures.user(userRepository, "detail-user-" + UUID.randomUUID(), Role.USER));
        when(footballDataAdapter.fetchCompetitionTeams(anyString()))
                .thenThrow(new AssertionError("GET /players/{id} no debe consultar Football-Data"));
        when(whoScoredAdapter.fetchLeaguePlayers(any()))
                .thenThrow(new AssertionError("GET /players/{id} no debe consultar WhoScored"));
    }

    @Test
    void returnsCompleteLocalDetailAndLatestActiveQuoteWithoutExternalCalls() throws Exception {
        Player player = player();
        stats(player);
        quote(player, OFFICIAL, 1, "1.1000", "6.5000", QuoteStatus.CALCULATED,
                "2026-10-19T18:00:00Z");
        quote(player, MOMENTUM, 2, "9.0000", "9.0000", QuoteStatus.CALCULATED,
                "2026-10-20T18:00:00Z");
        quote(player, OFFICIAL, 1, "1.2000", "8.0000", QuoteStatus.CALCULATED,
                "2026-10-20T18:00:00Z");
        quote(player, OFFICIAL, 2, "1.2450", "8.7500", QuoteStatus.CALCULATED,
                "2026-10-20T18:00:00Z");

        mockMvc.perform(user(get(path(player))))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationIdFilter.HEADER))
                .andExpect(jsonPath("$.player.id").value(player.getId()))
                .andExpect(jsonPath("$.player.externalId").value("3180"))
                .andExpect(jsonPath("$.player.fullName").value("Bukayo Saka"))
                .andExpect(jsonPath("$.player.team").value("Arsenal FC"))
                .andExpect(jsonPath("$.player.league").value("PREMIER_LEAGUE"))
                .andExpect(jsonPath("$.player.positions[0]").value("FORWARD"))
                .andExpect(jsonPath("$.player.nationality").value("England"))
                .andExpect(jsonPath("$.player.age").value(25))
                .andExpect(jsonPath("$.player.marketValue").value(1.245))
                .andExpect(jsonPath("$.stats.minutesPlayed").value(1980))
                .andExpect(jsonPath("$.stats.goals").value(12))
                .andExpect(jsonPath("$.stats.assists").value(9))
                .andExpect(jsonPath("$.stats.shots").value(61))
                .andExpect(jsonPath("$.stats.keyPasses").value(48))
                .andExpect(jsonPath("$.stats.tackles").value(21))
                .andExpect(jsonPath("$.stats.dribbles").value(37))
                .andExpect(jsonPath("$.stats.yellowCards").value(4))
                .andExpect(jsonPath("$.stats.redCards").value(0))
                .andExpect(jsonPath("$.stats.rating").value(7.42))
                .andExpect(jsonPath("$.stats.fetchedAt").value("2026-10-20T12:00:00Z"))
                .andExpect(jsonPath("$.quote.value").value(1.245))
                .andExpect(jsonPath("$.quote.score").value(8.75))
                .andExpect(jsonPath("$.quote.status").value("CALCULATED"))
                .andExpect(jsonPath("$.quote.strategy").value(OFFICIAL))
                .andExpect(jsonPath("$.quote.version").value(2))
                .andExpect(jsonPath("$.quote.calculatedAt").value("2026-10-20T18:00:00Z"));

        verifyNoInteractions(footballDataAdapter, whoScoredAdapter);
    }

    @Test
    void returnsNullStatsAndQuoteWhenComplementaryRowsAreAbsent() throws Exception {
        Player player = player();

        mockMvc.perform(user(get(path(player))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.player.id").value(player.getId()))
                .andExpect(jsonPath("$.stats").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.quote").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void keepsPartialStatsNullAndIgnoresQuotesFromInactiveStrategies() throws Exception {
        Player player = player();
        PlayerStats partial = new PlayerStats(player);
        partial.setFetchedAt(Instant.parse("2026-10-20T12:00:00Z"));
        statsRepository.save(partial);
        quote(player, MOMENTUM, 1, "2.0000", null, QuoteStatus.NO_STATS,
                "2026-10-20T18:00:00Z");

        mockMvc.perform(user(get(path(player))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.minutesPlayed").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.stats.goals").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.stats.rating").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.stats.fetchedAt").value("2026-10-20T12:00:00Z"))
                .andExpect(jsonPath("$.quote").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void returnsActiveQuoteWhenStatsAreAbsent() throws Exception {
        Player player = player();
        quote(player, OFFICIAL, 1, "1.0000", null, QuoteStatus.NO_STATS,
                "2026-10-20T18:00:00Z");

        mockMvc.perform(user(get(path(player))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.quote.value").value(1.0))
                .andExpect(jsonPath("$.quote.score").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.quote.status").value("NO_STATS"));
    }

    @Test
    void malformedOverflowAndNonPositiveIdsAreValidationErrors() throws Exception {
        mockMvc.perform(user(get("/players/abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        mockMvc.perform(user(get("/players/9223372036854775808")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        for (String id : new String[]{"0", "-1"}) {
            mockMvc.perform(user(get("/players/" + id)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("validation_error"))
                    .andExpect(jsonPath("$.message").value("El id del jugador debe ser mayor a cero"));
        }
    }

    @Test
    void positiveMissingPlayerIsPlayerNotFound() throws Exception {
        mockMvc.perform(user(get("/players/999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("player_not_found"));
    }

    @Test
    void noCredentialsReturns401WithoutLeakingPlayerExistence() throws Exception {
        mockMvc.perform(get("/players/999999"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("jugador"))));
    }

    @Test
    void userCanReadWithApiKeyAndJwt() throws Exception {
        Player player = player();

        mockMvc.perform(user(get(path(player)))).andExpect(status().isOk());

        String username = "detail-jwt-" + UUID.randomUUID();
        AuthFixtures.user(userRepository, username, Role.USER);
        mockMvc.perform(get(path(player)).header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + jwtUtil.generateToken(username)))
                .andExpect(status().isOk());
    }

    @Test
    void detailRankingAndQuoteHistoryMappingsCoexist() throws Exception {
        Player player = player();

        mockMvc.perform(user(get(path(player))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.player.id").value(player.getId()));
        mockMvc.perform(user(get("/players/ranking")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        mockMvc.perform(user(get(path(player) + "/quotes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    Player player() {
        return playerRepository.save(Player.builder()
                .externalId("3180")
                .fullName("Bukayo Saka")
                .team("Arsenal FC")
                .league(League.PREMIER_LEAGUE)
                .positions(Set.of(Position.FORWARD))
                .nationality("England")
                .age(25)
                .heightCm(178)
                .marketValue(new BigDecimal("1.2450"))
                .build());
    }

    PlayerStats stats(Player player) {
        PlayerStats stats = new PlayerStats(player);
        stats.setWhoscoredPlayerId("3180");
        stats.setMinutesPlayed(1980);
        stats.setGoals(12);
        stats.setAssists(9);
        stats.setShots(61);
        stats.setKeyPasses(48);
        stats.setTackles(21);
        stats.setDribbles(37);
        stats.setYellowCards(4);
        stats.setRedCards(0);
        stats.setRating(new BigDecimal("7.42"));
        stats.setFetchedAt(Instant.parse("2026-10-20T12:00:00Z"));
        return statsRepository.save(stats);
    }

    PlayerQuote quote(Player player, String strategy, int version, String value, String score,
                      QuoteStatus status, String calculatedAt) {
        return quoteRepository.save(PlayerQuote.builder()
                .player(player)
                .strategyCode(strategy)
                .strategyVersion(version)
                .value(new BigDecimal(value))
                .score(score == null ? null : new BigDecimal(score))
                .status(status)
                .calculatedAt(Instant.parse(calculatedAt))
                .runId(UUID.randomUUID())
                .correlationId(UUID.randomUUID().toString())
                .build());
    }

    MockHttpServletRequestBuilder user(MockHttpServletRequestBuilder request) {
        return request.header("X-API-KEY", userKey);
    }

    static String path(Player player) {
        return "/players/" + player.getId();
    }
}
