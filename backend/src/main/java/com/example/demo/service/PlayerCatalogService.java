package com.example.demo.service;

import com.example.demo.adapter.footballdata.FootballDataAdapter;
import com.example.demo.adapter.footballdata.FootballDataMappings;
import com.example.demo.adapter.footballdata.dto.FootballDataResponse;
import com.example.demo.config.FootballDataProperties;
import com.example.demo.exception.FootballDataException;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import com.example.demo.model.User;
import com.example.demo.repository.PlayerRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

@Service
public class PlayerCatalogService {
    private static final Logger log = LoggerFactory.getLogger(PlayerCatalogService.class);

    private final FootballDataAdapter adapter;
    private final PlayerRepository playerRepository;
    private final PlayerTokenInitializationService tokenService;
    private final CatalogSyncAuditService auditService;
    private final FootballDataProperties properties;
    private final Timer synchronizationTimer;
    private final Counter synchronizationErrors;
    private final Clock clock = Clock.systemUTC();

    public PlayerCatalogService(FootballDataAdapter adapter, PlayerRepository playerRepository,
                                PlayerTokenInitializationService tokenService,
                                CatalogSyncAuditService auditService,
                                FootballDataProperties properties,
                                @Qualifier("catalogSynchronizationDuration") Timer synchronizationTimer,
                                MeterRegistry registry) {
        this.adapter = adapter;
        this.playerRepository = playerRepository;
        this.tokenService = tokenService;
        this.auditService = auditService;
        this.properties = properties;
        this.synchronizationTimer = synchronizationTimer;
        this.synchronizationErrors = registry.counter("catalog.synchronization.errors");
    }

    @Transactional
    public SyncResult synchronizeCatalog() {
        User actor = tokenService.requireSuperuser();
        UUID correlationId = correlationId();
        Timer.Sample sample = Timer.start();
        auditService.append(actor.getId(), correlationId, "STARTED", "Catalog synchronization started",
                "CatalogSync", correlationId.toString(), null, "status=STARTED", null, 0, null);
        int processed = 0;
        int failedLeagues = 0;
        try {
            for (Map.Entry<String, League> competition : FootballDataMappings.competitions().entrySet()) {
                try {
                    processed += synchronizeCompetition(competition.getKey(), competition.getValue(), actor, correlationId);
                } catch (FootballDataException ex) {
                    failedLeagues++;
                    synchronizationErrors.increment();
                    log.warn("catalog_sync_league_failed correlationId={} league={} code={}",
                            correlationId, competition.getValue(), ex.getCode());
                }
            }
            String terminal = failedLeagues == 0 ? "COMPLETED"
                    : failedLeagues == FootballDataMappings.competitions().size() ? "FAILED" : "PARTIAL_FAILURE";
            auditService.append(actor.getId(), correlationId, terminal,
                    "Catalog synchronization finished", "CatalogSync", correlationId.toString(),
                    "status=STARTED", "status=" + terminal + ",processed=" + processed,
                    null, processed, failedLeagues == 0 ? null : "external_source_error");
            log.info("catalog_sync_finished correlationId={} result={} processed={} failedLeagues={}",
                    correlationId, terminal, processed, failedLeagues);
            return new SyncResult(terminal, processed, failedLeagues, correlationId);
        } catch (RuntimeException ex) {
            synchronizationErrors.increment();
            auditService.append(actor.getId(), correlationId, "FAILED", "Catalog synchronization aborted",
                    "CatalogSync", correlationId.toString(), "status=STARTED", "status=FAILED",
                    null, processed, "internal_persistence_error");
            log.error("catalog_sync_failed correlationId={} code=internal_persistence_error", correlationId);
            throw ex;
        } finally {
            sample.stop(synchronizationTimer);
            MDC.remove("correlationId");
        }
    }

    private int synchronizeCompetition(String code, League league, User actor, UUID correlationId) {
        FootballDataResponse response = adapter.fetchCompetitionTeams(code);
        int processed = 0;
        for (FootballDataResponse.Team team : response.teams()) {
            if (team.name() == null || team.name().isBlank()) continue;
            for (FootballDataResponse.SquadMember member : team.squad()) {
                if (member.id() == null || member.name() == null || member.name().isBlank()) continue;
                upsert(member, team.name(), league, actor, correlationId);
                processed++;
            }
        }
        return processed;
    }

    private void upsert(FootballDataResponse.SquadMember member, String team, League league,
                        User actor, UUID correlationId) {
        String externalId = member.id().toString();
        Player player = playerRepository.findByExternalId(externalId).orElse(null);
        boolean created = player == null;
        String before = created ? null : snapshot(player);
        if (created) {
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
        Player saved = playerRepository.saveAndFlush(player);
        if (created) tokenService.initialize(saved, actor, correlationId);
        auditService.append(actor.getId(), correlationId, created ? "PLAYER_CREATED" : "PLAYER_UPDATED",
                created ? "Player imported from Football-Data" : "Player catalog data refreshed",
                "Player", String.valueOf(saved.getId()), before, snapshot(saved), league, 1, null);
    }

    private Integer age(LocalDate birthDate) {
        return birthDate == null ? null : Period.between(birthDate, LocalDate.now(clock)).getYears();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String snapshot(Player player) {
        return "id=" + player.getId() + ",externalId=" + player.getExternalId()
                + ",name=" + player.getFullName() + ",team=" + player.getTeam()
                + ",league=" + player.getLeague() + ",positions=" + player.getPositions()
                + ",nationality=" + player.getNationality() + ",age=" + player.getAge()
                + ",marketValue=" + player.getMarketValue();
    }

    private UUID correlationId() {
        String existing = MDC.get("correlationId");
        UUID id;
        try {
            id = existing == null ? UUID.randomUUID() : UUID.fromString(existing);
        } catch (IllegalArgumentException ex) {
            id = UUID.randomUUID();
        }
        MDC.put("correlationId", id.toString());
        return id;
    }

    public record SyncResult(String status, int processed, int failedLeagues, UUID correlationId) {
    }
}
