package com.example.demo.e2e;

import com.example.demo.model.ActiveStrategySetting;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.QuoteStatus;
import com.example.demo.model.Role;
import com.example.demo.repository.ActiveStrategySettingRepository;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.E2EFixtures;
import com.example.demo.support.E2ETest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.example.demo.support.StrategyComparisonFixtures.quote;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cambio de estrategia activa contra PostgreSQL (spec 031). El esquema se crea de cero ({@code create-drop}), así
 * que el CHECK del enum de {@code audit_logs} ya incluye las acciones nuevas: este test no reproduce el riesgo de
 * bases existentes (research R10), que sólo cubre la verificación manual de T002.
 */
@E2ETest
class ActiveStrategyE2ETest {

    @LocalServerPort
    private int port;

    @Autowired E2EFixtures fixtures;
    @Autowired PlayerRepository players;
    @Autowired PlayerQuoteRepository quotes;
    @Autowired UserRepository users;
    @Autowired ApiKeyRepository apiKeys;
    @Autowired ActiveStrategySettingRepository settings;
    @Autowired JdbcTemplate jdbc;

    private Player player;
    private String adminKey;

    @BeforeEach
    void setUp() {
        settings.deleteAll();
        player = fixtures.createPlayersFromFiveLeagues().forLeague(League.LA_LIGA);
        quote(quotes, player, "MOMENTUM", "1.6000", QuoteStatus.CALCULATED, "2026-10-01T00:00:00Z");
        adminKey = AuthFixtures.apiKey(apiKeys, AuthFixtures.user(users, "e2e-strategy-admin", Role.ADMIN));
    }

    @Test
    void anAdminChangeIsAuditedAndAppliedByTheNextRecalculation() {
        RestClient client = RestClient.create("http://localhost:" + port);

        Map<String, Object> changed = client.put().uri("/strategies/active").header("X-API-KEY", adminKey)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("strategy", "MOMENTUM"))
                .retrieve().body(new ParameterizedTypeReference<>() { });
        assertThat(changed).containsEntry("changed", true).containsEntry("pendingRecalculation", true);

        Map<String, Object> run = client.post().uri("/quotes/recalculate").header("X-API-KEY", adminKey)
                .retrieve().body(new ParameterizedTypeReference<>() { });
        assertThat(run).containsEntry("activeStrategy", "MOMENTUM").containsEntry("marketValueUpdated", true);

        List<String> actions = jdbc.queryForList(
                "select action from audit_logs where entity_type = 'VALUATION_STRATEGY' order by id", String.class);
        assertThat(actions).containsExactly("ACTIVE_STRATEGY_CHANGED", "MARKET_STRATEGY_APPLIED");
        assertThat(players.findById(player.getId()).orElseThrow().getMarketValue())
                .isEqualByComparingTo(new BigDecimal("1.6000"));
        assertThat(settings.findById(ActiveStrategySetting.SINGLETON_ID).orElseThrow().getMarketStrategyCode())
                .isEqualTo("MOMENTUM");
    }
}
