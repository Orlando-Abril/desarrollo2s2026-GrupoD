package com.example.demo.dto.strategy;

import com.example.demo.service.valuation.strategy.ValuationStrategyDefinition;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/** Referencia a una estrategia del catálogo; {@code label} es código y versión (spec 031, research R12). */
@Schema(name = "StrategyRef", description = "Estrategia de valuación")
public record StrategyRef(
        @Schema(example = "MOMENTUM", requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) int version,
        @Schema(example = "MOMENTUM v1", requiredMode = Schema.RequiredMode.REQUIRED) String label) {

    public StrategyRef {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(label, "label");
    }

    public static StrategyRef of(ValuationStrategyDefinition definition) {
        return new StrategyRef(definition.code(), definition.version(),
                definition.code() + " v" + definition.version());
    }
}
