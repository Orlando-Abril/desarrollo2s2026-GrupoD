package com.example.demo.metrics;

/** Resultado de un recálculo de cotizaciones. */
public enum RecalculationOutcome {
    SUCCESS("success"),
    FAILURE("failure");

    private final String tag;

    RecalculationOutcome(String tag) {
        this.tag = tag;
    }

    /** Valor de la etiqueta en las métricas. */
    public String tag() {
        return tag;
    }
}
