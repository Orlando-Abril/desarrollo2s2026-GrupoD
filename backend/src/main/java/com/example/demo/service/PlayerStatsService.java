package com.example.demo.service;

import com.example.demo.adapter.whoscored.WhoScoredAdapter;
import com.example.demo.adapter.whoscored.dto.WhoScoredPlayerStats;
import com.example.demo.config.CacheConfig;
import com.example.demo.exception.WhoScoredException;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.PlayerStats;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.PlayerStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Enriquece los jugadores del catálogo con métricas de WhoScored.
 * <p>
 * Por liga: primero se resuelven desde la caché (por jugador) los jugadores con un resultado vigente; sólo si
 * queda alguno pendiente se descarga la liga (4 consultas). Unidad de falla de obtención: la liga (todo o nada):
 * si no se pudo obtener completa, sus pendientes quedan {@code failed} y conservan sus métricas previas.
 * El matching es por equipo y jugador con igualdad exacta de nombres normalizados (sin similitud ni alias), y
 * cada jugador se persiste en su propia transacción.
 */
@Service
public class PlayerStatsService {
    private static final Logger log = LoggerFactory.getLogger(PlayerStatsService.class);

    /** Largo mínimo (normalizado) de un nombre de WhoScored para asociar por inclusión: evita siglas como "psv". */
    private static final int MIN_INCLUDED_TEAM_NAME = 4;

    private final PlayerRepository playerRepository;
    private final PlayerStatsRepository statsRepository;
    private final WhoScoredAdapter adapter;
    private final TransactionOperations transactionOperations;
    private final CacheManager cacheManager;

    public PlayerStatsService(PlayerRepository playerRepository, PlayerStatsRepository statsRepository,
                              WhoScoredAdapter adapter, TransactionOperations transactionOperations,
                              CacheManager cacheManager) {
        this.playerRepository = playerRepository;
        this.statsRepository = statsRepository;
        this.adapter = adapter;
        this.transactionOperations = transactionOperations;
        this.cacheManager = cacheManager;
    }

    /** Sin transacción envolvente: una falla puntual no deshace lo ya guardado de otros jugadores. */
    public StatsUpdateResult updateAllStats() {
        Run run = new Run();
        try {
            Map<League, List<Player>> byLeague = playerRepository.findAll().stream()
                    .collect(Collectors.groupingBy(Player::getLeague, LinkedHashMap::new, Collectors.toList()));
            for (Map.Entry<League, List<Player>> entry : byLeague.entrySet()) {
                updateLeague(entry.getKey(), entry.getValue(), run);
            }
            return run.toResult();
        } finally {
            endRun();
        }
    }

    /** Cierra el transporte (navegador) de esta ejecución; un error al cerrar no cambia el resultado. */
    private void endRun() {
        try {
            adapter.endRun();
        } catch (RuntimeException ex) {
            log.warn("whoscored_stats_end_run_failed");
        }
    }

