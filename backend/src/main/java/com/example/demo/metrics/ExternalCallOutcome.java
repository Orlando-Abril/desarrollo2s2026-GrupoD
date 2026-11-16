package com.example.demo.metrics;

/** Resultado de una consulta a una fuente externa; {@code CACHE} si se resolvió sin llamarla. */
public enum ExternalCallOutcome {
    SUCCESS("success"),
    ERROR("error"),
    CACHE("cache");

    private final String tag;

    ExternalCallOutcome(String tag) {
        this.tag = tag;
    }

    /** Valor de la etiqueta en las métricas. */
    public String tag() {
        return tag;
    }
}
