package com.example.demo.dto.market;

import com.example.demo.model.QuoteStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Valuación de una posición con la última cotización de otra estrategia (spec 031). Los cinco campos son nulos
 * juntos cuando el jugador no tiene cotización de esa estrategia.
 */
@Schema(name = "PositionStrategyValuation",
        description = "Posición valuada con la última cotización de la estrategia pedida, cualquiera sea su estado")
public record PositionStrategyValuationResponse(
        @Schema(example = "1.1000", nullable = true) BigDecimal quotePrice,
        @Schema(example = "CALCULATED", nullable = true) QuoteStatus quoteStatus,
        @Schema(example = "2026-10-28T03:00:00Z", nullable = true) Instant quoteCalculatedAt,
        @Schema(example = "16.5000", nullable = true) BigDecimal value,
        @Schema(example = "0.5000", nullable = true) BigDecimal profitLoss) {

    public PositionStrategyValuationResponse {
        long present = Stream.of(quotePrice, quoteStatus, quoteCalculatedAt, value, profitLoss)
                .filter(Objects::nonNull).count();
        if (present != 0 && present != 5) {
            throw new IllegalArgumentException("strategy valuation fields must be all present or all null");
        }
        quotePrice = scale(quotePrice);
        value = scale(value);
        profitLoss = scale(profitLoss);
    }

    public static PositionStrategyValuationResponse missing() {
        return new PositionStrategyValuationResponse(null, null, null, null, null);
    }

    public static PositionStrategyValuationResponse of(BigDecimal quotePrice, QuoteStatus status, Instant calculatedAt,
                                                       int quantity, BigDecimal costBasis) {
        BigDecimal value = quotePrice.multiply(BigDecimal.valueOf(quantity));
        return new PositionStrategyValuationResponse(quotePrice, status, calculatedAt, value, value.subtract(costBasis));
    }

    public boolean quoted() {
        return value != null;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_EVEN);
    }
}
