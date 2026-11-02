package com.example.demo.controller;

import com.example.demo.config.CacheConfig;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import com.example.demo.model.Role;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtUtil;
import com.example.demo.service.valuation.QuoteCalculationService;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.RankingFixtures;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
@Transactional
class RankingControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired PlayerRepository playerRepository;
    @Autowired PlayerQuoteRepository quoteRepository;
    @Autowired UserRepository userRepository;
    @Autowired ApiKeyRepository apiKeyRepository;
    @Autowired JwtUtil jwtUtil;
    @Autowired CacheManager cacheManager;
    @MockitoBean QuoteCalculationService calculationService;

    private String userKey;

    @BeforeEach
    void setUp() {
        cacheManager.getCache(CacheConfig.QUOTES_RANKING_CACHE).clear();
        String suffix = UUID.randomUUID().toString();
        userKey = AuthFixtures.apiKey(apiKeyRepository,
                AuthFixtures.user(userRepository, "ranking-" + suffix, Role.USER));
    }

    @Test
    void activeRankingIsOrderedUniqueAndIncludesDeterministicVariation() throws Exception {
        Player low = player("Low", "A", League.PREMIER_LEAGUE, Position.FORWARD);
        Player high = player("High", "B", League.PREMIER_LEAGUE, Position.MIDFIELDER);
        player("No quote", "C", League.PREMIER_LEAGUE, Position.DEFENDER);
        RankingFixtures.quote(quoteRepository, low, "OFFICIAL", 1, "8.0000", "2026-10-01T00:00:00Z");
        RankingFixtures.quote(quoteRepository, low, "OFFICIAL", 2, "10.0000", "2026-10-02T00:00:00Z");
        RankingFixtures.quote(quoteRepository, high, "OFFICIAL", 1, "15.0000", "2026-10-02T00:00:00Z");

        mockMvc.perform(get("/players/ranking").header("X-API-KEY", userKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].rank").value(1))
                .andExpect(jsonPath("$.items[0].player.name").value("High"))
                .andExpect(jsonPath("$.items[0].percentageChange").value(nullValue()))
                .andExpect(jsonPath("$.items[1].rank").value(2))
                .andExpect(jsonPath("$.items[1].value").value(10.0))
                .andExpect(jsonPath("$.items[1].percentageChange").value(25.0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void playersWithoutPositionAreListedWithAnEmptyPositionSet() throws Exception {
        Player withoutPosition = player("Sin posicion", "Club", League.BUNDESLIGA);
        Player forward = player("Delantero", "Club", League.BUNDESLIGA, Position.FORWARD);
        RankingFixtures.quote(quoteRepository, withoutPosition, "MOMENTUM", 1, "1.0000", "2026-10-02T00:00:00Z");
        RankingFixtures.quote(quoteRepository, forward, "MOMENTUM", 1, "0.9000", "2026-10-02T00:00:00Z");

        mockMvc.perform(get("/players/ranking").header("X-API-KEY", userKey)
                        .param("strategy", "MOMENTUM").param("league", "BUNDESLIGA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].player.name").value("Sin posicion"))
                .andExpect(jsonPath("$.items[0].player.positions.length()").value(0))
                .andExpect(jsonPath("$.items[1].player.positions[0]").value("FORWARD"));
    }

    @Test
    void strategyFiltersAndPageRanksApplyToTheFilteredSet() throws Exception {
        for (int i = 0; i < 22; i++) {
            Player player = player("P" + i, "Club", League.LA_LIGA, Position.FORWARD, Position.MIDFIELDER);
            RankingFixtures.quote(quoteRepository, player, "MOMENTUM", 1, (100 - i) + ".0000",
                    "2026-10-02T00:00:00Z");
        }
        Player excluded = player("Excluded", "Club", League.SERIE_A, Position.FORWARD);
        RankingFixtures.quote(quoteRepository, excluded, "MOMENTUM", 1, "999.0000", "2026-10-02T00:00:00Z");

        mockMvc.perform(get("/players/ranking").header("X-API-KEY", userKey)
                        .param("strategy", "MOMENTUM").param("league", "LA_LIGA")
                        .param("position", "FORWARD").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].rank").value(21))
                .andExpect(jsonPath("$.items[1].rank").value(22))
                .andExpect(jsonPath("$.totalElements").value(22))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void percentageChangeCoversPositiveNegativeZeroAndBothNullCases() throws Exception {
        history("Positive", "10.0000", "12.5000");
        history("Negative", "10.0000", "8.0000");
        history("Unchanged", "10.0000", "10.0000");
        history("Previous zero", "0.0000", "5.0000");
        Player single = player("No previous", "Club", League.PREMIER_LEAGUE, Position.FORWARD);
        RankingFixtures.quote(quoteRepository, single, "OFFICIAL", 1, "6.0000", "2026-10-02T00:00:00Z");

        mockMvc.perform(get("/players/ranking").header("X-API-KEY", userKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.player.name == 'Positive')].percentageChange", hasItem(25.0)))
                .andExpect(jsonPath("$.items[?(@.player.name == 'Negative')].percentageChange", hasItem(-20.0)))
                .andExpect(jsonPath("$.items[?(@.player.name == 'Unchanged')].percentageChange", hasItem(0.0)))
                .andExpect(jsonPath("$.items[?(@.player.name == 'Previous zero')].percentageChange",
                        hasItem(nullValue())))
                .andExpect(jsonPath("$.items[?(@.player.name == 'No previous')].percentageChange",
                        hasItem(nullValue())));
    }

    @Test
    void validatesContractAndUnknownStrategy() throws Exception {
        for (String query : new String[]{"?page=-1", "?page=no", "?size=0", "?size=101",
                "?league=", "?league=OTHER", "?position=", "?position=OTHER"}) {
            mockMvc.perform(get("/players/ranking" + query).header("X-API-KEY", userKey))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("validation_error"));
        }
        mockMvc.perform(get("/players/ranking").header("X-API-KEY", userKey).param("strategy", "UNKNOWN"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("unknown_strategy"));
    }

    @Test
    void authenticationAcceptsUserJwtAndApiKeyAndRejectsMissingOrInvalidCredentials() throws Exception {
        mockMvc.perform(get("/players/ranking")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/players/ranking").header("X-API-KEY", "invalid"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/players/ranking").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/players/ranking").header("X-API-KEY", userKey)).andExpect(status().isOk());
        String username = "ranking-jwt-" + UUID.randomUUID();
        AuthFixtures.user(userRepository, username, Role.USER);
        mockMvc.perform(get("/players/ranking").header(HttpHeaders.AUTHORIZATION,
                "Bearer " + jwtUtil.generateToken(username))).andExpect(status().isOk());
    }

    @Test
    void rankingAndPlayerQuoteRouteCoexist() throws Exception {
        Player player = player("Routes", "Club", League.BUNDESLIGA, Position.DEFENDER);
        mockMvc.perform(get("/players/ranking").header("X-API-KEY", userKey)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
        mockMvc.perform(get("/players/" + player.getId() + "/quotes").header("X-API-KEY", userKey))
                .andExpect(status().isOk());
    }

    private Player player(String name, String team, League league, Position... positions) {
        return RankingFixtures.player(playerRepository, name, team, league, positions);
    }

    private void history(String name, String previous, String current) {
        Player player = player(name, "Club", League.PREMIER_LEAGUE, Position.FORWARD);
        RankingFixtures.quote(quoteRepository, player, "OFFICIAL", 1, previous, "2026-10-01T00:00:00Z");
        RankingFixtures.quote(quoteRepository, player, "OFFICIAL", 1, current, "2026-10-02T00:00:00Z");
    }
}
