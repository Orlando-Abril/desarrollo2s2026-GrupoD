package com.example.demo.service.valuation.momentum;

import com.example.demo.model.PlayerQuote;
import com.example.demo.model.PlayerStats;
import com.example.demo.model.Position;
import com.example.demo.model.QuoteStatus;
import com.example.demo.service.valuation.StatsWindow;
import com.example.demo.service.valuation.ValuationInput;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Datos de prueba del algoritmo MOMENTUM ({@code specs/019-estrategia-momentum/contracts/momentum-algorithm.md}).
 * Las ventanas se declaran de la más vieja a la más reciente; {@link #windows} les asigna ids, corridas e
 * instantes crecientes. Las cotizaciones previas por defecto son de otra corrida y anteriores a todas las
 * ventanas, así que siempre hay datos nuevos (FR-028) salvo que el test diga lo contrario.
 */
public final class MomentumFixtures {

    public static final long PLAYER_ID = 7;
    public static final Instant FIRST_WINDOW_END = Instant.parse("2026-09-01T12:00:00Z");
    public static final Instant PREVIOUS_AT = Instant.parse("2026-08-01T00:00:00Z");
    public static final UUID PREVIOUS_RUN = new UUID(1, 1);

    private static final Duration WEEK = Duration.ofDays(7);

    private MomentumFixtures() {
    }

    /** Ventana sin ubicar en el tiempo; {@link #windows} le asigna ids, corrida e instantes. */
    public record Spec(Integer minutes, Integer goals, Integer assists, Integer shots, Integer keyPasses,
                       Integer dribbles, Integer tackles, String rating, Integer yellowCards, Integer redCards,
                       boolean seasonReset, UUID runId) {

        public Spec withSeasonReset() {
            return new Spec(minutes, goals, assists, shots, keyPasses, dribbles, tackles, rating, yellowCards,
                    redCards, true, runId);
        }

        public Spec withRunId(UUID id) {
            return new Spec(minutes, goals, assists, shots, keyPasses, dribbles, tackles, rating, yellowCards,
                    redCards, seasonReset, id);
        }

        public Spec withCards(Integer yellow, Integer red) {
            return new Spec(minutes, goals, assists, shots, keyPasses, dribbles, tackles, rating, yellow, red,
                    seasonReset, runId);
        }

        public Spec withMinutes(Integer value) {
            return new Spec(value, goals, assists, shots, keyPasses, dribbles, tackles, rating, yellowCards,
                    redCards, seasonReset, runId);
        }
    }

    @SuppressWarnings("java:S107") // una columna por campo de la ventana, como en las tablas del contrato
    public static Spec window(Integer minutes, Integer goals, Integer assists, Integer shots, Integer keyPasses,
                              Integer dribbles, Integer tackles, String rating, Integer yellowCards,
                              Integer redCards) {
        return new Spec(minutes, goals, assists, shots, keyPasses, dribbles, tackles, rating, yellowCards, redCards,
                false, null);
    }

    /** Ventana sin minutos ni estadísticas. */
    public static Spec idle() {
        return window(0, 0, 0, 0, 0, 0, 0, null, 0, 0);
    }

    /** Ventanas de la más vieja a la más reciente, con instantes separados por una semana. */
    public static List<StatsWindow> windows(Spec... specs) {
        return windows(List.of(specs));
    }

    public static List<StatsWindow> windows(List<Spec> specs) {
        List<StatsWindow> result = new ArrayList<>(specs.size());
        for (int i = 0; i < specs.size(); i++) {
            Spec spec = specs.get(i);
            Instant to = FIRST_WINDOW_END.plus(WEEK.multipliedBy(i));
            UUID runId = spec.runId() != null ? spec.runId() : runOfWindow(i);
            result.add(new StatsWindow((long) i + 1, (long) i + 2, runId, to.minus(WEEK), to, spec.seasonReset(),
                    spec.minutes(), spec.goals(), spec.assists(), spec.shots(), spec.keyPasses(), spec.tackles(),
                    spec.dribbles(), spec.yellowCards(), spec.redCards(),
                    spec.rating() == null ? null : new BigDecimal(spec.rating())));
        }
        return result;
    }

    /** {@code count} copias de la misma ventana. */
    public static List<StatsWindow> repeated(int count, Spec spec) {
        return windows(Collections.nCopies(count, spec));
    }

    /** Corrida asignada por defecto a la ventana en la posición {@code index} (0 = la más vieja). */
    public static UUID runOfWindow(int index) {
        return new UUID(0, 100L + index);
    }

    public static PlayerStats season(Integer minutes, Integer goals, Integer assists, Integer shots,
                                     Integer keyPasses, Integer dribbles, Integer tackles, String rating) {
        PlayerStats stats = new PlayerStats();
        stats.setMinutesPlayed(minutes);
        stats.setGoals(goals);
        stats.setAssists(assists);
        stats.setShots(shots);
        stats.setKeyPasses(keyPasses);
        stats.setDribbles(dribbles);
        stats.setTackles(tackles);
        stats.setRating(rating == null ? null : new BigDecimal(rating));
        return stats;
    }

    /** Cotización MOMENTUM previa de otra corrida y anterior a todas las ventanas. */
    public static PlayerQuote momentumQuote(String value) {
        return momentumQuote(value, PREVIOUS_AT, PREVIOUS_RUN);
    }

    public static PlayerQuote momentumQuote(String value, Instant calculatedAt, UUID runId) {
        return quote("MOMENTUM", value, calculatedAt, runId);
    }

    public static PlayerQuote quote(String code, String value, Instant calculatedAt, UUID runId) {
        return quote(code, value, QuoteStatus.CALCULATED, calculatedAt, runId);
    }

    public static PlayerQuote quote(String code, String value, QuoteStatus status, Instant calculatedAt,
                                    UUID runId) {
        return PlayerQuote.builder()
                .strategyCode(code)
                .strategyVersion(1)
                .value(value == null ? null : new BigDecimal(value))
                .status(status)
                .calculatedAt(calculatedAt)
                .runId(runId)
                .build();
    }

    public static ValuationInput input(Set<Position> positions, PlayerStats stats, List<PlayerQuote> quotes,
                                       List<StatsWindow> windows) {
        return new ValuationInput(PLAYER_ID, positions, stats, null, quotes, windows);
    }

    public static ValuationInput input(Position position, PlayerStats stats, List<PlayerQuote> quotes,
                                       List<StatsWindow> windows) {
        return input(Set.of(position), stats, quotes, windows);
    }
}
