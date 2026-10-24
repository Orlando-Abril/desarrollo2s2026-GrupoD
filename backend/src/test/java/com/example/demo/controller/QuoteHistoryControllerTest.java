package com.example.demo.controller;

import com.example.demo.adapter.footballdata.FootballDataAdapter;
import com.example.demo.adapter.whoscored.WhoScoredAdapter;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.PlayerQuote;
import com.example.demo.model.Position;
import com.example.demo.model.QuoteStatus;
import com.example.demo.model.Role;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.valuation.QuoteCalculationService;
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

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
@Transactional
class QuoteHistoryControllerTest {

    private static final String OFFICIAL = "OFFICIAL";
    private static final String MOMENTUM = "MOMENTUM";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerQuoteRepository quoteRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private QuoteCalculationService calculationService;

    @MockitoBean
    private FootballDataAdapter footballDataAdapter;

    @MockitoBean
    private WhoScoredAdapter whoScoredAdapter;

    private String userKey;

    @BeforeEach
    void setUp() {
        userKey = AuthFixtures.apiKey(apiKeyRepository,
                AuthFixtures.user(userRepository, "quote-user-" + UUID.randomUUID(), Role.USER));
    }

    @Test
    void activeStrategyHistoryIsOrderedAndKeepsThePublicShape() throws Exception {
        Player player = player(League.PREMIER_LEAGUE, "Arsenal");
        quote(player, OFFICIAL, 1, "1.1000", "7.5000", QuoteStatus.CALCULATED,
                "2026-10-08T18:00:00Z");
        quote(player, OFFICIAL, 2, "1.2450", null, QuoteStatus.NO_STATS,
                "2026-10-20T18:00:00Z");

        mockMvc.perform(user(get(path(player))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].value").value(1.1))
                .andExpect(jsonPath("$[0].score").value(7.5))
                .andExpect(jsonPath("$[0].status").value("CALCULATED"))
                .andExpect(jsonPath("$[0].strategy").value(OFFICIAL))
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[0].calculatedAt").value("2026-10-08T18:00:00Z"))
                .andExpect(jsonPath("$[1].score").value(nullValue()))
                .andExpect(jsonPath("$[1].version").value(2))
                .andExpect(jsonPath("$[1].calculatedAt", endsWith("Z")));

        verifyNoInteractions(footballDataAdapter, whoScoredAdapter);
    }

    @Test
    void explicitStrategyAndPlayerAreIsolatedAndTiesUseRegistrationOrder() throws Exception {
        Player requested = player(League.LA_LIGA, "Barcelona");
        Player other = player(League.SERIE_A, "Inter");
        quote(requested, OFFICIAL, 1, "1.0000", "5.0000", QuoteStatus.CALCULATED,
                "2026-10-10T12:00:00Z");
        quote(requested, MOMENTUM, 1, "1.2000", "6.0000", QuoteStatus.CALCULATED,
                "2026-10-10T12:00:00Z");
        quote(requested, MOMENTUM, 2, "1.3000", "6.5000", QuoteStatus.CALCULATED,
                "2026-10-10T12:00:00Z");
        quote(other, MOMENTUM, 1, "9.0000", "9.0000", QuoteStatus.CALCULATED,
                "2026-10-10T12:00:00Z");

        mockMvc.perform(user(get(path(requested)).param("strategy", MOMENTUM)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].value").value(1.2))
                .andExpect(jsonPath("$[1].value").value(1.3));
    }

    @Test
    void historiesAreAvailableForPlayersFromEveryLeague() throws Exception {
        for (League league : League.values()) {
            Player player = player(league, "Team " + league);
            quote(player, OFFICIAL, 1, "1.0000", null, QuoteStatus.NO_STATS,
                    "2026-10-01T00:00:00Z");

            mockMvc.perform(user(get(path(player))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }
    }

    @Test
    void fromAndToSupportOpenAndClosedUtcIntervals() throws Exception {
        Player player = player(League.BUNDESLIGA, "Bayern");
        quote(player, OFFICIAL, 1, "1.0000", null, QuoteStatus.NO_STATS,
                "2026-10-09T23:59:59Z");
        quote(player, OFFICIAL, 1, "2.0000", null, QuoteStatus.NO_STATS,
                "2026-10-10T00:00:00Z");
        quote(player, OFFICIAL, 1, "3.0000", null, QuoteStatus.NO_STATS,
                "2026-10-10T23:59:59.999999Z");
        quote(player, OFFICIAL, 1, "4.0000", null, QuoteStatus.NO_STATS,
                "2026-10-11T00:00:00Z");

        mockMvc.perform(user(get(path(player)).param("from", "2026-10-10")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(user(get(path(player)).param("to", "2026-10-10")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(user(get(path(player)).param("from", "2026-10-10").param("to", "2026-10-10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].value").value(2.0))
                .andExpect(jsonPath("$[1].value").value(3.0));
    }

    @Test
    void noDateFiltersReturnsCompleteHistory() throws Exception {
        Player player = player(League.LIGUE_1, "PSG");
        quote(player, OFFICIAL, 1, "1.0000", null, QuoteStatus.NO_STATS, "2024-01-01T00:00:00Z");
        quote(player, OFFICIAL, 1, "2.0000", null, QuoteStatus.NO_STATS, "2026-12-31T23:00:00Z");

        mockMvc.perform(user(get(path(player))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void existingPlayerWithoutMatchingQuotesReturnsEmptyArray() throws Exception {
        Player player = player(League.SERIE_A, "Milan");
        quote(player, MOMENTUM, 1, "1.0000", null, QuoteStatus.NO_STATS, "2026-10-01T00:00:00Z");

        mockMvc.perform(user(get(path(player)).param("strategy", OFFICIAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void quoteAtReturnsTheLastQuoteAvailableByTheEndOfTheDay() throws Exception {
        Player player = player(League.PREMIER_LEAGUE, "Liverpool");
        quote(player, OFFICIAL, 1, "1.0000", null, QuoteStatus.NO_STATS, "2026-10-01T12:00:00Z");
        quote(player, OFFICIAL, 1, "2.0000", null, QuoteStatus.NO_STATS, "2026-10-08T18:00:00Z");
        quote(player, OFFICIAL, 1, "3.0000", null, QuoteStatus.NO_STATS, "2026-10-15T12:00:00Z");

        mockMvc.perform(user(get(path(player)).param("at", "2026-10-10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(2.0))
                .andExpect(jsonPath("$.calculatedAt").value("2026-10-08T18:00:00Z"));
    }

    @Test
    void quoteAtUsesTheLatestRegistrationOnTimestampTieAndSupportsExplicitStrategy() throws Exception {
        Player player = player(League.LA_LIGA, "Real Madrid");
        quote(player, MOMENTUM, 1, "1.1000", "5.0000", QuoteStatus.CALCULATED,
                "2026-10-10T09:00:00Z");
        quote(player, MOMENTUM, 1, "1.2000", "6.0000", QuoteStatus.CALCULATED,
                "2026-10-10T20:00:00Z");
        quote(player, MOMENTUM, 2, "1.3000", "7.0000", QuoteStatus.CALCULATED,
                "2026-10-10T20:00:00Z");
        quote(player, MOMENTUM, 2, "9.0000", "9.0000", QuoteStatus.CALCULATED,
                "2026-10-11T00:00:00Z");

        mockMvc.perform(user(get(path(player)).param("strategy", MOMENTUM).param("at", "2026-10-10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(1.3))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void quoteAtBeforeTheFirstQuoteReturnsQuoteNotFound() throws Exception {
        Player player = player(League.BUNDESLIGA, "Dortmund");
        quote(player, OFFICIAL, 1, "1.0000", null, QuoteStatus.NO_STATS, "2026-10-10T00:00:00Z");

        mockMvc.perform(user(get(path(player)).param("at", "2026-10-01")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("quote_not_found"));
    }

    @Test
    void nonPositivePlayerIdsAreValidationErrors() throws Exception {
        for (String id : new String[]{"0", "-1"}) {
            mockMvc.perform(user(get("/players/" + id + "/quotes")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("validation_error"));
        }
    }

    @Test
    void positiveMissingPlayerIsPlayerNotFound() throws Exception {
        mockMvc.perform(user(get("/players/999999/quotes")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("player_not_found"));
    }

    @Test
    void invalidStrategiesAreRejected() throws Exception {
        Player player = player(League.SERIE_A, "Roma");

        mockMvc.perform(user(get(path(player)).param("strategy", "")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(user(get(path(player)).param("strategy", "official")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unknown_strategy"));
        mockMvc.perform(user(get(path(player)).param("strategy", "FOO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unknown_strategy"));
    }

    @Test
    void invalidDatesAndInvertedRangeAreValidationErrors() throws Exception {
        Player player = player(League.LIGUE_1, "Lyon");

        for (String from : new String[]{"", "2026-1-1", "2025-02-29"}) {
            mockMvc.perform(user(get(path(player)).param("from", from)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("validation_error"));
        }
        mockMvc.perform(user(get(path(player)).param("from", "2026-10-20").param("to", "2026-10-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
    }

    @Test
    void validLeapDayIsAccepted() throws Exception {
        Player player = player(League.LA_LIGA, "Valencia");

        mockMvc.perform(user(get(path(player)).param("from", "2024-02-29").param("to", "2024-02-29")))
                .andExpect(status().isOk());
    }

    @Test
    void noCredentialsReturns401WithoutLeakingPlayerExistence() throws Exception {
        mockMvc.perform(get("/players/999999/quotes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.not("player_not_found")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("jugador"))));
    }

    @Test
    void roleUserCanReadWithApiKeyAndJwt() throws Exception {
        Player player = player(League.PREMIER_LEAGUE, "Chelsea");
        quote(player, OFFICIAL, 1, "1.0000", null, QuoteStatus.NO_STATS, "2026-10-01T00:00:00Z");

        mockMvc.perform(user(get(path(player)))).andExpect(status().isOk());

        String username = "jwt-user-" + UUID.randomUUID();
        AuthFixtures.user(userRepository, username, Role.USER);
        mockMvc.perform(get(path(player)).header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + jwtUtil.generateToken(username)))
                .andExpect(status().isOk());
    }

    @Test
    void playerCatalogAndQuoteHistoryMappingsCoexist() throws Exception {
        Player player = player(League.BUNDESLIGA, "Leipzig");

        mockMvc.perform(user(get("/players"))).andExpect(status().isOk());
        mockMvc.perform(user(get(path(player)))).andExpect(status().isOk());
    }

    private Player player(League league, String team) {
        return playerRepository.save(Player.builder()
                .externalId(UUID.randomUUID().toString())
                .fullName("Player " + league)
                .team(team)
                .league(league)
                .positions(Set.of(Position.MIDFIELDER))
                .nationality("Argentina")
                .age(25)
                .heightCm(180)
                .marketValue(new BigDecimal("1.0000"))
                .build());
    }

    private void quote(Player player, String strategy, int version, String value, String score,
                       QuoteStatus status, String calculatedAt) {
        quoteRepository.save(PlayerQuote.builder()
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

    private MockHttpServletRequestBuilder user(MockHttpServletRequestBuilder request) {
        return request.header("X-API-KEY", userKey);
    }

    private static String path(Player player) {
        return "/players/" + player.getId() + "/quotes";
    }
}
