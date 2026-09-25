package com.example.demo.service;

import com.example.demo.adapter.footballdata.FootballDataAdapter;
import com.example.demo.adapter.footballdata.FootballDataMappings;
import com.example.demo.adapter.footballdata.dto.FootballDataResponse;
import com.example.demo.exception.FootballDataException;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import com.example.demo.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashSet;
import java.util.Map;

@Service
public class PlayerCatalogService {
    private static final Logger log = LoggerFactory.getLogger(PlayerCatalogService.class);

    private final FootballDataAdapter adapter;
    private final PlayerRepository playerRepository;
    private final TransactionOperations transactionOperations;
    private final Clock clock = Clock.systemUTC();

    public PlayerCatalogService(FootballDataAdapter adapter, PlayerRepository playerRepository,
                                TransactionOperations transactionOperations) {
        this.adapter = adapter;
        this.playerRepository = playerRepository;
        this.transactionOperations = transactionOperations;
    }

    /**
     * Sin transacción envolvente: cada jugador se persiste en la suya, así una falla
     * puntual descarta sólo ese jugador y el resto de la carga sigue. {@code synchronized}
     * serializa cargas concurrentes para que no dupliquen jugadores por externalId.
     */
    public synchronized SyncResult synchronizeCatalog() {
        RunCounts counts = new RunCounts();
        for (Map.Entry<String, League> competition : FootballDataMappings.competitions().entrySet()) {
            try {
                synchronizeCompetition(competition.getKey(), competition.getValue(), counts);
            } catch (FootballDataException ex) {
                counts.failedLeagues++;
                log.warn("catalog_sync_league_failed league={} code={}", competition.getValue(), ex.getCode());
            }
        }
        String terminal = terminalStatus(counts);
        log.info("catalog_sync_finished result={} processed={} failedLeagues={} failedPlayers={}",
                terminal, counts.processed, counts.failedLeagues, counts.failedPlayers);
        return new SyncResult(terminal, counts.processed, counts.failedLeagues, counts.failedPlayers);
    }

    private void synchronizeCompetition(String code, League league, RunCounts counts) {
        FootballDataResponse response = adapter.fetchCompetitionTeams(code);
        for (FootballDataResponse.Team team : response.teams()) {
            if (team.name() == null || team.name().isBlank()) continue;
            for (FootballDataResponse.SquadMember member : team.squad()) {
                if (member.id() == null || member.name() == null || member.name().isBlank()) continue;
                if (persistPlayer(member, team.name(), league)) {
                    counts.processed++;
                } else {
                    counts.failedPlayers++;
                }
            }
        }
    }

    private boolean persistPlayer(FootballDataResponse.SquadMember member, String team, League league) {
        try {
            transactionOperations.executeWithoutResult(status -> upsert(member, team, league));
            return true;
        } catch (RuntimeException ex) {
            String code = ex instanceof DataIntegrityViolationException
                    ? "data_integrity_violation" : "internal_persistence_error";
            log.warn("catalog_sync_player_failed league={} externalId={} code={}", league, member.id(), code);
            return false;
        }
    }

    static String terminalStatus(RunCounts counts) {
        if (counts.failedLeagues == 0 && counts.failedPlayers == 0) return "COMPLETED";
        return counts.processed == 0 ? "FAILED" : "PARTIAL_FAILURE";
    }

    static final class RunCounts {
        int processed;
        int failedLeagues;
        int failedPlayers;
    }

    private void upsert(FootballDataResponse.SquadMember member, String team, League league) {
        String externalId = member.id().toString();
        Player player = playerRepository.findByExternalId(externalId).orElse(null);
        if (player == null) {
            player = Player.builder().externalId(externalId).marketValue(new BigDecimal("1.00")).build();
        }
        player.setFullName(member.name());
        player.setTeam(team);
        player.setLeague(league);
        player.setNationality(blankToNull(member.nationality()));
        player.setAge(age(member.dateOfBirth()));
        HashSet<Position> positions = new HashSet<>();
        FootballDataMappings.position(member.position()).ifPresent(positions::add);
        player.setPositions(positions);
        playerRepository.saveAndFlush(player);
    }

    private Integer age(LocalDate birthDate) {
        return birthDate == null ? null : Period.between(birthDate, LocalDate.now(clock)).getYears();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record SyncResult(String status, int processed, int failedLeagues, int failedPlayers) {
    }
}
