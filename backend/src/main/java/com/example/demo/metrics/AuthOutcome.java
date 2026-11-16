package com.example.demo.metrics;

/** Resultado de un registro o de un login. */
public enum AuthOutcome {
    SUCCESS("success"),
    FAILURE("failure");

    private final String tag;

    AuthOutcome(String tag) {
        this.tag = tag;
    }

    /** Valor de la etiqueta en las métricas. */
    public String tag() {
        return tag;
    }
}
