package com.example.demo.service.valuation.strategy;

/** Métrica de rendimiento que una estrategia puede ponderar; cada una corresponde a un campo de {@code PlayerStats}. */
public enum Metric {
    /** {@code goals}. */
    GOALS,
    /** {@code assists}. */
    ASSISTS,
    /** {@code shots}. */
    SHOTS,
    /** {@code keyPasses}. */
    KEY_PASSES,
    /** {@code dribbles}. */
    DRIBBLES,
    /** {@code tackles}. */
    TACKLES,
    /** {@code rating}. */
    RATING,
    /** {@code minutesPlayed}. */
    MINUTES,
    /** {@code yellowCards}. */
    YELLOW_CARDS,
    /** {@code redCards}. */
    RED_CARDS
}
