package com.example.demo.service.valuation.strategy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Parámetros de forma reciente de MOMENTUM (feature 019).
 *
 * @param window          cantidad de ventanas de estadísticas (feature 014) que mira la forma reciente
 * @param minHistory      snapshots mínimos; con menos, el algoritmo registra {@code INSUFFICIENT_HISTORY}
 * @param recencyWeights  peso de cada ventana, de la más reciente a la más vieja; el algoritmo los normaliza
 *                        sobre las ventanas presentes
 * @param possibleMinutes minutos posibles por ventana, para medir la disponibilidad
 * @param ratingBand      diferencia de rating contra el de temporada que lleva la métrica a 0 o a 1
 */
public record MomentumParameters(int window, int minHistory, List<BigDecimal> recencyWeights, int possibleMinutes,
                                 BigDecimal ratingBand) {

    public MomentumParameters {
        boolean valid = window >= 1
                && minHistory >= 2 && minHistory <= window + 1
                && validRecencyWeights(recencyWeights, window)
                && possibleMinutes > 0
                && ratingBand != null && ratingBand.signum() > 0;
        if (!valid) {
            throw new IllegalArgumentException("Parámetros de MOMENTUM inválidos: window=" + window
                    + ", minHistory=" + minHistory + ", recencyWeights=" + recencyWeights
                    + ", possibleMinutes=" + possibleMinutes + ", ratingBand=" + ratingBand
                    + " (se exige window >= 1, 2 <= minHistory <= window + 1, window pesos de recencia positivos y"
                    + " estrictamente decrecientes, possibleMinutes > 0 y ratingBand > 0)");
        }
        recencyWeights = List.copyOf(recencyWeights);
    }

    private static boolean validRecencyWeights(List<BigDecimal> weights, int window) {
        if (weights == null || weights.size() != window || weights.stream().anyMatch(Objects::isNull)) {
            return false;
        }
        for (int i = 0; i < weights.size(); i++) {
            if (weights.get(i).signum() <= 0 || (i > 0 && weights.get(i).compareTo(weights.get(i - 1)) >= 0)) {
                return false;
            }
        }
        return true;
    }
}
