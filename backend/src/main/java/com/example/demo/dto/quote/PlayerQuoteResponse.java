package com.example.demo.dto.quote;

import com.example.demo.model.PlayerQuote;
import com.example.demo.model.QuoteStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "PlayerQuoteResponse", description = "Cotización histórica persistida de un jugador")
public record PlayerQuoteResponse(
        @Schema(example = "1.2450", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal value,
        @Schema(example = "8.7500", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal score,
        @Schema(allowableValues = {"CALCULATED", "NO_STATS", "INSUFFICIENT_HISTORY"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        QuoteStatus status,
        @Schema(example = "OFFICIAL", requiredMode = Schema.RequiredMode.REQUIRED)
        String strategy,
        @Schema(example = "1", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer version,
        @Schema(example = "2026-10-20T18:00:00Z", type = "string", format = "date-time",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant calculatedAt) {

    public static PlayerQuoteResponse from(PlayerQuote quote) {
        return new PlayerQuoteResponse(quote.getValue(), quote.getScore(), quote.getStatus(),
                quote.getStrategyCode(), quote.getStrategyVersion(), quote.getCalculatedAt());
    }
}
