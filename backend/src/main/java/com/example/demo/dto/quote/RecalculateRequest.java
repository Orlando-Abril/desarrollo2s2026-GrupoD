package com.example.demo.dto.quote;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RecalculateRequest", description = "Pedido de recálculo; sin cuerpo o sin strategy se calculan todas")
public record RecalculateRequest(
        @Schema(description = "Código exacto de la estrategia. Ausente o null = todas.", example = "OFFICIAL",
                nullable = true) String strategy) {
}
