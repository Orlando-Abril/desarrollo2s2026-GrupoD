package com.example.demo.model;

/** Resultado de una cotización registrada en el historial; cada registro es definitivo. */
public enum QuoteStatus {
    /** Calculada con datos suficientes; score obligatorio. */
    CALCULATED,
    /** El jugador no tenía estadísticas. */
    NO_STATS,
    /** No había historial suficiente para la estrategia. */
    INSUFFICIENT_HISTORY
}
