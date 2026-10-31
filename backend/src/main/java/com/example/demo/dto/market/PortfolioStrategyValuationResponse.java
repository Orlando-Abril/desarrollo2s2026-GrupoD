package com.example.demo.dto.market;

import com.example.demo.dto.strategy.StrategyRef;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Totales del portfolio valuado con otra estrategia (spec 031). Los totales son nulos si alguna posición no tiene
 * cotización de esa estrategia.
 */
@Schema(name = "PortfolioStrategyValuation", description = "Totales del portfolio con la estrategia pedida")
public record PortfolioStrategyValuationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) StrategyRef strategy,
        @Schema(example = "16.5000", nullable = true) BigDecimal totalValue,
        @Schema(example = "0.5000", nullable = true) BigDecimal totalProfitLoss,
        @Schema(example = "116.5000", nullable = true) BigDecimal totalEquity,
        @Schema(example = "0", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int positionsWithoutQuote) {

    public PortfolioStrategyValuationResponse {
        Objects.requireNonNull(strategy, "strategy");
        if (positionsWithoutQuote < 0) {
            throw new IllegalArgumentException("positionsWithoutQuote must be non-negative");
        }
        boolean totalsPresent = totalValue != null && totalProfitLoss != null && totalEquity != null;
        boolean totalsAbsent = totalValue == null && totalProfitLoss == null && totalEquity == null;
        if (positionsWithoutQuote > 0 ? !totalsAbsent : !totalsPresent) {
            throw new IllegalArgumentException("strategy totals must be null exactly when a position has no quote");
        }
        totalValue = scale(totalValue);
        totalProfitLoss = scale(totalProfitLoss);
        totalEquity = scale(totalEquity);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_EVEN);
    }
}
