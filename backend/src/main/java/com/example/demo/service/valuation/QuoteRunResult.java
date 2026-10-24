package com.example.demo.service.valuation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Resumen de una corrida de recálculo de cotizaciones (feature 016).
 *
 * @param requestedStrategy código pedido; null si se pidieron todas
 * @param marketValueUpdated true si la estrategia activa se calculó y no fallaron todos los jugadores considerados
 */
public record QuoteRunResult(UUID runId, Instant calculatedAt, String requestedStrategy,
                             List<StrategyRunCounts> calculated, List<SkippedStrategy> skipped,
                             int playersProcessed, int playersFailed, String activeStrategy,
                             boolean marketValueUpdated) {

    public QuoteRunResult {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(calculatedAt, "calculatedAt must not be null");
        Objects.requireNonNull(activeStrategy, "activeStrategy must not be null");
        calculated = calculated == null ? List.of() : List.copyOf(calculated);
        skipped = skipped == null ? List.of() : List.copyOf(skipped);
        if (playersProcessed < 0 || playersFailed < 0) {
            throw new IllegalArgumentException("player counts must be non-negative");
        }
    }
}
