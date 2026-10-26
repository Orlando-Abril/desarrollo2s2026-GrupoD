package com.example.demo.service.valuation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Estadísticas atribuibles al intervalo entre dos snapshots consecutivos.
 *
 * @param currentRunId corrida que capturó el snapshot que cierra la ventana; MOMENTUM (feature 019) la usa
 *                     para saber si hubo datos nuevos desde su última cotización
 */
public record StatsWindow(
        Long previousSnapshotId,
        Long currentSnapshotId,
        UUID currentRunId,
        Instant fromCapturedAt,
        Instant toCapturedAt,
        boolean seasonReset,
        Integer minutesPlayed,
        Integer goals,
        Integer assists,
        Integer shots,
        Integer keyPasses,
        Integer tackles,
        Integer dribbles,
        Integer yellowCards,
        Integer redCards,
        BigDecimal rating) {

    private static final BigDecimal TEN = BigDecimal.TEN;

    public StatsWindow {
        Objects.requireNonNull(previousSnapshotId, "previousSnapshotId must not be null");
        Objects.requireNonNull(currentSnapshotId, "currentSnapshotId must not be null");
        Objects.requireNonNull(currentRunId, "currentRunId must not be null");
        Objects.requireNonNull(fromCapturedAt, "fromCapturedAt must not be null");
        Objects.requireNonNull(toCapturedAt, "toCapturedAt must not be null");
        if (rating != null && (rating.signum() < 0 || rating.compareTo(TEN) > 0)) {
            throw new IllegalArgumentException("rating must be between 0 and 10");
        }
    }
}
