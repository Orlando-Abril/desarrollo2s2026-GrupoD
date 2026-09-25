package com.example.demo.dto.player;

import com.example.demo.model.League;
import com.example.demo.model.Position;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;

import java.math.BigDecimal;
import java.util.Set;

@Schema(name = "PlayerResponse", description = "Jugador persistido en el catálogo local")
public record PlayerResponse(
        @Schema(example = "42", requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(example = "3180", requiredMode = Schema.RequiredMode.REQUIRED) String externalId,
        @Schema(example = "Bukayo Saka", requiredMode = Schema.RequiredMode.REQUIRED) String fullName,
        @Schema(example = "Arsenal FC", requiredMode = Schema.RequiredMode.REQUIRED) String team,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) League league,
        @ArraySchema(uniqueItems = true, schema = @Schema(implementation = Position.class)) Set<Position> positions,
        @Schema(example = "England", nullable = true) String nationality,
        @Schema(example = "25", nullable = true, minimum = "0") Integer age,
        @Schema(example = "1.00", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal marketValue) {
    public PlayerResponse {
        positions = positions == null ? Set.of() : Set.copyOf(positions);
    }
}
