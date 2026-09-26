package com.example.demo.adapter.whoscored;

import com.example.demo.adapter.whoscored.dto.WhoScoredFeedResponse;
import com.example.demo.adapter.whoscored.dto.WhoScoredFeedResponse.Row;
import com.example.demo.adapter.whoscored.dto.WhoScoredPlayerStats;
import com.example.demo.exception.WhoScoredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Único lugar que conoce los campos JSON del feed de WhoScored
 * (specs/005-whoscored-player-stats/contracts/whoscored-source.md, secciones 3 y 4).
 * Nunca inventa valores: ausente, {@code null} o no interpretable ⇒ {@code null}.
 */
@Component
public class WhoScoredStatsMapper {
    private static final Logger log = LoggerFactory.getLogger(WhoScoredStatsMapper.class);
    private static final BigDecimal MAX_RATING = BigDecimal.TEN;

    private final ObjectMapper objectMapper;

    public WhoScoredStatsMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public WhoScoredFeedResponse parse(String body) {
        WhoScoredFeedResponse response;
        try {
            response = objectMapper.readValue(body, WhoScoredFeedResponse.class);
        } catch (JacksonException ex) {
            throw new WhoScoredException(WhoScoredException.UNEXPECTED_STRUCTURE, "WhoScored devolvió JSON inválido", ex);
        }
        if (response == null || response.playerTableStats() == null) {
            throw new WhoScoredException(WhoScoredException.UNEXPECTED_STRUCTURE,
                    "WhoScored devolvió JSON sin playerTableStats");
        }
        return response;
    }

    /**
     * Combina las 4 respuestas de una liga por {@code (playerId, teamId)}. El resumen define las filas;
     * las consultas detalladas sólo completan su métrica (ausente ⇒ {@code null}).
     */
    public List<WhoScoredPlayerStats> merge(WhoScoredFeedResponse summary, WhoScoredFeedResponse shots,
                                            WhoScoredFeedResponse keyPasses, WhoScoredFeedResponse tackles,
                                            Instant fetchedAt) {
        Map<Key, Row> summaryRows = index(summary, "summary");
        if (summaryRows.isEmpty() && !summary.playerTableStats().isEmpty()) {
            throw new WhoScoredException(WhoScoredException.UNEXPECTED_STRUCTURE,
                    "WhoScored devolvió filas sin identificación de jugador o equipo");
        }
        Map<Key, Row> shotRows = index(shots, "shots");
        Map<Key, Row> keyPassRows = index(keyPasses, "key-passes");
        Map<Key, Row> tackleRows = index(tackles, "tackles");
        Map<String, Set<String>> teamNames = teamNames(List.of(summary, shots, keyPasses, tackles));

        List<WhoScoredPlayerStats> result = new ArrayList<>(summaryRows.size());
        for (Map.Entry<Key, Row> entry : summaryRows.entrySet()) {
            Key key = entry.getKey();
            Row row = entry.getValue();
            result.add(new WhoScoredPlayerStats(
                    key.playerId(), row.name(), key.teamId(), List.copyOf(teamNames.getOrDefault(key.teamId(), Set.of())),
                    integer(row.minsPlayed(), key, "minutesPlayed"),
                    integer(row.goal(), key, "goals"),
                    integer(row.assistTotal(), key, "assists"),
                    integer(detail(shotRows, key, Row::shotsTotal), key, "shots"),
                    integer(detail(keyPassRows, key, Row::keyPassesTotal), key, "keyPasses"),
                    integer(detail(tackleRows, key, Row::tackleWonTotal), key, "tackles"),
                    integer(row.yellowCard(), key, "yellowCards"),
                    integer(row.redCard(), key, "redCards"),
                    rating(row.rating(), key),
                    fetchedAt));
        }
        return result;
    }

    private Map<Key, Row> index(WhoScoredFeedResponse response, String category) {
        Map<Key, Row> rows = new LinkedHashMap<>();
        for (Row row : response.playerTableStats()) {
            String playerId = row == null ? null : id(row.playerId());
            String teamId = row == null ? null : id(row.teamId());
            if (playerId == null || teamId == null || row.name() == null || row.name().isBlank()) {
                log.warn("whoscored_stats_row_skipped category={} reason=missing_identity", category);
                continue;
            }
            Row previous = rows.putIfAbsent(new Key(playerId, teamId), row);
            if (previous != null) {
                log.warn("whoscored_stats_row_skipped category={} whoscoredPlayerId={} reason=duplicate_key",
                        category, playerId);
            }
        }
        return rows;
    }

    private static Map<String, Set<String>> teamNames(List<WhoScoredFeedResponse> responses) {
        Map<String, Set<String>> names = new HashMap<>();
        for (WhoScoredFeedResponse response : responses) {
            for (Row row : response.playerTableStats()) {
                String teamId = row == null ? null : id(row.teamId());
                if (teamId != null && row.teamName() != null && !row.teamName().isBlank()) {
                    names.computeIfAbsent(teamId, id -> new LinkedHashSet<>()).add(row.teamName().trim());
                }
            }
        }
        return names;
    }

    private static JsonNode detail(Map<Key, Row> rows, Key key, Function<Row, JsonNode> field) {
        Row row = rows.get(key);
        return row == null ? null : field.apply(row);
    }

    private static String id(JsonNode node) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) {
            return null;
        }
        long value = node.longValue();
        return value > 0 ? Long.toString(value) : null;
    }

    private Integer integer(JsonNode node, Key key, String metric) {
        BigDecimal value = number(node, key, metric);
        if (value == null) {
            return null;
        }
        if (value.signum() < 0) {
            invalid(key, metric);
            return null;
        }
        try {
            // Acepta 19 y 19.0 (tarjetas y totales detallados llegan como decimales); rechaza 1.5.
            return value.intValueExact();
        } catch (ArithmeticException ex) {
            invalid(key, metric);
            return null;
        }
    }

    private BigDecimal rating(JsonNode node, Key key) {
        BigDecimal value = number(node, key, "rating");
        if (value == null) {
            return null;
        }
        if (value.signum() < 0 || value.compareTo(MAX_RATING) > 0) {
            invalid(key, "rating");
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal number(JsonNode node, Key key, String metric) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (!node.isNumber()) {
            invalid(key, metric);
            return null;
        }
        return node.decimalValue();
    }

    private static void invalid(Key key, String metric) {
        log.warn("whoscored_stats_invalid_metric whoscoredPlayerId={} metric={}", key.playerId(), metric);
    }

    private record Key(String playerId, String teamId) {
    }
}
