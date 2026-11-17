package com.example.demo.controller;

import com.example.demo.adapter.footballdata.FootballDataAdapter;
import com.example.demo.adapter.whoscored.WhoScoredAdapter;
import com.example.demo.config.CacheConfig;
import com.example.demo.model.League;
import com.example.demo.model.Position;
import com.example.demo.model.Role;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.valuation.QuoteCalculationService;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.RankingFixtures;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
@Transactional
class RankingHighFrequencyContractTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ApiKeyRepository keys;
    @Autowired PlayerRepository players;
    @Autowired PlayerQuoteRepository quotes;
    @Autowired CacheManager caches;
    @MockitoBean QuoteCalculationService calculationService;
    @MockitoBean FootballDataAdapter footballDataAdapter;
    @MockitoBean WhoScoredAdapter whoScoredAdapter;

    @Test
    void repeatedHttpRankingKeepsBodyOrderPaginationAndSecurity() throws Exception {
        caches.getCache(CacheConfig.QUOTES_RANKING_CACHE).clear();
        String key = AuthFixtures.apiKey(keys, AuthFixtures.user(users,
                "high-frequency-" + UUID.randomUUID(), Role.USER));
        var low = RankingFixtures.player(players, "Low", "Club", League.LA_LIGA, Position.FORWARD);
        var high = RankingFixtures.player(players, "High", "Club", League.LA_LIGA, Position.FORWARD);
        RankingFixtures.quote(quotes, low, "OFFICIAL", 1, "10.0000", "2026-10-01T00:00:00Z");
        RankingFixtures.quote(quotes, high, "OFFICIAL", 1, "20.0000", "2026-10-01T00:00:00Z");

        var first = mvc.perform(get("/players/ranking").header("X-API-KEY", key))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].player.name").value("High"))
                .andExpect(jsonPath("$.items[1].player.name").value("Low"))
                .andExpect(jsonPath("$.items[0].percentageChange").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2)).andReturn();
        var hit = mvc.perform(get("/players/ranking").header("X-API-KEY", key))
                .andExpect(status().isOk()).andReturn();
        assertThat(hit.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        mvc.perform(get("/players/ranking").header("X-API-KEY", key)
                        .param("strategy", "OFFICIAL").param("league", "SERIE_A").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/players/ranking")).andExpect(status().isUnauthorized());
    }
}
