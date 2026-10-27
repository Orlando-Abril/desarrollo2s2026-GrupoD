package com.example.demo.dto.player;

import com.example.demo.model.PlayerQuote;
import com.example.demo.model.QuoteStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "CurrentQuoteResponse", description = "Cotización más reciente para la estrategia activa")
public record CurrentQuoteResponse(
        @Schema(minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal value,
        @Schema(nullable = true) BigDecimal score,
        @Schema(allowableValues = {"CALCULATED", "NO_STATS", "INSUFFICIENT_HISTORY"},
                requiredMode = Schema.RequiredMode.REQUIRED) QuoteStatus status,
        @Schema(example = "OFFICIAL", requiredMode = Schema.RequiredMode.REQUIRED) String strategy,
        @Schema(minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED) Integer version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant calculatedAt) {

    public static CurrentQuoteResponse from(PlayerQuote quote) {
        return new CurrentQuoteResponse(quote.getValue(), quote.getScore(), quote.getStatus(),
                quote.getStrategyCode(), quote.getStrategyVersion(), quote.getCalculatedAt());
    }
}
