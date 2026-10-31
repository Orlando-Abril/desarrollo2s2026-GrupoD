package com.example.demo.dto.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Cuerpo de {@code PUT /strategies/active}; el código se recorta antes de validarse (spec 031, FR-020). */
@Schema(name = "ChangeActiveStrategyRequest")
public record ChangeActiveStrategyRequest(
        @Schema(example = "MOMENTUM", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String strategy) {
}
