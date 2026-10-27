package com.example.demo.dto.player;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PlayerDetailResponse", description = "Jugador con estadísticas y cotización local opcionales")
public record PlayerDetailResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PlayerResponse player,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) PlayerStatsResponse stats,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) CurrentQuoteResponse quote) {
}
