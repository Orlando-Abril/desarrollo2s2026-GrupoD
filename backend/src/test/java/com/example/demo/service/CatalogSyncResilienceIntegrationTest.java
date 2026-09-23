package com.example.demo.service;

import com.example.demo.adapter.footballdata.FootballDataAdapter;
import com.example.demo.adapter.footballdata.dto.FootballDataResponse;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.scheduler.PlayerCatalogScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sincronización real contra H2 (transacciones, auditoría y scheduler de Spring) con la
 * fuente externa simulada: cubre FR-016, FR-019 y FR-023 de la spec 003.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog-sync-resilience;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "football-data.enabled=true",
        "football-data.sync-cron=0 0 0 1 1 *",
        "market.superuser-username=market-admin"
})
class CatalogSyncResilienceIntegrationTest {
    private static final String TOO_LONG_NAME = "x".repeat(300);

    @MockitoBean FootballDataAdapter adapter;
    @Autowired PlayerCatalogService service;
    @Autowired PlayerCatalogScheduler scheduler;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from player_token_allocations");
        jdbc.update("delete from player_positions");
        jdbc.update("delete from players");
        jdbc.update("delete from catalog_sync_audit_events");
        jdbc.update("delete from api_keys");
        jdbc.update("delete from users");
        reset(adapter);
        when(adapter.fetchCompetitionTeams(anyString())).thenReturn(new FootballDataResponse(List.of()));
    }

    @Test
    void failedSynchronizationLeavesDurableFailedEventInAuditTable() {
        createSuperuser();
        when(adapter.fetchCompetitionTeams(anyString())).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(service::synchronizeCatalog).isInstanceOf(IllegalStateException.class);

        assertThat(actions()).containsExactly("STARTED", "FAILED");
        assertThat(jdbc.queryForObject(
                "select failure_code from catalog_sync_audit_events where action = 'FAILED'", String.class))
                .isEqualTo("internal_persistence_error");
        assertThat(jdbc.queryForObject(
                "select count(distinct correlation_id) from catalog_sync_audit_events", Integer.class)).isOne();
    }

    @Test
    void invalidPlayerAmongValidOnesIsSkippedAndRunEndsAsPartialFailure() {
        createSuperuser();
        when(adapter.fetchCompetitionTeams("PL")).thenReturn(squad(
                member(1001L, "Valid One"), member(1002L, TOO_LONG_NAME), member(1003L, "Valid Two")));

        var result = service.synchronizeCatalog();

        assertThat(result.status()).isEqualTo("PARTIAL_FAILURE");
        assertThat(result.processed()).isEqualTo(2);
        assertThat(result.failedPlayers()).isOne();
        assertThat(jdbc.queryForList("select external_id from players order by external_id", String.class))
                .containsExactly("1001", "1003");
        assertThat(jdbc.queryForObject("select count(*) from player_token_allocations", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList(
                "select entity_id from catalog_sync_audit_events where action = 'PLAYER_FAILED'", String.class))
                .containsExactly("1002");
        assertThat(jdbc.queryForObject("select count(*) from catalog_sync_audit_events "
                + "where action in ('PLAYER_CREATED','TOKENS_ALLOCATED')", Integer.class)).isEqualTo(4);
        assertThat(actions()).contains("PARTIAL_FAILURE").doesNotContain("FAILED", "COMPLETED");
    }

    @Test
    void startupWithoutSuperuserRecoversOnNextBootstrapRetryWithoutRestart() {
        when(adapter.fetchCompetitionTeams("PL")).thenReturn(squad(member(1001L, "Valid One")));

        scheduler.synchronizeOnStartup();

        verify(adapter, never()).fetchCompetitionTeams(anyString());
        assertThat(actions()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from players", Integer.class)).isZero();

        createSuperuser();
        scheduler.retryUntilFirstSuccessfulSnapshot();

        assertThat(actions()).contains("STARTED", "COMPLETED");
        assertThat(jdbc.queryForList("select external_id from players", String.class)).containsExactly("1001");

        clearInvocations(adapter);
        scheduler.retryUntilFirstSuccessfulSnapshot();
        verify(adapter, never()).fetchCompetitionTeams(anyString());
    }

    private void createSuperuser() {
        users.save(User.builder().username("market-admin").email("market-admin@test")
                .passwordHash("not-a-real-hash").role(Role.ADMIN).build());
    }

    private List<String> actions() {
        return jdbc.queryForList("select action from catalog_sync_audit_events "
                + "where entity_type = 'CatalogSync' order by id", String.class);
    }

    private static FootballDataResponse squad(FootballDataResponse.SquadMember... members) {
        return new FootballDataResponse(List.of(new FootballDataResponse.Team("Team", Arrays.asList(members))));
    }

    private static FootballDataResponse.SquadMember member(long id, String name) {
        return new FootballDataResponse.SquadMember(id, name, "Offence", LocalDate.of(2000, 1, 1), "Argentina");
    }
}
