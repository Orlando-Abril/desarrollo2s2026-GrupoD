package com.example.demo.dto.market;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

@Schema(name = "TransactionResponse", description = "Calce ejecutado desde la perspectiva del usuario")
public record TransactionResponse(
        @Schema(example = "901", requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TransactionType type,
        @Schema(example = "42", requiredMode = Schema.RequiredMode.REQUIRED) Long playerId,
        @Schema(example = "Lionel Messi", requiredMode = Schema.RequiredMode.REQUIRED) String playerName,
        @Schema(example = "5", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED) int quantity,
        @Schema(example = "1.2500", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal price,
        @Schema(example = "6.2500", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal total,
        @Schema(type = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant executedAt) {

    public TransactionResponse {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(executedAt, "executedAt");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        price = Objects.requireNonNull(price, "price").setScale(4, RoundingMode.HALF_EVEN);
        total = Objects.requireNonNull(total, "total").setScale(4, RoundingMode.HALF_EVEN);
    }
}
