package com.example.demo.service.valuation;

import com.example.demo.model.Position;
import com.example.demo.model.QuoteStatus;
import com.example.demo.service.valuation.strategy.Metric;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Resultado de valuar a un jugador. Sólo {@link QuoteStatus#CALCULATED} tiene valor y score; en los demás
 * estados quien llama conserva la cotización anterior (feature 016).
 *
 * @param value           precio del token, escala 4; sólo en CALCULATED
 * @param score           puntaje en [0,1], escala 4; sólo en CALCULATED
 * @param excludedMetrics métricas con peso &gt; 0 sin dato en toda la población de la posición; sólo incluye
 *                        posiciones con exclusiones y está vacío fuera de CALCULATED
 */
public record QuoteResult(BigDecimal value, BigDecimal score, QuoteStatus status,
                          Map<Position, Set<Metric>> excludedMetrics) {

    public QuoteResult {
        Objects.requireNonNull(status, "status must not be null");
        excludedMetrics = copy(excludedMetrics);
        if (status == QuoteStatus.CALCULATED) {
            if (value == null || score == null) {
                throw new IllegalArgumentException("CALCULATED requiere value y score");
            }
        } else if (value != null || score != null || !excludedMetrics.isEmpty()) {
            throw new IllegalArgumentException(status + " no admite value, score ni métricas excluidas");
        }
    }

    public static QuoteResult calculated(BigDecimal value, BigDecimal score, Map<Position, Set<Metric>> excludedMetrics) {
        return new QuoteResult(value, score, QuoteStatus.CALCULATED, excludedMetrics);
    }

    public static QuoteResult noStats() {
        return new QuoteResult(null, null, QuoteStatus.NO_STATS, Map.of());
    }

    public static QuoteResult insufficientHistory() {
        return new QuoteResult(null, null, QuoteStatus.INSUFFICIENT_HISTORY, Map.of());
    }

    private static Map<Position, Set<Metric>> copy(Map<Position, Set<Metric>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<Position, Set<Metric>> copy = new EnumMap<>(Position.class);
        source.forEach((position, metrics) -> {
            Set<Metric> row = EnumSet.noneOf(Metric.class);
            row.addAll(metrics);
            copy.put(position, Collections.unmodifiableSet(row));
        });
        return Collections.unmodifiableMap(copy);
    }
}
