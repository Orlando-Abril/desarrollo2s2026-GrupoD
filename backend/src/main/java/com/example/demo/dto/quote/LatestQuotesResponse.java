package com.example.demo.dto.quote;

import com.example.demo.dto.strategy.StrategyRef;
import com.example.demo.model.QuoteStatus;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Última cotización de una estrategia para un conjunto de jugadores, en el orden pedido (spec 031). */
@Schema(name = "LatestQuotesResponse", description = "Una entrada por id distinto existente, en el orden pedido")
public record LatestQuotesResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) StrategyRef strategy,
        @ArraySchema(schema = @Schema(implementation = Item.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED)) List<Item> items) {

    public LatestQuotesResponse {
        Objects.requireNonNull(strategy, "strategy");
        items = List.copyOf(items);
    }

    @Schema(name = "LatestQuoteItem")
    public record Item(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) Long playerId,
            @Schema(description = "Nulo si el jugador no tiene cotización de la estrategia", nullable = true,
                    requiredMode = Schema.RequiredMode.REQUIRED) Quote quote) {
    }

    @Schema(name = "LatestQuote")
    public record Quote(
            @Schema(example = "1.1500", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal value,
            @Schema(example = "CALCULATED", requiredMode = Schema.RequiredMode.REQUIRED) QuoteStatus status,
            @Schema(example = "2026-10-28T03:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
            Instant calculatedAt,
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) int strategyVersion) {
    }
}
