package com.example.demo.adapter.whoscored.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Una fila {@code (playerId, teamId)} de una liga, combinada de las 4 respuestas del feed.
 * {@code teamNames} son los nombres del equipo vistos en esas respuestas (p. ej. "Man Utd" y
 * "Manchester United"). Es también el valor guardado en la caché {@code whoscored-player-stats}.
 */
public record WhoScoredPlayerStats(
        String whoscoredPlayerId,
        String name,
        String whoscoredTeamId,
        List<String> teamNames,
        Integer minutesPlayed,
        Integer goals,
        Integer assists,
        Integer shots,
        Integer keyPasses,
        Integer tackles,
        Integer yellowCards,
        Integer redCards,
        BigDecimal rating,
        Instant fetchedAt) {

    public boolean hasAnyMetric() {
        return rating != null || Stream.of(minutesPlayed, goals, assists, shots, keyPasses, tackles,
                yellowCards, redCards).anyMatch(Objects::nonNull);
    }
}
