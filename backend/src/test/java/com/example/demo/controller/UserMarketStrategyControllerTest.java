package com.example.demo.controller;

import com.example.demo.adapter.footballdata.FootballDataAdapter;
import com.example.demo.adapter.whoscored.WhoScoredAdapter;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import com.example.demo.model.QuoteStatus;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.market.HoldingRepository;
import com.example.demo.repository.market.MarketOrderRepository;
import com.example.demo.repository.market.TradeRepository;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.RankingFixtures;
import com.example.demo.support.UnitTestProfile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static com.example.demo.support.StrategyComparisonFixtures.bought;
import static com.example.demo.support.StrategyComparisonFixtures.dataSnapshot;
import static com.example.demo.support.StrategyComparisonFixtures.quote;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 031, US1: {@code GET /users/{id}/portfolio?strategy=}. */
@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
@Transactional
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class UserMarketStrategyControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ApiKeyRepository apiKeys;
    @Autowired PlayerRepository players;
    @Autowired PlayerQuoteRepository quotes;
    @Autowired MarketOrderRepository orders;
    @Autowired TradeRepository trades;
    @Autowired HoldingRepository holdings;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @MockitoBean FootballDataAdapter footballDataAdapter;
    @MockitoBean WhoScoredAdapter whoScoredAdapter;

    private User owner;
    private User seller;
    private String ownerKey;
    private String adminKey;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        owner = AuthFixtures.user(users, "strategy-owner-" + suffix, Role.USER);
        seller = AuthFixtures.user(users, "strategy-seller-" + suffix, Role.USER);
        ownerKey = AuthFixtures.apiKey(apiKeys, owner);
        adminKey = AuthFixtures.apiKey(apiKeys, AuthFixtures.user(users, "strategy-admin-" + suffix, Role.ADMIN));
    }

    @Test
    void addsTheStrategyValuationToEachPositionAndToTheTotals() throws Exception {
        Player messi = player("Messi", "1.3000");
        Player pedri = player("Pedri", "1.0000");
        bought(orders, trades, holdings, owner, seller, messi, 15, "1.0667");
        bought(orders, trades, holdings, owner, seller, pedri, 4, "1.0000");
        quote(quotes, messi, "MOMENTUM", "1.0000", QuoteStatus.CALCULATED, "2026-10-01T00:00:00Z");
        quote(quotes, messi, "MOMENTUM", "1.1000", QuoteStatus.CALCULATED, "2026-10-28T03:00:00Z");
        quote(quotes, pedri, "MOMENTUM", "0.7500", QuoteStatus.NO_STATS, "2026-10-28T03:00:00Z");
        quote(quotes, pedri, "OFFICIAL", "9.0000", QuoteStatus.CALCULATED, "2026-10-29T03:00:00Z");

        mvc.perform(get(portfolio(owner)).param("strategy", "MOMENTUM").header("X-API-KEY", ownerKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions[0].playerName").value("Messi"))
                .andExpect(jsonPath("$.positions[0].currentValue").value(19.5))
                .andExpect(jsonPath("$.positions[0].strategyValuation.quotePrice").value(1.1))
                .andExpect(jsonPath("$.positions[0].strategyValuation.quoteStatus").value("CALCULATED"))
                .andExpect(jsonPath("$.positions[0].strategyValuation.quoteCalculatedAt")
                        .value("2026-10-28T03:00:00Z"))
                .andExpect(jsonPath("$.positions[0].strategyValuation.value").value(16.5))
                .andExpect(jsonPath("$.positions[1].strategyValuation.quoteStatus").value("NO_STATS"))
                .andExpect(jsonPath("$.positions[1].strategyValuation.value").value(3.0))
                .andExpect(jsonPath("$.strategyValuation.strategy.code").value("MOMENTUM"))
                .andExpect(jsonPath("$.strategyValuation.strategy.label").value("MOMENTUM v1"))
                .andExpect(jsonPath("$.strategyValuation.totalValue").value(19.5))
                .andExpect(jsonPath("$.strategyValuation.positionsWithoutQuote").value(0));
    }

    @Test
    void withoutStrategyTheResponseHasNoStrategyValuationAtAnyLevel() throws Exception {
        Player messi = player("Messi", "1.3000");
        bought(orders, trades, holdings, owner, seller, messi, 15, "1.0000");
        quote(quotes, messi, "MOMENTUM", "1.1000", QuoteStatus.CALCULATED, "2026-10-28T03:00:00Z");

        mvc.perform(get(portfolio(owner)).header("X-API-KEY", ownerKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions[0].currentValue").value(19.5))
                .andExpect(content().string(not(containsString("strategyValuation"))));
    }

    @Test
    void aPlayerWithoutQuoteReportsExplicitNulls() throws Exception {
        Player messi = player("Messi", "1.3000");
        bought(orders, trades, holdings, owner, seller, messi, 15, "1.0000");

        mvc.perform(get(portfolio(owner)).param("strategy", "MOMENTUM").header("X-API-KEY", ownerKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions[0].strategyValuation.value").isEmpty())
                .andExpect(content().string(containsString("\"quotePrice\":null")))
                .andExpect(content().string(containsString("\"totalValue\":null")))
                .andExpect(jsonPath("$.strategyValuation.positionsWithoutQuote").value(1));
    }

    @Test
    void anUnknownStrategyIsRejectedBeforeAuthorization() throws Exception {
        User other = AuthFixtures.user(users, "strategy-other-" + UUID.randomUUID(), Role.USER);

        mvc.perform(get(portfolio(owner)).param("strategy", "FOO").header("X-API-KEY", ownerKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unknown_strategy"))
                .andExpect(jsonPath("$.positions").doesNotExist());
        mvc.perform(get(portfolio(other)).param("strategy", "FOO").header("X-API-KEY", ownerKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unknown_strategy"));
    }

    @Test
    void accessRulesAreTheSameWithStrategy() throws Exception {
        User other = AuthFixtures.user(users, "strategy-other-" + UUID.randomUUID(), Role.USER);

        mvc.perform(get(portfolio(other)).param("strategy", "MOMENTUM").header("X-API-KEY", ownerKey))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
        mvc.perform(get(portfolio(owner)).param("strategy", "MOMENTUM"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/users/" + Long.MAX_VALUE + "/portfolio").param("strategy", "MOMENTUM")
                        .header("X-API-KEY", adminKey))
                .andExpect(status().isNotFound());
    }

    @Test
    void statementCountIsConstantAndNothingIsWrittenOrFetchedExternally() throws Exception {
        for (int i = 0; i < 2; i++) {
            seededPosition("Small" + i);
        }
        long few = measure();
        for (int i = 0; i < 18; i++) {
            seededPosition("Large" + i);
        }
        entityManager.flush();
        Map<String, Object> before = dataSnapshot(jdbc);

        long many = measure();

        assertThat(many).isEqualTo(few);
        assertThat(dataSnapshot(jdbc)).isEqualTo(before);
        verifyNoInteractions(footballDataAdapter, whoScoredAdapter);
    }

    private void seededPosition(String name) {
        Player player = player(name, "1.0000");
        bought(orders, trades, holdings, owner, seller, player, 2, "1.0000");
        quote(quotes, player, "MOMENTUM", "1.2000", QuoteStatus.CALCULATED, "2026-10-28T03:00:00Z");
    }

    private long measure() throws Exception {
        entityManager.flush();
        entityManager.clear();
        SessionFactory factory = entityManagerFactory.unwrap(SessionFactory.class);
        factory.getStatistics().clear();
        mvc.perform(get(portfolio(owner)).param("strategy", "MOMENTUM").header("X-API-KEY", ownerKey))
                .andExpect(status().isOk());
        return factory.getStatistics().getPrepareStatementCount();
    }

    private Player player(String name, String marketValue) {
        Player player = RankingFixtures.player(players, name, "Club", League.LA_LIGA, Position.FORWARD);
        player.setMarketValue(new BigDecimal(marketValue));
        return players.save(player);
    }

    private static String portfolio(User user) {
        return "/users/" + user.getId() + "/portfolio";
    }
}
