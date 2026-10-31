package com.example.demo.dto.quote;

import com.example.demo.dto.strategy.StrategyRef;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Jugadores subvaluados (la alternativa es mayor que el precio de mercado) y sobrevaluados (spec 031). Cada lista
 * se ordena por |differencePercent| descendente y, ante empate, por nombre e id ascendentes.
 */
@Schema(name = "OpportunitiesResponse", description = "Oportunidades frente a la estrategia alternativa")
public record OpportunitiesResponse(
        @Schema(description = "Estrategia activa configurada", requiredMode = Schema.RequiredMode.REQUIRED)
        StrategyRef activeStrategy,
        @Schema(description = "Estrategia comparada", requiredMode = Schema.RequiredMode.REQUIRED)
        StrategyRef alternativeStrategy,
        @Schema(description = "Estrategia que fijó el marketValue vigente; su estado excluye jugadores",
                requiredMode = Schema.RequiredMode.REQUIRED)
        StrategyRef marketStrategy,
        @Schema(example = "false", requiredMode = Schema.RequiredMode.REQUIRED) boolean pendingRecalculation,
        @ArraySchema(schema = @Schema(implementation = OpportunityItemResponse.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED))
        List<OpportunityItemResponse> undervalued,
        @ArraySchema(schema = @Schema(implementation = OpportunityItemResponse.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED))
        List<OpportunityItemResponse> overvalued) {

    public OpportunitiesResponse {
        undervalued = List.copyOf(undervalued);
        overvalued = List.copyOf(overvalued);
    }
}
