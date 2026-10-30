package com.example.demo.service.market;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Resultado del motor de calce: los calces en orden de ejecución y lo que quedó sin ejecutar. */
public record MatchResult(List<Fill> fills, int remaining) {

    private static final int SCALE = 4;

    public MatchResult {
        fills = List.copyOf(fills);
    }

    public int filledQuantity() {
        return fills.stream().mapToInt(Fill::quantity).sum();
    }

    /** Suma de {@code precio × cantidad} de los calces, con escala 4. */
    public BigDecimal total() {
        return fills.stream()
                .map(fill -> fill.price().multiply(BigDecimal.valueOf(fill.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, RoundingMode.HALF_EVEN);
    }
}
