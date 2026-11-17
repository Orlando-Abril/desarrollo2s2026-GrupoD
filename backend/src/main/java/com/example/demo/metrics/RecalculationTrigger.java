package com.example.demo.metrics;

/** Qué disparó un recálculo de cotizaciones: un pedido manual o el scheduler. */
public enum RecalculationTrigger {
    MANUAL("manual"),
    SCHEDULED("scheduled");

    private final String tag;

    RecalculationTrigger(String tag) {
        this.tag = tag;
    }

    /** Valor de la etiqueta en las métricas. */
    public String tag() {
        return tag;
    }
}
