package com.example.demo.service;

/**
 * Resumen de una actualización de estadísticas, sólo para el log (no se persiste).
 * {@code processed = updated + unmatched + failed}; {@code fromCache} es un subconjunto de {@code updated}.
 */
public record StatsUpdateResult(int processed, int updated, int fromCache, int unmatched, int failed) {
}
