package com.example.demo.adapter.whoscored;

import com.example.demo.adapter.whoscored.dto.WhoScoredFeedResponse;
import com.example.demo.adapter.whoscored.dto.WhoScoredPlayerStats;
import com.example.demo.config.WhoScoredProperties;
import com.example.demo.exception.WhoScoredException;
import com.example.demo.model.League;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Integración con el feed JSON interno de WhoScored ({@code /statisticsfeed/1/getplayerstatistics}).
 * El endpoint no es oficial ni estable (Cloudflare bloquea al cliente HTTP de Java; por eso el transporte por
 * defecto es un navegador, ver {@link WhoScoredFeedClient}). Una liga se obtiene con 4 consultas y se devuelve
 * sólo si las 4 respuestas son válidas: nunca un conjunto parcial.
 * Ver specs/005-whoscored-player-stats/contracts/whoscored-source.md.
 */
@Component
public class WhoScoredAdapter {
    private static final Logger log = LoggerFactory.getLogger(WhoScoredAdapter.class);

    static final String FEED_PATH = "/statisticsfeed/1/getplayerstatistics";

    /** Marcadores de challenge anti-bot: un cuerpo así nunca se interpreta como JSON. */
    private static final List<String> BLOCK_MARKERS = List.of(
            "<title>Just a moment...</title>", "challenges.cloudflare.com", "_cf_chl_opt",
            "_Incapsula_Resource", "Incapsula incident ID");

    private static final List<FeedQuery> LEAGUE_QUERIES = List.of(
            new FeedQuery("summary", "all", 0),
            new FeedQuery("shots", "zones", 2),
            new FeedQuery("key-passes", "length", 2),
            new FeedQuery("tackles", "success", 2));

    private final WhoScoredFeedClient feedClient;
    private final WhoScoredProperties properties;
    private final WhoScoredStatsMapper mapper;
    private final Clock clock = Clock.systemUTC();
    /** {@link System#nanoTime()} de la última consulta real; {@code null} si todavía no hubo ninguna. */
    private Long lastRequestNanos;

    public WhoScoredAdapter(WhoScoredFeedClient feedClient, WhoScoredProperties properties,
                            WhoScoredStatsMapper mapper) {
        this.feedClient = feedClient;
        this.properties = properties;
        this.mapper = mapper;
    }

    /** Todas las filas {@code (jugador, equipo)} de la liga, o {@link WhoScoredException} si falta alguna respuesta. */
    public List<WhoScoredPlayerStats> fetchLeaguePlayers(League league) {
        int tournamentId = properties.tournaments().get(league);
        WhoScoredFeedResponse summary = fetch(league, tournamentId, LEAGUE_QUERIES.get(0));
        WhoScoredFeedResponse shots = fetch(league, tournamentId, LEAGUE_QUERIES.get(1));
        WhoScoredFeedResponse keyPasses = fetch(league, tournamentId, LEAGUE_QUERIES.get(2));
        WhoScoredFeedResponse tackles = fetch(league, tournamentId, LEAGUE_QUERIES.get(3));
        return mapper.merge(summary, shots, keyPasses, tackles, clock.instant());
    }

    /** Libera el transporte al terminar una ejecución (cierra el navegador); la próxima lo vuelve a abrir. */
    public void endRun() {
        feedClient.close();
    }

    /** Una consulta con, a lo sumo, un único reintento y sólo ante bloqueo. */
    private WhoScoredFeedResponse fetch(League league, int tournamentId, FeedQuery query) {
        waitSinceLastRequest(properties.requestDelay());
        try {
            return mapper.parse(request(tournamentId, query));
        } catch (WhoScoredException ex) {
            if (!WhoScoredException.BLOCKED.equals(ex.getCode())) {
                throw ex;
            }
            log.warn("whoscored_stats_request_blocked league={} category={} attempt=1", league, query.category());
            if (!properties.blockRetry().enabled()) {
                throw ex;
            }
        }
        waitSinceLastRequest(max(properties.blockRetry().delay(), properties.requestDelay()));
        try {
            return mapper.parse(request(tournamentId, query));
        } catch (WhoScoredException ex) {
            if (WhoScoredException.BLOCKED.equals(ex.getCode())) {
                log.warn("whoscored_stats_request_blocked league={} category={} attempt=2", league, query.category());
            }
            throw ex;
        }
    }

    private String request(int tournamentId, FeedQuery query) {
        lastRequestNanos = System.nanoTime();
        WhoScoredFeedClient.FeedResponse response = feedClient.get(pathAndQuery(tournamentId, query));
        return classify(response.status(), response.body());
    }

    private static String pathAndQuery(int tournamentId, FeedQuery query) {
        return UriComponentsBuilder.fromPath(FEED_PATH)
                .queryParam("category", query.category())
                .queryParam("subcategory", query.subcategory())
                .queryParam("statsAccumulationType", query.accumulationType())
                .queryParam("isCurrent", "true")
                .queryParam("playerId", "")
                .queryParam("teamIds", "")
                .queryParam("matchId", "")
                .queryParam("stageId", "")
                .queryParam("tournamentOptions", tournamentId)
                .queryParam("sortBy", "Rating")
                .queryParam("sortAscending", "")
                .queryParam("age", "")
                .queryParam("ageComparisonType", "")
                .queryParam("appearances", "")
                .queryParam("appearancesComparisonType", "")
                .queryParam("field", "Overall")
                .queryParam("nationality", "")
                .queryParam("positionOptions", "")
                .queryParam("timeOfTheGameEnd", "")
                .queryParam("timeOfTheGameStart", "")
                .queryParam("isMinApp", "false")
                .queryParam("page", "")
                .queryParam("includeZeroValues", "true")
                .queryParam("numberOfPlayersToPick", "")
                .build()
                .toUriString();
    }

    /** Clasifica la respuesta antes de leer JSON (contrato, sección 5). */
    static String classify(int status, String body) {
        if (status == 403 || BLOCK_MARKERS.stream().anyMatch(body::contains)) {
            throw new WhoScoredException(WhoScoredException.BLOCKED, "WhoScored bloqueó la consulta (status " + status + ")");
        }
        if (status != 200) {
            throw new WhoScoredException(WhoScoredException.HTTP_ERROR, "WhoScored respondió status " + status);
        }
        if (!body.stripLeading().startsWith("{")) {
            throw new WhoScoredException(WhoScoredException.UNEXPECTED_STRUCTURE, "WhoScored no devolvió JSON");
        }
        return body;
    }

    private void waitSinceLastRequest(Duration delay) {
        if (lastRequestNanos == null || delay.isZero()) {
            return;
        }
        long remainingNanos = delay.toNanos() - (System.nanoTime() - lastRequestNanos);
        if (remainingNanos <= 0) {
            return;
        }
        try {
            // Redondeo hacia arriba: nunca esperar menos que la espera configurada.
            Thread.sleep((remainingNanos + 999_999) / 1_000_000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new WhoScoredException(WhoScoredException.TIMEOUT, "Espera entre consultas interrumpida", ex);
        }
    }

    private static Duration max(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private record FeedQuery(String category, String subcategory, int accumulationType) {
    }
}
