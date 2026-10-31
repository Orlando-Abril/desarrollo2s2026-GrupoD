package com.example.demo.dto.quote;

import com.example.demo.model.League;
import com.example.demo.model.Position;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Jugador cuyo precio de mercado difiere de su última cotización alternativa (spec 031). */
@Schema(name = "OpportunityItem", description = "difference = alternativeQuote − marketValue; "
        + "differencePercent = difference ÷ marketValue × 100")
public record OpportunityItemResponse(
        @Schema(example = "7", requiredMode = Schema.RequiredMode.REQUIRED) Long playerId,
        @Schema(example = "Pedri", requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(example = "FC Barcelona", requiredMode = Schema.RequiredMode.REQUIRED) String team,
        @Schema(example = "LA_LIGA", requiredMode = Schema.RequiredMode.REQUIRED) League league,
        @ArraySchema(schema = @Schema(implementation = Position.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED)) List<Position> positions,
        @Schema(example = "1.0000", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal marketValue,
        @Schema(example = "1.2000", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal alternativeQuote,
        @Schema(example = "2026-10-28T03:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant alternativeQuoteCalculatedAt,
        @Schema(example = "0.2000", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal difference,
        @Schema(example = "20.00", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal differencePercent) {

    public OpportunityItemResponse {
        positions = List.copyOf(positions);
    }
}
