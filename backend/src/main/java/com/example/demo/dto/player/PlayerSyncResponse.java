package com.example.demo.dto.player;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PlayerSyncResponse", description = "Resumen de una carga del catálogo desde Football-Data.org")
public record PlayerSyncResponse(
        @Schema(allowableValues = {"COMPLETED", "PARTIAL_FAILURE", "FAILED"},
                requiredMode = Schema.RequiredMode.REQUIRED) String status,
        @Schema(description = "Jugadores creados o actualizados", minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED) int processed,
        @Schema(description = "Ligas cuya consulta externa falló", minimum = "0", maximum = "5",
                requiredMode = Schema.RequiredMode.REQUIRED) int failedLeagues,
        @Schema(description = "Jugadores que no se pudieron guardar", minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED) int failedPlayers) {
}
