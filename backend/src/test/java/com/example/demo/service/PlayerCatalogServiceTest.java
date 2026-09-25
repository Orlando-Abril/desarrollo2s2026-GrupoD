package com.example.demo.service;

import com.example.demo.adapter.footballdata.FootballDataAdapter;
import com.example.demo.adapter.footballdata.dto.FootballDataResponse;
import com.example.demo.exception.FootballDataException;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import com.example.demo.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerCatalogServiceTest {
    @Mock FootballDataAdapter adapter;
    @Mock PlayerRepository repository;
    private PlayerCatalogService service;

    @BeforeEach
    void setUp() {
        service = new PlayerCatalogService(adapter, repository, TransactionOperations.withoutTransaction());
    }

    @Test
    void synchronizesFiveLeagues() {
        when(adapter.fetchCompetitionTeams(anyString())).thenReturn(new FootballDataResponse(List.of()));
        when(adapter.fetchCompetitionTeams("PL")).thenReturn(new FootballDataResponse(List.of(
                new FootballDataResponse.Team("Arsenal FC", List.of(new FootballDataResponse.SquadMember(
                        3180L, "Bukayo Saka", "Right Winger", LocalDate.of(2001, 9, 5), "England"))))));
        when(repository.findByExternalId("3180")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.synchronizeCatalog();

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.processed()).isOne();
        for (String code : List.of("PL", "PD", "SA", "BL1", "FL1")) {
            verify(adapter).fetchCompetitionTeams(code);
        }
        ArgumentCaptor<Player> saved = ArgumentCaptor.forClass(Player.class);
        verify(repository).saveAndFlush(saved.capture());
        Player player = saved.getValue();
        assertThat(player.getExternalId()).isEqualTo("3180");
        assertThat(player.getFullName()).isEqualTo("Bukayo Saka");
        assertThat(player.getTeam()).isEqualTo("Arsenal FC");
        assertThat(player.getLeague()).isEqualTo(League.PREMIER_LEAGUE);
        assertThat(player.getPositions()).containsExactly(Position.FORWARD);
        assertThat(player.getNationality()).isEqualTo("England");
        assertThat(player.getAge()).isNotNull().isGreaterThanOrEqualTo(24);
        assertThat(player.getMarketValue()).isEqualByComparingTo("1.00");
    }

    @Test
    void updatesExistingPlayerWithoutDuplicating() {
        Player existing = Player.builder().id(42L).externalId("100").fullName("Old Name").team("Old Team")
                .league(League.PREMIER_LEAGUE).positions(new HashSet<>(Set.of(Position.DEFENDER)))
                .marketValue(new BigDecimal("7.50")).build();
        when(adapter.fetchCompetitionTeams(anyString())).thenReturn(new FootballDataResponse(List.of()));
        when(adapter.fetchCompetitionTeams("PL")).thenReturn(response("New Team", 100L));
        when(repository.findByExternalId("100")).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.synchronizeCatalog();

        assertThat(result.status()).isEqualTo("COMPLETED");
        ArgumentCaptor<Player> saved = ArgumentCaptor.forClass(Player.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue()).isSameAs(existing);
        assertThat(existing.getId()).isEqualTo(42L);
        assertThat(existing.getTeam()).isEqualTo("New Team");
        assertThat(existing.getFullName()).isEqualTo("Player 100");
        assertThat(existing.getMarketValue()).isEqualByComparingTo("7.50");
    }

    @Test
    void skipsIncompleteMembersAndTeams() {
        when(adapter.fetchCompetitionTeams(anyString())).thenReturn(new FootballDataResponse(List.of()));
        when(adapter.fetchCompetitionTeams("PL")).thenReturn(new FootballDataResponse(List.of(
                new FootballDataResponse.Team("Team", List.of(
                        new FootballDataResponse.SquadMember(null, "No Id", "Offence", null, null),
                        new FootballDataResponse.SquadMember(1L, " ", "Offence", null, null),
                        new FootballDataResponse.SquadMember(2L, "Valid", "Offence", null, null))),
                new FootballDataResponse.Team(" ", List.of(
                        new FootballDataResponse.SquadMember(3L, "Teamless", "Offence", null, null))))));
        when(repository.saveAndFlush(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.synchronizeCatalog();

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.processed()).isOne();
        verify(repository).findByExternalId("2");
        verify(repository, times(1)).saveAndFlush(any(Player.class));
    }

    @Test
    void leagueFailureKeepsOtherLeagues() {
        when(adapter.fetchCompetitionTeams(anyString())).thenReturn(response("Team", 100L));
        when(adapter.fetchCompetitionTeams("PL"))
                .thenThrow(new FootballDataException("external_source_error", "unavailable"));
        when(repository.saveAndFlush(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.synchronizeCatalog();

        assertThat(result.status()).isEqualTo("PARTIAL_FAILURE");
        assertThat(result.failedLeagues()).isOne();
        assertThat(result.processed()).isEqualTo(4);
        verify(repository, never()).deleteAll();
        verify(repository, never()).delete(any(Player.class));
    }

    @Test
    void playerFailureIsSkipped() {
        when(adapter.fetchCompetitionTeams(anyString())).thenReturn(new FootballDataResponse(List.of()));
        when(adapter.fetchCompetitionTeams("PL")).thenReturn(response("Team", 100L, 200L, 300L));
        when(repository.saveAndFlush(any(Player.class))).thenAnswer(invocation -> {
            Player player = invocation.getArgument(0);
            if ("200".equals(player.getExternalId())) throw new DataIntegrityViolationException("value too long");
            return player;
        });

        var result = service.synchronizeCatalog();

        assertThat(result.status()).isEqualTo("PARTIAL_FAILURE");
        assertThat(result.processed()).isEqualTo(2);
        assertThat(result.failedPlayers()).isOne();
    }

    @Test
    void everythingFailingEndsAsFailed() {
        when(adapter.fetchCompetitionTeams(anyString()))
                .thenThrow(new FootballDataException("external_source_error", "unavailable"));

        var result = service.synchronizeCatalog();

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.processed()).isZero();
        assertThat(result.failedLeagues()).isEqualTo(5);
        verify(repository, never()).saveAndFlush(any());
    }

    private FootballDataResponse response(String team, long... ids) {
        List<FootballDataResponse.SquadMember> squad = java.util.Arrays.stream(ids)
                .mapToObj(id -> new FootballDataResponse.SquadMember(id, "Player " + id, "Offence",
                        LocalDate.of(2000, 1, 1), null))
                .toList();
        return new FootballDataResponse(List.of(new FootballDataResponse.Team(team, squad)));
    }
}
