package com.example.demo.service.valuation;

import com.example.demo.model.Position;
import com.example.demo.service.valuation.strategy.Metric;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Población normalizada de una corrida, inmutable. Guarda, por jugador elegible y por posición, las métricas
 * en [0,1] y, por posición, las métricas sin dato en ningún jugador. No conoce pesos: qué excluir lo decide
 * cada algoritmo. Sólo la crea {@link MetricNormalizer} (constructor package-private).
 */
public final class NormalizedPopulation {

    private final int minMinutes;
    private final Map<Long, Map<Position, Map<Metric, BigDecimal>>> normalized;
    private final Map<Position, Set<Metric>> metricsWithoutData;

    NormalizedPopulation(int minMinutes, Map<Long, Map<Position, Map<Metric, BigDecimal>>> normalized,
                         Map<Position, Set<Metric>> metricsWithoutData) {
        this.minMinutes = minMinutes;
        Map<Long, Map<Position, Map<Metric, BigDecimal>>> players = new HashMap<>();
        normalized.forEach((playerId, byPosition) -> {
            Map<Position, Map<Metric, BigDecimal>> positions = new EnumMap<>(Position.class);
            byPosition.forEach((position, values) ->
                    positions.put(position, Collections.unmodifiableMap(new EnumMap<>(values))));
            players.put(playerId, Collections.unmodifiableMap(positions));
        });
        this.normalized = Collections.unmodifiableMap(players);
        Map<Position, Set<Metric>> withoutData = new EnumMap<>(Position.class);
        metricsWithoutData.forEach((position, metrics) -> {
            Set<Metric> row = EnumSet.noneOf(Metric.class);
            row.addAll(metrics);
            withoutData.put(position, Collections.unmodifiableSet(row));
        });
        this.metricsWithoutData = Collections.unmodifiableMap(withoutData);
    }

    /** Umbral de minutos con el que se armó la población. */
    public int minMinutes() {
        return minMinutes;
    }

    /** Si el jugador es elegible: tiene estadísticas, al menos una posición y minutos &gt;= umbral. */
    public boolean contains(Long playerId) {
        return normalized.containsKey(playerId);
    }

    /** Métricas normalizadas del jugador en la posición; vacío si no es elegible o no tiene esa posición. */
    public Map<Metric, BigDecimal> normalized(Long playerId, Position position) {
        return normalized.getOrDefault(playerId, Map.of()).getOrDefault(position, Map.of());
    }

    /** Métricas sin dato para ningún jugador elegible de la posición; vacío si la posición no tiene jugadores. */
    public Set<Metric> metricsWithoutData(Position position) {
        return metricsWithoutData.getOrDefault(position, Set.of());
    }
}
