package com.example.demo.dto.player;

import com.example.demo.model.PlayerStats;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "PlayerStatsResponse", description = "Estadísticas locales de la temporada del jugador")
public record PlayerStatsResponse(
        @Schema(nullable = true) Integer minutesPlayed,
        @Schema(nullable = true) Integer goals,
        @Schema(nullable = true) Integer assists,
        @Schema(nullable = true) Integer shots,
        @Schema(nullable = true) Integer keyPasses,
        @Schema(nullable = true) Integer tackles,
        @Schema(nullable = true) Integer dribbles,
        @Schema(nullable = true) Integer yellowCards,
        @Schema(nullable = true) Integer redCards,
        @Schema(nullable = true) BigDecimal rating,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant fetchedAt) {

    public static PlayerStatsResponse from(PlayerStats stats) {
        return new PlayerStatsResponse(stats.getMinutesPlayed(), stats.getGoals(), stats.getAssists(),
                stats.getShots(), stats.getKeyPasses(), stats.getTackles(), stats.getDribbles(),
                stats.getYellowCards(), stats.getRedCards(), stats.getRating(), stats.getFetchedAt());
    }
}
