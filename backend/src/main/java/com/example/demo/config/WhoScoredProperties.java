package com.example.demo.config;

import com.example.demo.model.League;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

/**
 * Configuración de la integración con WhoScored (ver specs/005-whoscored-player-stats/contracts/configuration.md).
 * {@code tournaments} mapea cada liga al id de torneo que usa el feed ({@code tournamentOptions}).
 */
@Validated
@ConfigurationProperties(prefix = "whoscored")
public record WhoScoredProperties(
        @NotNull URI baseUrl,
        @NotBlank String userAgent,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration requestDelay,
        @NotNull Duration cacheTtl,
        @NotNull @Valid BlockRetry blockRetry,
        @NotNull Map<League, Integer> tournaments,
        @NotNull @Valid Sync sync,
        @NotNull Client client,
        @NotNull @Valid Browser browser) {

    /** Transporte del feed: {@code BROWSER} (Chromium headless, app) o {@code HTTP} (RestClient, tests). */
    public enum Client { BROWSER, HTTP }

    /** Único reintento por consulta, sólo ante bloqueo (403 o challenge). */
    public record BlockRetry(boolean enabled, @NotNull Duration delay) {
    }

    public record Sync(boolean enabled, @NotBlank String cron, @NotBlank String zone) {
    }

    /** Página a la que navega el navegador antes de pedir el feed, y cuánto esperar a que cargue. */
    public record Browser(@NotBlank @Pattern(regexp = "/.*") String landingPath, @NotNull Duration navigationTimeout) {
    }

    @AssertTrue(message = "whoscored durations must be positive (request-delay and block-retry.delay may be zero)")
    public boolean hasValidDurations() {
        return isPositive(connectTimeout) && isPositive(readTimeout) && isPositive(cacheTtl)
                && browser != null && isPositive(browser.navigationTimeout())
                && requestDelay != null && !requestDelay.isNegative()
                && blockRetry != null && blockRetry.delay() != null && !blockRetry.delay().isNegative();
    }

    @AssertTrue(message = "whoscored.tournaments must define a positive id for every league")
    public boolean hasAllTournaments() {
        return tournaments != null && Arrays.stream(League.values())
                .allMatch(league -> tournaments.get(league) != null && tournaments.get(league) > 0);
    }

    private static boolean isPositive(Duration duration) {
        return duration != null && !duration.isNegative() && !duration.isZero();
    }
}
