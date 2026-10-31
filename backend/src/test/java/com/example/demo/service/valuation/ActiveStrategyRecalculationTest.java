package com.example.demo.service.valuation;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import com.example.demo.model.QuoteStatus;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.ActiveStrategySettingRepository;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.scheduler.QuoteRecalculationScheduler;
import com.example.demo.security.JwtUtil;
import com.example.demo.service.valuation.strategy.ValuationStrategyCatalog;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.RankingFixtures;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.UUID;

import static com.example.demo.support.StrategyComparisonFixtures.quote;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 031, US4: el cambio no toca precios, lo aplica el próximo recálculo (manual o programado) y persiste. */
@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
class ActiveStrategyRecalculationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PlayerRepository players;
    @Autowired PlayerQuoteRepository quotes;
    @Autowired ActiveStrategySettingRepository settings;
    @Autowired QuoteCalculationService calculationService;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwt;

    private String adminToken;
    private Player player;
    private String name;

    @BeforeEach
    @AfterEach
    void clean() {
        settings.deleteAll();
        jdbc.update("delete from audit_logs where entity_type = 'VALUATION_STRATEGY'");
    }

    @BeforeEach
    void setUp() {
        User admin = AuthFixtures.user(users, "recalc-admin-" + UUID.randomUUID(), Role.ADMIN);
        adminToken = "Bearer " + jwt.generateToken(admin.getUsername());
        name = "Strategy switch " + UUID.randomUUID();
        player = RankingFixtures.player(players, name, "Club", League.LA_LIGA, Position.FORWARD);
        player.setMarketValue(new BigDecimal("1.3000"));
        players.save(player);
        quote(quotes, player, "OFFICIAL", "1.3000", QuoteStatus.CALCULATED, "2026-10-01T00:00:00Z");
        quote(quotes, player, "MOMENTUM", "1.7000", QuoteStatus.CALCULATED, "2026-10-01T00:00:00Z");
    }

    @Test
    void theNextManualRecalculationAppliesTheNewStrategyAndDefaultsSwitchImmediately() throws Exception {
        mvc.perform(ranking())
                .andExpect(jsonPath("$.items[?(@.player.name == '" + name + "')].value", hasItem(1.3)));

        changeTo("MOMENTUM");

        assertThat(marketValue()).isEqualByComparingTo("1.3000");
        mvc.perform(ranking())
                .andExpect(jsonPath("$.items[?(@.player.name == '" + name + "')].value", hasItem(1.7)));
        mvc.perform(get("/players/" + player.getId() + "/quotes").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].strategy").value("MOMENTUM"));
        mvc.perform(get("/strategies/active").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.pendingRecalculation").value(true));

        mvc.perform(post("/quotes/recalculate").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeStrategy").value("MOMENTUM"))
                .andExpect(jsonPath("$.marketValueUpdated").value(true));

        assertThat(marketValue()).isEqualByComparingTo("1.7000");
        mvc.perform(get("/strategies/active").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.marketStrategy.code").value("MOMENTUM"))
                .andExpect(jsonPath("$.pendingRecalculation").value(false));
    }

    @Test
    void theScheduledRecalculationAlsoAppliesTheNewStrategy() throws Exception {
        changeTo("MOMENTUM");

        new QuoteRecalculationScheduler(calculationService).run();

        assertThat(marketValue()).isEqualByComparingTo("1.7000");
        mvc.perform(get("/strategies/active").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.marketStrategy.code").value("MOMENTUM"))
                .andExpect(jsonPath("$.pendingRecalculation").value(false));
    }

    @Test
    void thePersistedChoiceSurvivesARestartWithTheOldProperty() throws Exception {
        changeTo("MOMENTUM");

        ValuationStrategyCatalog restarted = new ValuationStrategyCatalog("OFFICIAL", settings);
        restarted.afterSingletonsInstantiated();

        assertThat(restarted.active().code()).isEqualTo("MOMENTUM");
        assertThat(restarted.propertyDefault().code()).isEqualTo("OFFICIAL");
    }

    private void changeTo(String strategy) throws Exception {
        mvc.perform(put("/strategies/active").header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"strategy\":\"" + strategy + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(true));
    }

    /** Filtrado por la liga y posición del jugador: otros tests pueden dejar jugadores sin posiciones en la base. */
    private MockHttpServletRequestBuilder ranking() {
        return get("/players/ranking").header(HttpHeaders.AUTHORIZATION, adminToken)
                .param("league", "LA_LIGA").param("position", "FORWARD").param("size", "100");
    }

    private BigDecimal marketValue() {
        return players.findById(player.getId()).orElseThrow().getMarketValue();
    }
}
