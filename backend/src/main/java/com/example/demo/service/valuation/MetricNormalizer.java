package com.example.demo.service.valuation;

import com.example.demo.model.PlayerStats;
import com.example.demo.model.Position;
import com.example.demo.service.valuation.strategy.Metric;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Normaliza una vez por corrida las estadísticas de una población (contrato en
 * {@code specs/015-estrategia-oficial/contracts/valuation-strategies.md} §1 y §2):
 * <ul>
 *   <li>sólo entran los elegibles: con estadísticas, al menos una posición y minutos &gt;= umbral;</li>
 *   <li>goles, asistencias, tiros, pases clave, gambetas y tackles se pasan a valores por 90' y se normalizan
 *       min-max dentro de cada posición; un {@code null} cuenta 0 y si {@code max = min} valen 0.5;</li>
 *   <li>el rating se normaliza como {@code rating / 10}, acotado a [0,1];</li>
 *   <li>por posición se registran las métricas que ningún elegible tiene informadas.</li>
 * </ul>
 * No conoce pesos ni estrategias: lo reusan OFFICIAL y MOMENTUM.
 */
public final class MetricNormalizer {

    /** Métricas de conteo, que se pasan a valores por 90' y se normalizan min-max. */
    private static final List<Metric> COUNT_METRICS = List.of(Metric.GOALS, Metric.ASSISTS, Metric.SHOTS,
            Metric.KEY_PASSES, Metric.DRIBBLES, Metric.TACKLES);

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal MINUTES_PER_MATCH = BigDecimal.valueOf(90);
    private static final BigDecimal MAX_RATING = BigDecimal.TEN;
    private static final BigDecimal TIED = new BigDecimal("0.5");

    private final int minMinutes;

    public MetricNormalizer(int minMinutes) {
        if (minMinutes <= 0) {
            throw new IllegalArgumentException("El umbral de minutos debe ser > 0: " + minMinutes);
        }
        this.minMinutes = minMinutes;
    }

    /** Regla de elegibilidad, compartida con los algoritmos para evaluar al jugador que se valúa. */
    public static boolean isEligible(Set<Position> positions, PlayerStats stats, int minMinutes) {
        return stats != null && !positions.isEmpty()
                && stats.getMinutesPlayed() != null && stats.getMinutesPlayed() >= minMinutes;
    }

    public NormalizedPopulation normalize(Collection<PopulationEntry> entries) {
        Set<Long> seen = new HashSet<>();
        Map<Long, Map<Metric, BigDecimal>> raw = new HashMap<>();
        Map<Position, List<PopulationEntry>> byPosition = new EnumMap<>(Position.class);
        for (PopulationEntry entry : entries) {
            if (!seen.add(entry.playerId())) {
                throw new IllegalArgumentException("Jugador repetido en la población: " + entry.playerId());
            }
            if (isEligible(entry.positions(), entry.stats(), minMinutes)) {
                raw.put(entry.playerId(), rawValues(entry.stats()));
                entry.positions().forEach(position ->
                        byPosition.computeIfAbsent(position, key -> new ArrayList<>()).add(entry));
            }
        }

        Map<Long, Map<Position, Map<Metric, BigDecimal>>> normalized = new HashMap<>();
        Map<Position, Set<Metric>> withoutData = new EnumMap<>(Position.class);
        byPosition.forEach((position, members) -> {
            withoutData.put(position, metricsWithoutData(members));
            for (PopulationEntry member : members) {
                normalized.computeIfAbsent(member.playerId(), key -> new EnumMap<>(Position.class))
                        .put(position, new EnumMap<>(Metric.class));
            }
            for (Metric metric : COUNT_METRICS) {
                normalizeMinMax(position, metric, members, raw, normalized);
            }
            for (PopulationEntry member : members) {
                BigDecimal rating = raw.get(member.playerId()).get(Metric.RATING);
                normalized.get(member.playerId()).get(position).put(Metric.RATING, rating);
            }
        });
        return new NormalizedPopulation(minMinutes, normalized, withoutData);
    }

    private static void normalizeMinMax(Position position, Metric metric, List<PopulationEntry> members,
                                        Map<Long, Map<Metric, BigDecimal>> raw,
                                        Map<Long, Map<Position, Map<Metric, BigDecimal>>> normalized) {
        BigDecimal min = null;
        BigDecimal max = null;
        for (PopulationEntry member : members) {
            BigDecimal value = raw.get(member.playerId()).get(metric);
            min = min == null ? value : min.min(value);
            max = max == null ? value : max.max(value);
        }
        BigDecimal range = max.subtract(min);
        for (PopulationEntry member : members) {
            BigDecimal value = raw.get(member.playerId()).get(metric);
            BigDecimal result = range.signum() == 0 ? TIED : value.subtract(min).divide(range, MC);
            normalized.get(member.playerId()).get(position).put(metric, result);
        }
    }

    private static Map<Metric, BigDecimal> rawValues(PlayerStats stats) {
        Map<Metric, BigDecimal> values = new EnumMap<>(Metric.class);
        BigDecimal minutes = BigDecimal.valueOf(stats.getMinutesPlayed());
        for (Metric metric : COUNT_METRICS) {
            Integer count = count(stats, metric);
            BigDecimal total = BigDecimal.valueOf(count == null ? 0 : count);
            values.put(metric, total.multiply(MINUTES_PER_MATCH).divide(minutes, MC));
        }
        BigDecimal rating = stats.getRating() == null ? BigDecimal.ZERO : stats.getRating().divide(MAX_RATING, MC);
        values.put(Metric.RATING, rating.max(BigDecimal.ZERO).min(BigDecimal.ONE));
        return values;
    }

    private static Set<Metric> metricsWithoutData(List<PopulationEntry> members) {
        Set<Metric> withoutData = EnumSet.noneOf(Metric.class);
        for (Metric metric : COUNT_METRICS) {
            if (members.stream().allMatch(member -> count(member.stats(), metric) == null)) {
                withoutData.add(metric);
            }
        }
        if (members.stream().allMatch(member -> member.stats().getRating() == null)) {
            withoutData.add(Metric.RATING);
        }
        return withoutData;
    }

    private static Integer count(PlayerStats stats, Metric metric) {
        return switch (metric) {
            case GOALS -> stats.getGoals();
            case ASSISTS -> stats.getAssists();
            case SHOTS -> stats.getShots();
            case KEY_PASSES -> stats.getKeyPasses();
            case DRIBBLES -> stats.getDribbles();
            case TACKLES -> stats.getTackles();
            default -> throw new IllegalArgumentException("No es una métrica de conteo: " + metric);
        };
    }
}
