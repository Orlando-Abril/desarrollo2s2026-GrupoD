package com.example.demo.adapter.whoscored.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * Respuesta cruda de {@code /statisticsfeed/1/getplayerstatistics}. Los valores numéricos quedan como
 * {@link JsonNode} para distinguir ausente, {@code null}, entero, decimal y no numérico al mapearlos.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WhoScoredFeedResponse(List<Row> playerTableStats) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(
            JsonNode playerId,
            String name,
            JsonNode teamId,
            String teamName,
            JsonNode tournamentId,
            JsonNode minsPlayed,
            JsonNode goal,
            JsonNode assistTotal,
            JsonNode yellowCard,
            JsonNode redCard,
            JsonNode rating,
            JsonNode shotsTotal,
            JsonNode keyPassesTotal,
            JsonNode tackleWonTotal) {
    }
}
