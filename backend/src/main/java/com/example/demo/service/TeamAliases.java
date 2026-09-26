package com.example.demo.service;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Equivalencias fijas entre el nombre de un equipo en el catálogo (Football-Data) y su nombre en WhoScored, para
 * los equipos cuyos nombres no se parecen lo suficiente para las reglas de {@link NameNormalizer} (research V6).
 * <p>
 * Se comparan nombres normalizados ({@link NameNormalizer#team}), así que tildes, mayúsculas y sufijos no importan.
 * Para agregar un equipo: sumar una línea acá y un caso en PlayerStatsServiceTest.
 */
final class TeamAliases {

    /** Nombre en el catálogo (Football-Data) → nombre en WhoScored. */
    private static final Map<String, String> CATALOG_TO_WHOSCORED = Map.of(
            "Borussia Mönchengladbach", "Borussia M.Gladbach",
            "Olympique Lyonnais", "Lyon",
            "Stade Rennais FC 1901", "Rennes");

    private static final Map<String, String> NORMALIZED = CATALOG_TO_WHOSCORED.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(
                    entry -> NameNormalizer.team(entry.getKey()),
                    entry -> NameNormalizer.team(entry.getValue())));

    private TeamAliases() {
    }

    /** Nombre normalizado en WhoScored equivalente a un equipo del catálogo (ya normalizado), si hay uno. */
    static Optional<String> whoScoredNameFor(String normalizedCatalogTeam) {
        return Optional.ofNullable(NORMALIZED.get(normalizedCatalogTeam));
    }
}
