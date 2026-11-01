package com.example.demo.dto.quote;

import com.example.demo.model.League;
import com.example.demo.model.Position;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Set;

@Schema(name = "RankingEntryResponse", description = "Posición y cotización vigente de un jugador")
public record RankingEntryResponse(
        @Schema(minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED) long rank,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RankingPlayerResponse player,
        @Schema(minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal value,
        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal percentageChange,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String strategy,
        @Schema(minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED) int version) {
    public RankingEntryResponse {
        if (rank < 1) throw new IllegalArgumentException("rank debe ser mayor a cero");
        if (player == null || value == null || value.signum() < 0 || strategy == null || strategy.isBlank()
                || version < 1) {
            throw new IllegalArgumentException("Entrada de ranking inválida");
        }
    }

    @Schema(name = "RankingPlayerResponse", description = "Fotografía del jugador incluida en el ranking")
    public record RankingPlayerResponse(
            @Schema(minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED) long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String team,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) League league,
            @ArraySchema(uniqueItems = true,
                    arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                    schema = @Schema(implementation = Position.class)) Set<Position> positions) {
        public RankingPlayerResponse {
            if (id < 1 || name == null || name.isBlank() || team == null || team.isBlank() || league == null
                    || positions == null) {
                throw new IllegalArgumentException("Jugador de ranking inválido");
            }
            // Hay jugadores sin posición informada por Football-Data: se listan con el conjunto vacío.
            positions = Set.copyOf(positions);
        }
    }
}
