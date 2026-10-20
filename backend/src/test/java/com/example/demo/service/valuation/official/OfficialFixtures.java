package com.example.demo.service.valuation.official;

import com.example.demo.model.PlayerQuote;
import com.example.demo.model.PlayerStats;
import com.example.demo.model.Position;
import com.example.demo.model.QuoteStatus;
import com.example.demo.service.valuation.PopulationEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * Datos de prueba del algoritmo OFFICIAL: las poblaciones de
 * {@code specs/015-estrategia-oficial/contracts/valuation-strategies.md} §5. Cada método devuelve objetos
 * nuevos porque {@link PlayerStats} es mutable.
 */
public final class OfficialFixtures {

    public static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

    public static final long A = 1;
    public static final long B = 2;
    public static final long C = 3;
    public static final long X = 4;
    public static final long G1 = 11;
    public static final long G2 = 12;
    public static final long D1 = 21;
    public static final long F1 = 31;
    public static final long F2 = 32;

    private OfficialFixtures() {
    }

    public static PlayerStats stats(Integer minutes, Integer goals, Integer assists, Integer shots, Integer keyPasses,
                                    Integer dribbles, Integer tackles, String rating) {
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

    public static PopulationEntry entry(long id, PlayerStats stats, Position... positions) {
        return new PopulationEntry(id, Set.of(positions), stats);
    }

    /** Copia de la entrada con las gambetas ausentes (falla de la consulta opcional de WhoScored). */
    public static PopulationEntry withoutDribbles(PopulationEntry entry) {
        PlayerStats source = entry.stats();
        PlayerStats copy = stats(source.getMinutesPlayed(), source.getGoals(), source.getAssists(), source.getShots(),
                source.getKeyPasses(), null, source.getTackles(), null);
        copy.setRating(source.getRating());
        return new PopulationEntry(entry.playerId(), entry.positions(), copy);
    }

    /** Copia de la entrada con otro valor de minutos jugados. */
    public static PopulationEntry withMinutes(PopulationEntry entry, Integer minutes) {
        PlayerStats source = entry.stats();
        PlayerStats copy = stats(minutes, source.getGoals(), source.getAssists(), source.getShots(),
                source.getKeyPasses(), source.getDribbles(), source.getTackles(), null);
        copy.setRating(source.getRating());
        return new PopulationEntry(entry.playerId(), entry.positions(), copy);
    }

    public static PlayerQuote quote(String strategyCode, String value, Instant calculatedAt) {
        return quote(strategyCode, value, QuoteStatus.CALCULATED, calculatedAt);
    }

    public static PlayerQuote quote(String strategyCode, String value, QuoteStatus status, Instant calculatedAt) {
        return PlayerQuote.builder()
                .strategyCode(strategyCode)
                .strategyVersion(1)
                .value(value == null ? null : new BigDecimal(value))
                .status(status)
                .calculatedAt(calculatedAt)
                .build();
    }

    // §5.1 MIDFIELDER
    public static PopulationEntry a() {
        return entry(A, stats(900, 5, 4, 30, 20, 15, 25, "8.0"), Position.MIDFIELDER);
    }

    public static PopulationEntry b() {
        return entry(B, stats(900, 1, 0, 10, 5, 3, 8, "6.0"), Position.MIDFIELDER);
    }

    public static PopulationEntry c() {
        return entry(C, stats(1800, 6, 4, 40, 25, 18, 33, "7.0"), Position.MIDFIELDER);
    }

    // §5.5 MIDFIELDER + FORWARD
    public static PopulationEntry x() {
        return entry(X, stats(1800, 6, 4, 40, 25, 30, 16, "7.5"), Position.MIDFIELDER, Position.FORWARD);
    }

    // §5.2 GOALKEEPER
    public static PopulationEntry g1() {
        return entry(G1, stats(1800, 0, 1, 0, 9, null, 6, "7.2"), Position.GOALKEEPER);
    }

    public static PopulationEntry g2() {
        return entry(G2, stats(1800, 0, 0, 0, 3, null, 2, "6.6"), Position.GOALKEEPER);
    }

    // §5.3 DEFENDER
    public static PopulationEntry d1() {
        return entry(D1, stats(1350, 1, 2, 3, 10, 5, 40, "7.0"), Position.DEFENDER);
    }

    // §5.4 FORWARD
    public static PopulationEntry f1() {
        return entry(F1, stats(1800, 20, 6, 80, 20, 40, 10, "7.8"), Position.FORWARD);
    }

    public static PopulationEntry f2() {
        return entry(F2, stats(1800, 4, 2, 30, 30, 20, 20, "6.8"), Position.FORWARD);
    }
}
