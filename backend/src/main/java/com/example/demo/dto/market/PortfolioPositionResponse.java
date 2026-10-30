package com.example.demo.dto.market;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Schema(name = "PortfolioPositionResponse", description = "Posición vigente valuada a mercado")
public record PortfolioPositionResponse(
        @Schema(example = "42", requiredMode = Schema.RequiredMode.REQUIRED) Long playerId,
        @Schema(example = "Lionel Messi", requiredMode = Schema.RequiredMode.REQUIRED) String playerName,
        @Schema(example = "Inter Miami CF", requiredMode = Schema.RequiredMode.REQUIRED) String playerTeam,
        @Schema(example = "15", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED) int quantity,
        @Schema(example = "2", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int reservedQuantity,
        @Schema(example = "13", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int availableQuantity,
        @Schema(example = "1.0667", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal averagePurchasePrice,
        @Schema(example = "16.0000", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal costBasis,
        @Schema(example = "1.3000", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal marketValue,
        @Schema(example = "19.5000", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal currentValue,
        @Schema(example = "3.5000", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal profitLoss) {

    public PortfolioPositionResponse {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(playerTeam, "playerTeam");
        if (quantity <= 0 || reservedQuantity < 0 || reservedQuantity > quantity
                || availableQuantity != quantity - reservedQuantity) {
            throw new IllegalArgumentException("invalid portfolio position quantities");
        }
        averagePurchasePrice = scale(averagePurchasePrice);
        costBasis = scale(costBasis);
        marketValue = scale(marketValue);
        currentValue = scale(currentValue);
        profitLoss = scale(profitLoss);
        if (profitLoss.compareTo(currentValue.subtract(costBasis).setScale(4, RoundingMode.HALF_EVEN)) != 0) {
            throw new IllegalArgumentException("profitLoss must equal currentValue - costBasis");
        }
    }

    private static BigDecimal scale(BigDecimal value) {
        return Objects.requireNonNull(value, "monetary value").setScale(4, RoundingMode.HALF_EVEN);
    }
}
