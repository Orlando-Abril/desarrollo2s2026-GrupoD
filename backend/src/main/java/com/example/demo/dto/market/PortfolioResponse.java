package com.example.demo.dto.market;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

@Schema(name = "PortfolioResponse", description = "Portfolio, saldo y patrimonio de una cuenta")
public record PortfolioResponse(
        @Schema(example = "7", requiredMode = Schema.RequiredMode.REQUIRED) Long userId,
        @Schema(example = "900.0000", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal balance,
        @Schema(example = "12.0000", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal reservedCredits,
        @Schema(example = "888.0000", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal availableBalance,
        @ArraySchema(schema = @Schema(implementation = PortfolioPositionResponse.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED)) List<PortfolioPositionResponse> positions,
        @Schema(example = "16.0000", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal totalCost,
        @Schema(example = "19.5000", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal totalCurrentValue,
        @Schema(example = "3.5000", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal totalProfitLoss,
        @Schema(example = "919.5000", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal totalEquity) {

    public PortfolioResponse {
        Objects.requireNonNull(userId, "userId");
        balance = scale(balance);
        reservedCredits = scale(reservedCredits);
        availableBalance = scale(availableBalance);
        positions = List.copyOf(Objects.requireNonNull(positions, "positions"));
        totalCost = scale(totalCost);
        totalCurrentValue = scale(totalCurrentValue);
        totalProfitLoss = scale(totalProfitLoss);
        totalEquity = scale(totalEquity);
        BigDecimal expectedCost = scale(positions.stream().map(PortfolioPositionResponse::costBasis)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal expectedCurrent = scale(positions.stream().map(PortfolioPositionResponse::currentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal expectedProfit = scale(positions.stream().map(PortfolioPositionResponse::profitLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        if (availableBalance.compareTo(balance.subtract(reservedCredits).setScale(4, RoundingMode.HALF_EVEN)) != 0
                || totalEquity.compareTo(balance.add(totalCurrentValue).setScale(4, RoundingMode.HALF_EVEN)) != 0
                || totalCost.compareTo(expectedCost) != 0 || totalCurrentValue.compareTo(expectedCurrent) != 0
                || totalProfitLoss.compareTo(expectedProfit) != 0) {
            throw new IllegalArgumentException("invalid portfolio totals");
        }
    }

    private static BigDecimal scale(BigDecimal value) {
        return Objects.requireNonNull(value, "monetary value").setScale(4, RoundingMode.HALF_EVEN);
    }
}
