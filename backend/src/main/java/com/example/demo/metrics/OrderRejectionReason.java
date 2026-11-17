package com.example.demo.metrics;

/** Motivo de rechazo de una orden en {@code gdmarket.orders.rejected}. */
public enum OrderRejectionReason {
    INSUFFICIENT_BALANCE("insufficient_balance"),
    INSUFFICIENT_TOKENS("insufficient_tokens"),
    PRICE_OUT_OF_BAND("price_out_of_band");

    private final String tag;

    OrderRejectionReason(String tag) {
        this.tag = tag;
    }

    /** Valor de la etiqueta en las métricas. */
    public String tag() {
        return tag;
    }
}
