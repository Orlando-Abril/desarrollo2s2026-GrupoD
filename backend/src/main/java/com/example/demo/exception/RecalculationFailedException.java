package com.example.demo.exception;

import java.util.UUID;

/** La corrida de recálculo no pudo completarse fuera del procesamiento de un jugador (snapshot, carga o resumen). */
public class RecalculationFailedException extends RuntimeException {

    private final UUID runId;

    public RecalculationFailedException(UUID runId, Throwable cause) {
        super("No se pudo completar el recálculo", cause);
        this.runId = runId;
    }

    public UUID runId() {
        return runId;
    }
}
