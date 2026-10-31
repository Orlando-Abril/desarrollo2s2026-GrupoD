package com.example.demo.controller;

import com.example.demo.adapter.footballdata.FootballDataAdapter;
import com.example.demo.adapter.whoscored.WhoScoredAdapter;
import com.example.demo.model.ActiveStrategySetting;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import com.example.demo.model.QuoteStatus;
import com.example.demo.model.Role;
import com.example.demo.model.market.AuditActorType;
import com.example.demo.repository.ActiveStrategySettingRepository;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.UserRepository;
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
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.example.demo.support.StrategyComparisonFixtures.dataSnapshot;
import static com.example.demo.support.StrategyComparisonFixtures.quote;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 031, US2: {@code GET /quotes/opportunities}. */
@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
@Transactional
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class OpportunityControllerTest {

    private static final String AT = "2026-10-28T03:00:00Z";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ApiKeyRepository apiKeys;
    @Autowired PlayerRepository players;
    @Autowired PlayerQuoteRepository quotes;
    @Autowired ActiveStrategySettingRepository settings;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @MockitoBean FootballDataAdapter footballDataAdapter;
    @MockitoBean WhoScoredAdapter whoScoredAdapter;

    private String userKey;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from player_quotes");
        settings.deleteAll();
        userKey = AuthFixtures.apiKey(apiKeys,
                AuthFixtures.user(users, "opportunities-" + UUID.randomUUID(), Role.USER));
    }

    @Test
    void classifiesAgainstTheAlternativeAndReportsTheStrategies() throws Exception {
        seeded("Under", League.LA_LIGA, Position.MIDFIELDER, "1.0000", "1.2000");
        seeded("Over", League.LA_LIGA, Position.FORWARD, "2.0000", "1.5000");
        seeded("Equal", League.LA_LIGA, Position.FORWARD, "1.5000", "1.5000");
        Player noStats = seeded("No stats", League.LA_LIGA, Position.FORWARD, "1.0000", null);
        quote(quotes, noStats, "MOMENTUM", "3.0000", QuoteStatus.NO_STATS, AT);

        mvc.perform(get("/quotes/opportunities").header("X-API-KEY", userKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeStrategy.code").value("OFFICIAL"))
                .andExpect(jsonPath("$.alternativeStrategy.code").value("MOMENTUM"))
                .andExpect(jsonPath("$.marketStrategy.code").value("OFFICIAL"))
                .andExpect(jsonPath("$.pendingRecalculation").value(false))
                .andExpect(jsonPath("$.undervalued.length()").value(1))
                .andExpect(jsonPath("$.undervalued[0].name").value("Under"))
                .andExpect(jsonPath("$.undervalued[0].positions[0]").value("MIDFIELDER"))
                .andExpect(jsonPath("$.undervalued[0].marketValue").value(1.0))
                .andExpect(jsonPath("$.undervalued[0].alternativeQuote").value(1.2))
                .andExpect(jsonPath("$.undervalued[0].alternativeQuoteCalculatedAt").value(AT))
                .andExpect(jsonPath("$.undervalued[0].difference").value(0.2))
                .andExpect(jsonPath("$.undervalued[0].differencePercent").value(20.0))
                .andExpect(jsonPath("$.overvalued.length()").value(1))
                .andExpect(jsonPath("$.overvalued[0].name").value("Over"))
                .andExpect(jsonPath("$.overvalued[0].differencePercent").value(-25.0));
    }

    @Test
    void filtersByLeagueAndPosition() throws Exception {
        seeded("Liga forward", League.LA_LIGA, Position.FORWARD, "1.0000", "1.5000");
        seeded("Liga defender", League.LA_LIGA, Position.DEFENDER, "1.0000", "1.6000");
        seeded("Serie A forward", League.SERIE_A, Position.FORWARD, "1.0000", "0.5000");

        mvc.perform(get("/quotes/opportunities").header("X-API-KEY", userKey)
                        .param("league", "LA_LIGA").param("position", "FORWARD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.undervalued.length()").value(1))
                .andExpect(jsonPath("$.undervalued[0].name").value("Liga forward"))
                .andExpect(jsonPath("$.overvalued.length()").value(0));
    }

    @Test
    void limitCutsEachListAndInvalidParametersAreRejected() throws Exception {
        for (int i = 0; i < 7; i++) {
            seeded("Up" + i, League.LA_LIGA, Position.FORWARD, "1.0000", "1." + (i + 1) + "000");
            seeded("Down" + i, League.LA_LIGA, Position.FORWARD, "2.0000", "1." + (i + 1) + "000");
        }

        mvc.perform(get("/quotes/opportunities").header("X-API-KEY", userKey).param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.undervalued.length()").value(5))
                .andExpect(jsonPath("$.undervalued[0].name").value("Up6"))
                .andExpect(jsonPath("$.overvalued.length()").value(5))
                .andExpect(jsonPath("$.overvalued[0].name").value("Down0"));
        mvc.perform(get("/quotes/opportunities").header("X-API-KEY", userKey))
                .andExpect(jsonPath("$.undervalued.length()").value(7));
        for (String[] invalid : new String[][]{{"limit", "0"}, {"limit", "51"}, {"limit", "x"},
                {"league", "XX"}, {"position", "STRIKER"}}) {
            mvc.perform(get("/quotes/opportunities").header("X-API-KEY", userKey).param(invalid[0], invalid[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("validation_error"));
        }
    }

    @Test
    void requiresCredentials() throws Exception {
        mvc.perform(get("/quotes/opportunities")).andExpect(status().isUnauthorized());
    }

    @Test
    void withMomentumActiveTheAlternativeIsOfficial() throws Exception {
        Player player = RankingFixtures.player(players, "Momentum market", "Club", League.LA_LIGA, Position.FORWARD);
        quote(quotes, player, "MOMENTUM", "1.0000", QuoteStatus.CALCULATED, AT);
        quote(quotes, player, "OFFICIAL", "1.4000", QuoteStatus.CALCULATED, AT);
        setting("MOMENTUM", "MOMENTUM");

        mvc.perform(get("/quotes/opportunities").header("X-API-KEY", userKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeStrategy.code").value("MOMENTUM"))
                .andExpect(jsonPath("$.alternativeStrategy.code").value("OFFICIAL"))
                .andExpect(jsonPath("$.pendingRecalculation").value(false))
                .andExpect(jsonPath("$.undervalued[0].alternativeQuote").value(1.4));
    }

    @Test
    void withAPendingChangeTheListsAreEmpty() throws Exception {
        seeded("Under", League.LA_LIGA, Position.FORWARD, "1.0000", "1.2000");
        setting("MOMENTUM", "OFFICIAL");

        mvc.perform(get("/quotes/opportunities").header("X-API-KEY", userKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeStrategy.code").value("MOMENTUM"))
                .andExpect(jsonPath("$.alternativeStrategy.code").value("OFFICIAL"))
                .andExpect(jsonPath("$.marketStrategy.code").value("OFFICIAL"))
                .andExpect(jsonPath("$.pendingRecalculation").value(true))
                .andExpect(jsonPath("$.undervalued").isEmpty())
                .andExpect(jsonPath("$.overvalued").isEmpty());
    }

    @Test
    void statementCountIsConstantAndNothingIsWrittenOrFetchedExternally() throws Exception {
        for (int i = 0; i < 10; i++) {
            seeded("Few" + i, League.LA_LIGA, Position.FORWARD, "1.0000", "1.2000");
        }
        long few = measure();
        for (int i = 0; i < 90; i++) {
            seeded("Many" + i, League.LA_LIGA, Position.FORWARD, "1.0000", "1.2000");
        }
        entityManager.flush();
        Map<String, Object> before = dataSnapshot(jdbc);

        long many = measure();

        assertThat(many).isEqualTo(few);
        assertThat(dataSnapshot(jdbc)).isEqualTo(before);
        verifyNoInteractions(footballDataAdapter, whoScoredAdapter);
    }

    private long measure() throws Exception {
        entityManager.flush();
        entityManager.clear();
        SessionFactory factory = entityManagerFactory.unwrap(SessionFactory.class);
        factory.getStatistics().clear();
        mvc.perform(get("/quotes/opportunities").header("X-API-KEY", userKey)).andExpect(status().isOk());
        return factory.getStatistics().getPrepareStatementCount();
    }

    /** Jugador con precio de mercado fijado por OFFICIAL y, si {@code momentum} no es nulo, una cotización MOMENTUM. */
    private Player seeded(String name, League league, Position position, String marketValue, String momentum) {
        Player player = RankingFixtures.player(players, name, "Club", league, position);
        player.setMarketValue(new BigDecimal(marketValue));
        players.save(player);
        quote(quotes, player, "OFFICIAL", marketValue, QuoteStatus.CALCULATED, AT);
        if (momentum != null) {
            quote(quotes, player, "MOMENTUM", momentum, QuoteStatus.CALCULATED, AT);
        }
        return player;
    }

    private void setting(String active, String market) {
        ActiveStrategySetting setting = ActiveStrategySetting.empty();
        setting.changeActive(active, AuditActorType.SYSTEM, null, Instant.parse(AT));
        setting.recordMarket(market, Instant.parse(AT));
        settings.save(setting);
    }
}
