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

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
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
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofHours(1), "0 0 * * * *", true);
        service = new PlayerCatalogService(adapter, repository, tokenService, auditService, properties,
                registry.timer("catalog.synchronization"), registry);
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
        verify(adapter, org.mockito.Mockito.times(5)).fetchCompetitionTeams(anyString());
        verify(tokenService, org.mockito.Mockito.times(5)).initialize(any(), any(), any());
        verify(auditService, atLeastOnce()).append(any(), any(), anyString(), anyString(), anyString(),
                anyString(), any(), any(), any(), any(), any());
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
                .thenReturn(new FootballDataResponse(List.of()));
        var result = service.synchronizeCatalog();
        assertThat(result.status()).isEqualTo("PARTIAL_FAILURE");
        verify(repository, never()).deleteAll();
    }

    private FootballDataResponse response(long id) {
        return new FootballDataResponse(List.of(new FootballDataResponse.Team("Team", List.of(
                new FootballDataResponse.SquadMember(id, "Player", "Offence",
                        LocalDate.of(2000, 1, 1), null)))));
    }
}
