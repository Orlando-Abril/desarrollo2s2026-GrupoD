package com.example.demo.metrics;

/** Fuente externa consultada en {@code gdmarket.external.calls}. */
public enum ExternalSource {
    FOOTBALL_DATA("football_data"),
    WHOSCORED("whoscored");

    private final String tag;

    ExternalSource(String tag) {
        this.tag = tag;
    }

    /** Valor de la etiqueta en las métricas. */
    public String tag() {
        return tag;
    }
}
