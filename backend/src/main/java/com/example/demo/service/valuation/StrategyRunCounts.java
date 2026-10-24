package com.example.demo.service.valuation;

/** Cantidad de cotizaciones por estado de una estrategia calculada en una corrida; sólo cuenta jugadores procesados. */
public record StrategyRunCounts(String code, int version, int calculated, int noStats, int insufficientHistory) {

    public StrategyRunCounts {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (calculated < 0 || noStats < 0 || insufficientHistory < 0) {
            throw new IllegalArgumentException("counts must be non-negative");
        }
    }
}
