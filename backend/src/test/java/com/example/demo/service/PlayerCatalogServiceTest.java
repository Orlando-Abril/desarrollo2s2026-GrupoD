package com.example.demo.service;

import com.example.demo.adapter.footballdata.FootballDataAdapter;
import com.example.demo.adapter.footballdata.dto.FootballDataResponse;
import com.example.demo.config.FootballDataProperties;
import com.example.demo.exception.FootballDataException;
import com.example.demo.exception.SuperuserUnavailableException;
import com.example.demo.model.Player;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.PlayerRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionOperations;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerCatalogServiceTest {
    @Mock FootballDataAdapter adapter;
    @Mock PlayerRepository repository;
    @Mock PlayerTokenInitializationService tokenService;
    @Mock CatalogSyncAuditService auditService;
    private User admin;
    private PlayerCatalogService service;

    @BeforeEach
    void setUp() {
        admin = User.builder().id(7L).username("market-admin").role(Role.ADMIN).build();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        var properties = new FootballDataProperties(URI.create("https://football.test/v4"), "secret",
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofHours(1), "0 0 * * * *",
                Duration.ofMinutes(5), true);
        service = new PlayerCatalogService(adapter, repository, tokenService, auditService, properties,
                TransactionOperations.withoutTransaction(), registry.timer("catalog.synchronization"), registry);
    }

    @Test
    void synchronizesFiveLeaguesAndInitializesNewPlayers() {
        when(tokenService.requireSuperuser()).thenReturn(admin);
        when(adapter.fetchCompetitionTeams(anyString())).thenReturn(response(100L));
        when(repository.findByExternalId("100")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Player.class))).thenAnswer(invocation -> {
            Player player = invocation.getArgument(0); player.setId(10L); return player;
        });
        var result = service.synchronizeCatalog();
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.processed()).isEqualTo(5);
        verify(adapter, times(5)).fetchCompetitionTeams(anyString());
        verify(tokenService, times(5)).initialize(any(), any(), any());
        verifyLifecycle("STARTED", times(1));
        verifyLifecycle("COMPLETED", times(1));
    }

    @Test
    void playerChangeEventsJoinThePlayerTransaction() {
        when(tokenService.requireSuperuser()).thenReturn(admin);
        when(adapter.fetchCompetitionTeams(anyString())).thenReturn(response(100L));
        when(repository.findByExternalId("100")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Player.class))).thenAnswer(invocation -> {
            Player player = invocation.getArgument(0); player.setId(10L); return player;
        });
        service.synchronizeCatalog();
        verify(auditService, times(5)).appendInCurrentTransaction(eq(7L), any(), eq("PLAYER_CREATED"), anyString(),
                eq("Player"), eq("10"), any(), any(), any(), any(), any());
        verify(auditService, never()).append(any(), any(), eq("PLAYER_CREATED"), any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    @Test
    void doesNoExternalOrPersistenceWorkWhenSuperuserIsUnavailable() {
        when(tokenService.requireSuperuser()).thenThrow(new SuperuserUnavailableException());
        assertThatThrownBy(service::synchronizeCatalog).isInstanceOf(SuperuserUnavailableException.class);
        verify(adapter, never()).fetchCompetitionTeams(anyString());
        verify(repository, never()).save(any());
        verify(auditService, never()).append(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void partialExternalFailureDoesNotDeleteLocalPlayers() {
        when(tokenService.requireSuperuser()).thenReturn(admin);
        when(adapter.fetchCompetitionTeams(anyString()))
                .thenThrow(new FootballDataException("external_source_error", "unavailable"))
                .thenReturn(response(100L));
        when(repository.saveAndFlush(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.synchronizeCatalog();
        assertThat(result.status()).isEqualTo("PARTIAL_FAILURE");
        assertThat(result.failedLeagues()).isOne();
        verify(repository, never()).deleteAll();
    }

    @Test
    void oneFailingPlayerIsSkippedWhileValidPlayersArePersisted() {
        when(tokenService.requireSuperuser()).thenReturn(admin);
        when(adapter.fetchCompetitionTeams(anyString())).thenReturn(new FootballDataResponse(List.of()));
        when(adapter.fetchCompetitionTeams("PL")).thenReturn(response(100L, 200L, 300L));
        when(repository.saveAndFlush(any(Player.class))).thenAnswer(invocation -> {
            Player player = invocation.getArgument(0);
            if ("200".equals(player.getExternalId())) throw new DataIntegrityViolationException("value too long");
            return player;
        });

        var result = service.synchronizeCatalog();

        assertThat(result.status()).isEqualTo("PARTIAL_FAILURE");
        assertThat(result.processed()).isEqualTo(2);
        assertThat(result.failedPlayers()).isOne();
        verify(tokenService, times(2)).initialize(any(), any(), any());
        verify(auditService, times(1)).append(eq(7L), any(), eq("PLAYER_FAILED"), anyString(), eq("Player"),
                eq("200"), any(), any(), any(), any(), eq("data_integrity_violation"));
        verifyLifecycle("PARTIAL_FAILURE", times(1));
    }

    @Test
    void everyPlayerFailingEndsAsFailedInsteadOfPartialFailure() {
        when(tokenService.requireSuperuser()).thenReturn(admin);
        when(adapter.fetchCompetitionTeams(anyString())).thenReturn(new FootballDataResponse(List.of()));
        when(adapter.fetchCompetitionTeams("PL")).thenReturn(response(200L));
        when(repository.saveAndFlush(any(Player.class)))
                .thenThrow(new DataIntegrityViolationException("value too long"));

        var result = service.synchronizeCatalog();

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.processed()).isZero();
        verifyLifecycle("FAILED", times(1));
        verifyLifecycle("PARTIAL_FAILURE", never());
    }

    @Test
    void internalErrorAfterStartedAppendsDurableFailedEventAndPropagates() {
        when(tokenService.requireSuperuser()).thenReturn(admin);
        when(adapter.fetchCompetitionTeams(anyString())).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(service::synchronizeCatalog).isInstanceOf(IllegalStateException.class);

        verifyLifecycle("STARTED", times(1));
        verify(auditService).append(eq(7L), any(), eq("FAILED"), anyString(), eq("CatalogSync"), anyString(),
                any(), any(), any(), any(), eq("internal_persistence_error"));
    }

    private void verifyLifecycle(String action, org.mockito.verification.VerificationMode mode) {
        verify(auditService, mode).append(eq(7L), any(), eq(action), anyString(), eq("CatalogSync"),
                argThat(id -> id != null && !id.isBlank()), any(), any(), any(), any(), any());
    }

    private FootballDataResponse response(long... ids) {
        List<FootballDataResponse.SquadMember> squad = java.util.Arrays.stream(ids)
                .mapToObj(id -> new FootballDataResponse.SquadMember(id, "Player " + id, "Offence",
                        LocalDate.of(2000, 1, 1), null))
                .toList();
        return new FootballDataResponse(List.of(new FootballDataResponse.Team("Team", squad)));
    }
}
