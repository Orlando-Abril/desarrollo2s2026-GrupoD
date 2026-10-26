package com.example.demo.service.valuation;

import com.example.demo.model.PlayerQuote;
import com.example.demo.model.PlayerStats;
import com.example.demo.model.Position;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Entrada de {@link ValuationAlgorithm#calculate} para un jugador. OFFICIAL usa la población normalizada y la
 * última cotización previa; MOMENTUM (feature 019) usa las ventanas de 014, las estadísticas de temporada como
 * promedio contra el que compara cada ventana y la última cotización previa.
 *
 * @param stats          estadísticas de temporada; {@code null} si el jugador no tiene
 * @param population     población normalizada una vez por corrida; obligatoria para OFFICIAL
 * @param previousQuotes sólo cotizaciones de la estrategia que se calcula, de la más nueva a la más vieja por
 *                       {@code calculatedAt}; el código de estrategia lo valida el algoritmo
 * @param windows        ventanas entre snapshots de estadísticas (feature 014)
 */
public record ValuationInput(Long playerId, Set<Position> positions, PlayerStats stats,
                             NormalizedPopulation population, List<PlayerQuote> previousQuotes,
                             List<StatsWindow> windows) {

    public ValuationInput {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(positions, "positions must not be null");
        positions = Set.copyOf(positions);
        previousQuotes = previousQuotes == null ? List.of() : List.copyOf(previousQuotes);
        windows = windows == null ? List.of() : List.copyOf(windows);
        requireNewestFirst(playerId, previousQuotes);
    }

    private static void requireNewestFirst(Long playerId, List<PlayerQuote> quotes) {
        for (int i = 0; i < quotes.size(); i++) {
            if (quotes.get(i).getCalculatedAt() == null) {
                throw new IllegalArgumentException("La cotización previa " + i + " del jugador " + playerId
                        + " no tiene calculatedAt");
            }
            if (i > 0 && quotes.get(i).getCalculatedAt().isAfter(quotes.get(i - 1).getCalculatedAt())) {
                throw new IllegalArgumentException("Las cotizaciones previas del jugador " + playerId
                        + " deben venir ordenadas de la más nueva a la más vieja");
            }
        }
    }
}