    private void updateLeague(League league, List<Player> players, Run run) {
        run.processed += players.size();
        List<Player> pending = new ArrayList<>();
        for (Player player : players) {
            WhoScoredPlayerStats cached = cacheGet(player, run);
            if (cached == null) {
                pending.add(player);
            } else if (persist(player, cached)) {
                run.updated++;
                run.fromCache++;
            } else {
                failed(player, WhoScoredException.PERSISTENCE_ERROR, run);
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        List<WhoScoredPlayerStats> rows;
        try {
            rows = adapter.fetchLeaguePlayers(league);
        } catch (WhoScoredException ex) {
            // detail = qué respondió la fuente (p. ej. "status 429"), para diagnosticar fallas pasajeras.
            // Sin comillas: el patrón JSON del log no las escapa. detail va último porque puede tener espacios.
            log.warn("whoscored_stats_league_failed league={} code={} detail={}", league, ex.getCode(), ex.getMessage());
            run.failed += pending.size();
            return;
        }
        Map<String, List<WhoScoredPlayerStats>> rowsByTeam = rows.stream()
                .collect(Collectors.groupingBy(WhoScoredPlayerStats::whoscoredTeamId, LinkedHashMap::new,
                        Collectors.toList()));
        Map<String, List<Player>> pendingByTeam = pending.stream()
                .collect(Collectors.groupingBy(Player::getTeam, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<Player>> team : pendingByTeam.entrySet()) {
            updateTeam(team.getKey(), team.getValue(), rowsByTeam, run);
        }
    }

    private void updateTeam(String localTeam, List<Player> players,
                            Map<String, List<WhoScoredPlayerStats>> rowsByTeam, Run run) {
        String teamId = matchTeam(localTeam, rowsByTeam, players, run);
        if (teamId == null) {
            return;
        }
        List<WhoScoredPlayerStats> teamRows = rowsByTeam.get(teamId);
        for (Player player : players) {
            updatePlayer(player, teamRows, run);
        }
    }

    /**
     * Equipo de WhoScored para un equipo local, dentro de la liga. Primero igualdad exacta de nombres normalizados;
     * si no hay ninguna, inclusión (research V6): el nombre local empieza con el de WhoScored ("tottenham hotspur" ↔
     * "tottenham", "internazionale milano" ↔ "inter") y, si eso no da un único candidato, contiene todas sus palabras
     * ("olympique marseille" ↔ "marseille"). Sin similitud ni alias; sólo se asocia con un único candidato.
     * Devuelve {@code null} (y registra a los jugadores como sin coincidencia) si no hay exactamente uno.
     */
    private String matchTeam(String localTeam, Map<String, List<WhoScoredPlayerStats>> rowsByTeam,
                             List<Player> players, Run run) {
        String local = NameNormalizer.team(localTeam);
        // Equivalencia fija (TeamAliases): tiene prioridad; si ese equipo no está en la liga, siguen las demás reglas.
        String alias = TeamAliases.whoScoredNameFor(local).orElse(null);
        if (alias != null) {
            List<String> aliased = teamsWhere(rowsByTeam, name -> name.equals(alias));
            if (aliased.size() == 1) {
                return included(localTeam, aliased.get(0), rowsByTeam, "alias");
            }
        }
        List<String> exact = teamsWhere(rowsByTeam, name -> !local.isEmpty() && name.equals(local));
        if (exact.size() == 1) {
            return exact.get(0);
        }
        if (exact.isEmpty() && !local.isEmpty()) {
            List<String> prefix = teamsWhere(rowsByTeam,
                    name -> name.length() >= MIN_INCLUDED_TEAM_NAME && local.startsWith(name));
            if (prefix.size() == 1) {
                return included(localTeam, prefix.get(0), rowsByTeam, "prefix");
            }
            Set<String> localWords = Set.copyOf(List.of(local.split(" ")));
            List<String> contains = teamsWhere(rowsByTeam,
                    name -> name.length() >= MIN_INCLUDED_TEAM_NAME && localWords.containsAll(List.of(name.split(" "))));
            if (contains.size() == 1) {
                return included(localTeam, contains.get(0), rowsByTeam, "contains");
            }
            if (prefix.isEmpty() && contains.isEmpty()) {
                players.forEach(player -> unmatched(player, "team_not_found", run));
                return null;
            }
        }
        players.forEach(player -> unmatched(player, "team_ambiguous", run));
        return null;
    }

    /** Asociación no exacta (equivalencia o inclusión): se registra para poder auditarla. */
    private static String included(String localTeam, String teamId, Map<String, List<WhoScoredPlayerStats>> rowsByTeam,
                                   String rule) {
        log.info("whoscored_stats_team_matched team={} whoscoredTeam={} rule={}", localTeam,
                String.join("/", teamNames(rowsByTeam.get(teamId))), rule);
        return teamId;
    }

    /** Ids de los equipos de WhoScored con algún nombre (normalizado) que cumple la condición. */
    private static List<String> teamsWhere(Map<String, List<WhoScoredPlayerStats>> rowsByTeam,
                                           Predicate<String> condition) {
        return rowsByTeam.entrySet().stream()
                .filter(team -> teamNames(team.getValue()).stream()
                        .map(NameNormalizer::team)
                        .anyMatch(condition))
                .map(Map.Entry::getKey)
                .toList();
    }

    private void updatePlayer(Player player, List<WhoScoredPlayerStats> teamRows, Run run) {
        String normalizedName = NameNormalizer.person(player.getFullName());
        List<WhoScoredPlayerStats> matches = teamRows.stream()
                .filter(row -> !normalizedName.isEmpty() && NameNormalizer.person(row.name()).equals(normalizedName))
                .toList();
        if (matches.size() != 1) {
            unmatched(player, matches.isEmpty() ? "player_not_found" : "player_ambiguous", run);
            return;
        }
        WhoScoredPlayerStats row = matches.get(0);
        if (!row.hasAnyMetric()) {
            failed(player, WhoScoredException.NO_METRICS, run);
            return;
        }
        if (persist(player, row)) {
            run.updated++;
            // Después de persistir: si la base falla, el jugador no queda en caché como resuelto.
            cachePut(player, row, run);
        } else {
            failed(player, WhoScoredException.PERSISTENCE_ERROR, run);
        }
    }

    private boolean persist(Player player, WhoScoredPlayerStats row) {
        try {
            transactionOperations.executeWithoutResult(status -> upsert(player, row));
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** Reemplazo total: todas las métricas se sobrescriben, incluidas las {@code null} (FR-010). */
    private void upsert(Player player, WhoScoredPlayerStats row) {
        PlayerStats stats = statsRepository.findById(player.getId()).orElseGet(() -> new PlayerStats(player));
        stats.setWhoscoredPlayerId(row.whoscoredPlayerId());
        stats.setMinutesPlayed(row.minutesPlayed());
        stats.setGoals(row.goals());
        stats.setAssists(row.assists());
        stats.setShots(row.shots());
        stats.setKeyPasses(row.keyPasses());
        stats.setTackles(row.tackles());
        stats.setYellowCards(row.yellowCards());
        stats.setRedCards(row.redCards());
        stats.setRating(row.rating());
        stats.setFetchedAt(row.fetchedAt());
        statsRepository.save(stats);
    }

    /**
     * Caché por jugador (FR-013). Si la caché falla, se registra y se sigue sin ella durante el resto de la
     * ejecución (FR-016), para no esperar un timeout de Redis por cada jugador.
     */
    private WhoScoredPlayerStats cacheGet(Player player, Run run) {
        if (!run.cacheAvailable) {
            return null;
        }
        try {
            Cache cache = cacheManager.getCache(CacheConfig.WHOSCORED_PLAYER_STATS_CACHE);
            return cache == null ? null : cache.get(player.getId(), WhoScoredPlayerStats.class);
        } catch (RuntimeException ex) {
            cacheUnavailable("get", run);
            return null;
        }
    }

    private void cachePut(Player player, WhoScoredPlayerStats row, Run run) {
        if (!run.cacheAvailable) {
            return;
        }
        try {
            Cache cache = cacheManager.getCache(CacheConfig.WHOSCORED_PLAYER_STATS_CACHE);
            if (cache != null) {
                cache.put(player.getId(), row);
            }
        } catch (RuntimeException ex) {
            cacheUnavailable("put", run);
        }
    }

    private static void cacheUnavailable(String operation, Run run) {
        log.warn("whoscored_stats_cache_unavailable operation={}", operation);
        run.cacheAvailable = false;
    }

    private static Set<String> teamNames(List<WhoScoredPlayerStats> rows) {
        Set<String> names = new LinkedHashSet<>();
        rows.forEach(row -> names.addAll(row.teamNames() == null ? List.of() : row.teamNames()));
        return names;
    }

    private static void unmatched(Player player, String reason, Run run) {
        log.info("whoscored_stats_unmatched playerId={} team={} reason={}", player.getId(), player.getTeam(), reason);
        run.unmatched++;
    }

    private static void failed(Player player, String code, Run run) {
        log.warn("whoscored_stats_player_failed playerId={} code={}", player.getId(), code);
        run.failed++;
    }

    /** Estado de una ejecución: contadores del resumen y disponibilidad de la caché. */
    private static final class Run {
        int processed;
        int updated;
        int fromCache;
        int unmatched;
        int failed;
        boolean cacheAvailable = true;

        StatsUpdateResult toResult() {
            return new StatsUpdateResult(processed, updated, fromCache, unmatched, failed);
        }
    }
}
