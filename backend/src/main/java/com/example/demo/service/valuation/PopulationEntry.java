package com.example.demo.service.valuation;

import com.example.demo.model.PlayerStats;
import com.example.demo.model.Position;

import java.util.Objects;
import java.util.Set;

/**
 * Jugador que se ofrece a {@link MetricNormalizer} para armar la población de una corrida.
 *
 * @param stats estadísticas de temporada; {@code null} si el jugador no tiene (queda fuera de la población)
 */
public record PopulationEntry(Long playerId, Set<Position> positions, PlayerStats stats) {

    public PopulationEntry {
        Objects.requireNonNull(playerId, "playerId must not be null");
        positions = positions == null ? Set.of() : Set.copyOf(positions);
    }
}
