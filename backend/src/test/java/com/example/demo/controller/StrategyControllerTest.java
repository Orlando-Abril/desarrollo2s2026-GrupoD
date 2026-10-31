package com.example.demo.controller;

import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.model.ActiveStrategySetting;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.model.market.AuditAction;
import com.example.demo.model.market.AuditActorType;
import com.example.demo.model.market.AuditLog;
import com.example.demo.repository.ActiveStrategySettingRepository;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.market.AuditLogRepository;
import com.example.demo.security.JwtUtil;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 031, US4 y US5: {@code GET} y {@code PUT /strategies/active}. */
@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
class StrategyControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ApiKeyRepository apiKeys;
    @Autowired PlayerRepository players;
    @Autowired ActiveStrategySettingRepository settings;
    @Autowired AuditLogRepository auditLogs;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwt;

    private User user;
    private User admin;

    @BeforeEach
    @AfterEach
    void clean() {
        settings.deleteAll();
        jdbc.update("delete from audit_logs where entity_type = 'VALUATION_STRATEGY'");
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        user = AuthFixtures.user(users, "strategy-user-" + suffix, Role.USER);
        admin = AuthFixtures.user(users, "strategy-admin-" + suffix, Role.ADMIN);
    }

    // US4: PUT

    @Test
    void aUserIsForbiddenAndNothingChanges() throws Exception {
        mvc.perform(change("MOMENTUM").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"))
                .andExpect(jsonPath("$.message").value("No tenés permisos para esta operación"));

        assertThat(settings.findById(ActiveStrategySetting.SINGLETON_ID)).isEmpty();
        assertThat(changeLogs()).isEmpty();
    }

    @Test
    void credentialsAreRequired() throws Exception {
        mvc.perform(change("MOMENTUM")).andExpect(status().isUnauthorized());
        mvc.perform(get("/strategies/active")).andExpect(status().isUnauthorized());
    }

    @Test
    void anAdminChangesTheStrategyWhichIsAuditedButDoesNotTouchMarketValues() throws Exception {
        Player player = RankingFixtures.player(players, "Untouched", "Club", League.LA_LIGA, Position.FORWARD);
        String correlationId = UUID.randomUUID().toString();

        mvc.perform(change(" MOMENTUM ").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(CorrelationIdFilter.HEADER, correlationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.active.code").value("MOMENTUM"))
                .andExpect(jsonPath("$.alternative.code").value("OFFICIAL"))
                .andExpect(jsonPath("$.marketStrategy.code").value("OFFICIAL"))
                .andExpect(jsonPath("$.pendingRecalculation").value(true))
                .andExpect(jsonPath("$.changedBy.actorType").value("USER"))
                .andExpect(jsonPath("$.changedBy.userId").value(admin.getId()))
                .andExpect(jsonPath("$.changedBy.username").value(admin.getUsername()))
                .andExpect(jsonPath("$.changedAt").isNotEmpty());

        List<AuditLog> logs = changeLogs();
        assertThat(logs).hasSize(1);
        AuditLog log = logs.get(0);
        assertThat(log.getActorType()).isEqualTo(AuditActorType.USER);
        assertThat(log.getActorUserId()).isEqualTo(admin.getId());
        assertThat(log.getEntityType()).isEqualTo("VALUATION_STRATEGY");
        assertThat(log.getEntityId()).isEqualTo("active");
        assertThat(log.getBeforeState()).contains("\"OFFICIAL\"").contains("\"version\"");
        assertThat(log.getAfterState()).contains("\"MOMENTUM\"");
        assertThat(log.getCorrelationId()).isEqualTo(correlationId);
        assertThat(players.findById(player.getId()).orElseThrow().getMarketValue())
                .isEqualByComparingTo(new BigDecimal("1.0000"));
    }

    @Test
    void reactivatingTheActiveStrategyIsANoOp() throws Exception {
        mvc.perform(change("MOMENTUM").header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isOk());

        mvc.perform(change("MOMENTUM").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false))
                .andExpect(jsonPath("$.active.code").value("MOMENTUM"));

        assertThat(changeLogs()).hasSize(1);
    }

    @Test
    void ownerlessServiceKeyIsAuditedAsSystemAndAdminOwnedKeyAsItsOwner() throws Exception {
        mvc.perform(change("MOMENTUM").header("X-API-KEY", AuthFixtures.apiKey(apiKeys, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changedBy.actorType").value("SYSTEM"))
                .andExpect(jsonPath("$.changedBy.userId").value(nullValue()));
        mvc.perform(change("OFFICIAL").header("X-API-KEY", AuthFixtures.apiKey(apiKeys, admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changedBy.actorType").value("USER"))
                .andExpect(jsonPath("$.changedBy.userId").value(admin.getId()));

        List<AuditLog> logs = changeLogs();
        assertThat(logs).extracting(AuditLog::getActorType).containsExactly(AuditActorType.SYSTEM, AuditActorType.USER);
        assertThat(logs.get(1).getActorUserId()).isEqualTo(admin.getId());
    }

    @Test
    void invalidStrategiesAndBodiesAreRejectedWithoutChanges() throws Exception {
        mvc.perform(change("FOO").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unknown_strategy"))
                .andExpect(jsonPath("$.message").value(containsString("OFFICIAL")))
                .andExpect(jsonPath("$.message").value(containsString("MOMENTUM")));
        for (String body : new String[]{"", "{}", "{\"strategy\":\"\"}", "{\"strategy\":", "[]"}) {
            mvc.perform(put("/strategies/active").contentType(MediaType.APPLICATION_JSON).content(body)
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                    .andExpect(status().isBadRequest());
        }

        assertThat(settings.findById(ActiveStrategySetting.SINGLETON_ID)
                .map(ActiveStrategySetting::getActiveCode)).isEmpty();
        assertThat(changeLogs()).isEmpty();
    }

    // US5: GET

    @Test
    void withoutAnyChangeTheActiveIsThePropertyForEveryAuthenticatedUser() throws Exception {
        for (User viewer : List.of(user, admin)) {
            mvc.perform(get("/strategies/active").header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active.code").value("OFFICIAL"))
                    .andExpect(jsonPath("$.active.version").value(1))
                    .andExpect(jsonPath("$.active.label").value("OFFICIAL v1"))
                    .andExpect(jsonPath("$.alternative.code").value("MOMENTUM"))
                    .andExpect(jsonPath("$.marketStrategy.code").value("OFFICIAL"))
                    .andExpect(jsonPath("$.pendingRecalculation").value(false))
                    .andExpect(jsonPath("$.changedAt").value(nullValue()))
                    .andExpect(jsonPath("$.changedBy").value(nullValue()))
                    .andExpect(jsonPath("$.changed").doesNotExist());
        }
    }

    @Test
    void aStoredPendingChangeIsReported() throws Exception {
        ActiveStrategySetting setting = ActiveStrategySetting.empty();
        setting.changeActive("MOMENTUM", AuditActorType.USER, admin.getId(), Instant.parse("2026-10-30T14:05:00Z"));
        setting.recordMarket("OFFICIAL", Instant.parse("2026-10-27T06:00:00Z"));
        settings.save(setting);

        mvc.perform(get("/strategies/active").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active.code").value("MOMENTUM"))
                .andExpect(jsonPath("$.alternative.code").value("OFFICIAL"))
                .andExpect(jsonPath("$.marketStrategy.code").value("OFFICIAL"))
                .andExpect(jsonPath("$.pendingRecalculation").value(true))
                .andExpect(jsonPath("$.changedAt").value("2026-10-30T14:05:00Z"))
                .andExpect(jsonPath("$.changedBy.username").value(admin.getUsername()));
    }

    private List<AuditLog> changeLogs() {
        return auditLogs.findByActionOrderByIdAsc(AuditAction.ACTIVE_STRATEGY_CHANGED);
    }

    private static MockHttpServletRequestBuilder change(String strategy) {
        return put("/strategies/active").contentType(MediaType.APPLICATION_JSON)
                .content("{\"strategy\":\"" + strategy + "\"}");
    }

    private String bearer(User account) {
        return "Bearer " + jwt.generateToken(account.getUsername());
    }
}
