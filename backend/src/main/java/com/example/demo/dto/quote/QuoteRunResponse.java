package com.example.demo.dto.quote;

import com.example.demo.service.valuation.QuoteRunResult;
import com.example.demo.service.valuation.SkippedStrategy;
import com.example.demo.service.valuation.StrategyRunCounts;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "QuoteRunResponse", description = "Resumen de una corrida de recálculo de cotizaciones")
public record QuoteRunResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID runId,
        @Schema(description = "Instante de cálculo común a todas las cotizaciones de la corrida",
                requiredMode = Schema.RequiredMode.REQUIRED) Instant calculatedAt,
        @Schema(description = "Estrategia pedida; null si se pidieron todas", nullable = true) String requestedStrategy,
        @Schema(description = "Estrategias calculadas con la cantidad de cotizaciones por estado",
                requiredMode = Schema.RequiredMode.REQUIRED) List<StrategyRunCountsResponse> strategies,
        @Schema(description = "Estrategias del catálogo que no se calcularon",
                requiredMode = Schema.RequiredMode.REQUIRED) List<SkippedStrategyResponse> skippedStrategies,
        @Schema(description = "Jugadores con sus cotizaciones guardadas", minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED) int playersProcessed,
        @Schema(description = "Jugadores que fallaron y quedaron sin cotizaciones de la corrida", minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED) int playersFailed,
        @Schema(description = "Estrategia que define el precio de mercado", example = "OFFICIAL",
                requiredMode = Schema.RequiredMode.REQUIRED) String activeStrategy,
        @Schema(description = "Si el precio de mercado quedó con la cotización de la estrategia activa",
                requiredMode = Schema.RequiredMode.REQUIRED) boolean marketValueUpdated) {

    public static QuoteRunResponse from(QuoteRunResult result) {
        return new QuoteRunResponse(result.runId(), result.calculatedAt(), result.requestedStrategy(),
                result.calculated().stream().map(StrategyRunCountsResponse::from).toList(),
                result.skipped().stream().map(SkippedStrategyResponse::from).toList(),
                result.playersProcessed(), result.playersFailed(), result.activeStrategy(),
                result.marketValueUpdated());
    }

    @Schema(name = "StrategyRunCounts", description = "Cotizaciones de una estrategia por estado")
    public record StrategyRunCountsResponse(
            @Schema(example = "OFFICIAL", requiredMode = Schema.RequiredMode.REQUIRED) String code,
            @Schema(example = "1", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED) int version,
            @Schema(minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int calculated,
            @Schema(minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int noStats,
            @Schema(minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int insufficientHistory) {

        static StrategyRunCountsResponse from(StrategyRunCounts counts) {
            return new StrategyRunCountsResponse(counts.code(), counts.version(), counts.calculated(),
                    counts.noStats(), counts.insufficientHistory());
        }
    }

    @Schema(name = "SkippedStrategy", description = "Estrategia salteada y su motivo")
    public record SkippedStrategyResponse(
            @Schema(example = "MOMENTUM", requiredMode = Schema.RequiredMode.REQUIRED) String code,
            @Schema(example = "1", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED) int version,
            @Schema(allowableValues = {SkippedStrategy.NO_ALGORITHM},
                    requiredMode = Schema.RequiredMode.REQUIRED) String reason) {

        static SkippedStrategyResponse from(SkippedStrategy skipped) {
            return new SkippedStrategyResponse(skipped.code(), skipped.version(), skipped.reason());
        }
    }
}
