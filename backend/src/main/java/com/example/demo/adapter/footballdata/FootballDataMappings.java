package com.example.demo.adapter.footballdata;

import com.example.demo.model.League;
import com.example.demo.model.Position;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class FootballDataMappings {
    private static final Map<String, League> LEAGUES = Map.of(
            "PL", League.PREMIER_LEAGUE,
            "PD", League.LA_LIGA,
            "SA", League.SERIE_A,
            "BL1", League.BUNDESLIGA,
            "FL1", League.LIGUE_1);

    private FootballDataMappings() {
    }

    public static Map<String, League> competitions() {
        return LEAGUES;
    }

    public static Optional<Position> position(String externalPosition) {
        if (externalPosition == null || externalPosition.isBlank()) {
            return Optional.empty();
        }
        return switch (externalPosition.trim().toUpperCase(Locale.ROOT)) {
            case "GOALKEEPER" -> Optional.of(Position.GOALKEEPER);
            case "DEFENCE", "DEFENDER", "CENTRE-BACK", "LEFT-BACK", "RIGHT-BACK" ->
                    Optional.of(Position.DEFENDER);
            case "MIDFIELD", "MIDFIELDER", "CENTRAL MIDFIELD", "ATTACKING MIDFIELD",
                    "DEFENSIVE MIDFIELD" -> Optional.of(Position.MIDFIELDER);
            case "OFFENCE", "FORWARD", "CENTRE-FORWARD", "LEFT WINGER", "RIGHT WINGER" ->
                    Optional.of(Position.FORWARD);
            default -> Optional.empty();
        };
    }
}
