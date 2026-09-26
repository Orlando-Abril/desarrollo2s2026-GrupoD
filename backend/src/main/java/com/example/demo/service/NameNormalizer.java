package com.example.demo.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Normalización determinística para el matching con WhoScored (FR-005, FR-006, FR-008):
 * sin similitud aproximada ni alias. Dos nombres coinciden sólo si su forma normalizada es idéntica.
 */
public final class NameNormalizer {

    /** Letras que NFD no descompone en base + diacrítico. */
    private static final Map<String, String> TRANSLITERATIONS = Map.of(
            "ø", "o", "æ", "ae", "ß", "ss", "đ", "d", "ł", "l", "ı", "i", "œ", "oe", "þ", "th");

    /**
     * Tokens institucionales que se ignoran en nombres de equipo (lista cerrada). La segunda línea se agregó tras la
     * primera ejecución real (research V6): prefijos/sufijos de Football-Data que WhoScored no usa.
     */
    private static final Set<String> TEAM_TOKENS = Set.of(
            "fc", "cf", "afc", "sc", "ac", "as", "ssc", "sv", "vfb", "vfl", "tsg", "rc", "rcd", "ud", "cd", "sd",
            "ogc", "osc", "stade", "club", "de",
            "us", "ss", "aj", "es", "ca", "sco", "acf", "bc", "cfc", "fsv", "calcio", "balompie", "futbol");

    /** Tokens sólo numéricos (años de fundación, "04", "1."): también se ignoran en equipos. */
    private static final Pattern NUMERIC_TOKEN = Pattern.compile("\\d+");

    private NameNormalizer() {
    }

    public static String person(String name) {
        if (name == null) {
            return "";
        }
        String value = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : TRANSLITERATIONS.entrySet()) {
            value = value.replace(entry.getKey(), entry.getValue());
        }
        value = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return value.replaceAll("[^a-z0-9]+", " ").trim();
    }

    public static String team(String name) {
        return Arrays.stream(person(name).split(" "))
                .filter(token -> !token.isEmpty() && !TEAM_TOKENS.contains(token)
                        && !NUMERIC_TOKEN.matcher(token).matches())
                .collect(Collectors.joining(" "));
    }
}
