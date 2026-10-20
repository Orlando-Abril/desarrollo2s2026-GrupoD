package com.example.demo.service.valuation.official;

import com.example.demo.model.PlayerQuote;
import com.example.demo.model.Position;
import com.example.demo.model.QuoteStatus;
import com.example.demo.service.valuation.NormalizedPopulation;
import com.example.demo.service.valuation.PopulationEntry;
import com.example.demo.service.valuation.QuoteResult;
import com.example.demo.service.valuation.ValuationInput;
import com.example.demo.service.valuation.strategy.Metric;
import com.example.demo.service.valuation.strategy.MomentumStrategyV1;
import com.example.demo.service.valuation.strategy.OfficialStrategyV1;
import com.example.demo.service.valuation.strategy.StrategyType;
import com.example.demo.service.valuation.strategy.ValuationStrategyDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static com.example.demo.service.valuation.official.OfficialFixtures.A;
import static com.example.demo.service.valuation.official.OfficialFixtures.B;
import static com.example.demo.service.valuation.official.OfficialFixtures.C;
import static com.example.demo.service.valuation.official.OfficialFixtures.D1;
import static com.example.demo.service.valuation.official.OfficialFixtures.F1;
import static com.example.demo.service.valuation.official.OfficialFixtures.F2;
import static com.example.demo.service.valuation.official.OfficialFixtures.G1;
import static com.example.demo.service.valuation.official.OfficialFixtures.G2;
import static com.example.demo.service.valuation.official.OfficialFixtures.T0;
import static com.example.demo.service.valuation.official.OfficialFixtures.X;
import static com.example.demo.service.valuation.official.OfficialFixtures.a;
import static com.example.demo.service.valuation.official.OfficialFixtures.b;
import static com.example.demo.service.valuation.official.OfficialFixtures.c;
import static com.example.demo.service.valuation.official.OfficialFixtures.d1;
import static com.example.demo.service.valuation.official.OfficialFixtures.entry;
import static com.example.demo.service.valuation.official.OfficialFixtures.f1;
import static com.example.demo.service.valuation.official.OfficialFixtures.f2;
import static com.example.demo.service.valuation.official.OfficialFixtures.g1;
import static com.example.demo.service.valuation.official.OfficialFixtures.g2;
import static com.example.demo.service.valuation.official.OfficialFixtures.quote;
import static com.example.demo.service.valuation.official.OfficialFixtures.stats;
import static com.example.demo.service.valuation.official.OfficialFixtures.withMinutes;
import static com.example.demo.service.valuation.official.OfficialFixtures.withoutDribbles;
import static com.example.demo.service.valuation.official.OfficialFixtures.x;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Los números esperados salen de {@code specs/015-estrategia-oficial/contracts/valuation-strategies.md} §5. */
class OfficialPerformanceAlgorithmTest {

    private static final ValuationStrategyDefinition OFFICIAL = OfficialStrategyV1.DEFINITION;

    private final OfficialPerformanceAlgorithm algorithm = new OfficialPerformanceAlgorithm(90);

    // US1: fórmula de la consigna y las 4 posiciones

    @Test
    void typeIsOfficial() {
        assertThat(algorithm.type()).isEqualTo(StrategyType.OFFICIAL);
    }

    @Test
    void midfielderIsTheAssignmentFormula() {
        Map<Long, QuoteResult> results = valuate(List.of(a(), b(), c()));

        assertCalculated(results.get(A), "0.9600", "1.0960");
        assertCalculated(results.get(B), "0.1200", "1.0120");
        assertCalculated(results.get(C), "0.5400", "1.0540");
        assertThat(results.values()).allMatch(result -> result.excludedMetrics().isEmpty());
    }

    @Test
    void goalkeeperWeighsRatingAndIgnoresZeroWeightMetrics() {
        Map<Long, QuoteResult> results = valuate(List.of(g1(), g2()));

        assertCalculated(results.get(G1), "0.8040", "1.0804");
        assertCalculated(results.get(G2), "0.4620", "1.0462");
        assertThat(results.values()).allMatch(result -> result.excludedMetrics().isEmpty());
    }

    @Test
    void singleDefenderGetsHalfInEveryCount() {
        assertCalculated(valuate(List.of(d1())).get(D1), "0.5600", "1.0560");
    }

    @Test
    void forwardWeighsGoalsAndShots() {
        Map<Long, QuoteResult> results = valuate(List.of(f1(), f2()));

        assertCalculated(results.get(F1), "0.8560", "1.0856");
        assertCalculated(results.get(F2), "0.2360", "1.0236");
    }

    @Test
    void individualNullCountsAsZero() {
        PopulationEntry aWithoutShots = entry(A, stats(900, 5, 4, null, 20, 15, 25, "8.0"), Position.MIDFIELDER);

        Map<Long, QuoteResult> results = valuate(List.of(aWithoutShots, b(), c()));

        assertCalculated(results.get(A), "0.8600", "1.0860");
        assertCalculated(results.get(B), "0.1700", "1.0170");
        assertCalculated(results.get(C), "0.5900", "1.0590");
    }

    @Test
    void rejectsNonOfficialDefinition() {
        NormalizedPopulation population = algorithm.normalize(List.of(a()));
        ValuationStrategyDefinition momentum = MomentumStrategyV1.DEFINITION;

        assertThatThrownBy(() -> algorithm.calculate(input(a(), population), momentum))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingPopulation() {
        assertThatThrownBy(() -> algorithm.calculate(input(a(), null), OFFICIAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPopulationBuiltWithAnotherThreshold() {
        NormalizedPopulation population = new OfficialPerformanceAlgorithm(270).normalize(List.of(a()));

        assertThatThrownBy(() -> algorithm.calculate(input(a(), population), OFFICIAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("270");
    }

    // US2: NO_STATS

    @Test
    void playersWithoutEnoughDataAreNoStats() {
        List<PopulationEntry> ineligible = List.of(
                entry(91, null, Position.MIDFIELDER),
                entry(92, stats(900, 1, 1, 1, 1, 1, 1, "7.0")),
                entry(93, stats(0, 1, 1, 1, 1, 1, 1, "7.0"), Position.MIDFIELDER),
                entry(94, stats(null, 1, 1, 1, 1, 1, 1, "7.0"), Position.MIDFIELDER),
                entry(95, stats(89, 1, 1, 1, 1, 1, 1, "7.0"), Position.MIDFIELDER));
        List<PopulationEntry> population = new ArrayList<>(List.of(a(), b(), c()));
        population.addAll(ineligible);

        Map<Long, QuoteResult> results = valuate(population);

        for (PopulationEntry entry : ineligible) {
            assertThat(results.get(entry.playerId())).as("jugador %s", entry.playerId()).isEqualTo(QuoteResult.noStats());
        }
    }

    @Test
    void thresholdComesFromThePopulation() {
        NormalizedPopulation population = algorithm.normalize(List.of(a(), b(), c()));

        assertThat(algorithm.calculate(input(withMinutes(a(), 89), population), OFFICIAL)).isEqualTo(QuoteResult.noStats());
        assertThat(valuate(List.of(withMinutes(a(), 90), b())).get(A).status()).isEqualTo(QuoteStatus.CALCULATED);
    }

    @Test
    void emptyPopulationGivesNoStats() {
        NormalizedPopulation empty = algorithm.normalize(List.of());

        assertThat(algorithm.calculate(input(a(), empty), OFFICIAL)).isEqualTo(QuoteResult.noStats());
    }

    @Test
    void noStatsPlayerWithPreviousQuoteStillHasNoValue() {
        NormalizedPopulation population = algorithm.normalize(List.of(a()));
        PopulationEntry withoutStats = entry(91, null, Position.MIDFIELDER);

        QuoteResult result = algorithm.calculate(input(withoutStats, population, quote("OFFICIAL", "1.20", T0)), OFFICIAL);

        assertThat(result).isEqualTo(QuoteResult.noStats());
    }

    @Test
    void noStatsPlayersDoNotChangeTheOthers() {
        Map<Long, QuoteResult> base = valuate(List.of(a(), b(), c()));
        Map<Long, QuoteResult> withOutliers = valuate(List.of(a(), b(), c(),
                entry(91, stats(60, 9, 9, 9, 9, 9, 9, "10"), Position.MIDFIELDER),
                entry(92, stats(89, 0, 0, 0, 0, 0, 0, "1"), Position.MIDFIELDER)));

        for (long id : new long[]{A, B, C}) {
            assertThat(withOutliers.get(id)).isEqualTo(base.get(id));
        }
    }

    // US3: reparto de pesos excluidos

    @Test
    void dribblesMissingInTheWholePositionIsRedistributed() {
        Map<Long, QuoteResult> results = valuate(List.of(withoutDribbles(a()), withoutDribbles(b()), withoutDribbles(c())));

        assertCalculated(results.get(A), "0.9556", "1.0956");
        assertCalculated(results.get(B), "0.1333", "1.0133");
        assertCalculated(results.get(C), "0.5444", "1.0544");
        assertThat(results.values()).allMatch(result ->
                result.excludedMetrics().equals(Map.of(Position.MIDFIELDER, EnumSet.of(Metric.DRIBBLES))));
    }

    @Test
    void zeroWeightMetricWithoutDataIsNotReported() {
        Map<Long, QuoteResult> results = valuate(List.of(g1(), g2()));

        assertThat(results.get(G1).excludedMetrics()).isEmpty();
        assertThat(results.get(G1).value()).isEqualByComparingTo("1.0804");
    }

    @Test
    void oneValueKeepsTheMetric() {
        Map<Long, QuoteResult> results = valuate(List.of(withoutDribbles(a()), withoutDribbles(b()), c()));

        assertThat(results.values()).allMatch(result -> result.excludedMetrics().isEmpty());
    }

    @Test
    void positionWithoutAnyDataGivesNoStats() {
        Map<Long, QuoteResult> results = valuate(List.of(
                entry(1, stats(900, null, null, null, null, null, null, null), Position.DEFENDER),
                entry(2, stats(900, null, null, null, null, null, null, null), Position.DEFENDER)));

        assertThat(results.values()).allMatch(QuoteResult.noStats()::equals);
    }

    // US4: varias posiciones

    @Test
    void multiPositionPlayerAveragesItsPositions() {
        Map<Long, QuoteResult> results = valuate(List.of(a(), b(), c(), x(), f1(), f2()));

        assertCalculated(results.get(X), "0.4738", "1.0474");
        assertThat(results.get(A).value()).isEqualByComparingTo("1.0960");
        assertThat(results.get(B).value()).isEqualByComparingTo("1.0120");
        assertThat(results.get(C).value()).isEqualByComparingTo("1.0540");
        assertThat(results.get(F1).value()).isEqualByComparingTo("1.0856");
        assertThat(results.get(F2).value()).isEqualByComparingTo("1.0236");
    }

    @Test
    void positionWithoutScoreIsLeftOutOfTheAverage() {
        PopulationEntry empty = entry(60, stats(900, null, null, null, null, null, null, null),
                Position.MIDFIELDER, Position.FORWARD);
        PopulationEntry emptyMidfielder = entry(61, stats(900, null, null, null, null, null, null, null),
                Position.MIDFIELDER);

        QuoteResult result = valuate(List.of(empty, emptyMidfielder, f1(), f2())).get(60L);

        assertCalculated(result, "0.0000", "1.0000");
        assertThat(result.excludedMetrics()).containsOnlyKeys(Position.MIDFIELDER);
        assertThat(result.excludedMetrics().get(Position.MIDFIELDER)).containsExactlyInAnyOrder(Metric.GOALS,
                Metric.ASSISTS, Metric.SHOTS, Metric.KEY_PASSES, Metric.DRIBBLES, Metric.TACKLES, Metric.RATING);
    }

    // US5: tope y redondeo

    @Test
    void valueIsCappedAgainstThePreviousQuote() {
        NormalizedPopulation population = algorithm.normalize(List.of(a(), b(), c()));

        QuoteResult up = algorithm.calculate(input(a(), population, quote("OFFICIAL", "0.80", T0)), OFFICIAL);
        QuoteResult down = algorithm.calculate(input(b(), population, quote("OFFICIAL", "1.50", T0)), OFFICIAL);
        QuoteResult within = algorithm.calculate(input(c(), population, quote("OFFICIAL", "1.00", T0)), OFFICIAL);

        assertCalculated(up, "0.9600", "0.9600");
        assertCalculated(down, "0.1200", "1.2000");
        assertCalculated(within, "0.5400", "1.0540");
    }

    @Test
    void capUsesTheNewestQuoteWhateverItsStatus() {
        NormalizedPopulation population = algorithm.normalize(List.of(a(), b(), c()));

        QuoteResult result = algorithm.calculate(input(a(), population,
                quote("OFFICIAL", "0.80", QuoteStatus.NO_STATS, T0.plusSeconds(60)),
                quote("OFFICIAL", "1.00", T0)), OFFICIAL);

        assertThat(result.value()).isEqualByComparingTo("0.9600");
    }

    @Test
    void fifthDecimalIsRoundedHalfUp() {
        // 0.70×0.5 + 0.30×0.715 = 0.5645 → 1.05645 → 1.0565 (HALF_EVEN daría 1.0564)
        PopulationEntry defender = entry(D1, stats(1350, 1, 2, 3, 10, 5, 40, "7.15"), Position.DEFENDER);

        QuoteResult result = valuate(List.of(defender)).get(D1);

        assertCalculated(result, "0.5645", "1.0565");
        assertThat(result.value().toPlainString()).isEqualTo("1.0565");
    }

    @Test
    void previousQuoteFromAnotherStrategyIsRejected() {
        NormalizedPopulation population = algorithm.normalize(List.of(a()));

        assertThatThrownBy(() -> algorithm.calculate(input(a(), population, quote("MOMENTUM", "1.00", T0)), OFFICIAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MOMENTUM");
    }

    @Test
    void previousQuoteWithoutValueIsRejected() {
        NormalizedPopulation population = algorithm.normalize(List.of(a()));

        assertThatThrownBy(() -> algorithm.calculate(input(a(), population, quote("OFFICIAL", null, T0)), OFFICIAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(A));
    }

    @Test
    void inputIsValidatedBeforeNoStats() {
        NormalizedPopulation population = algorithm.normalize(List.of(a()));
        PopulationEntry withoutStats = entry(91, null, Position.MIDFIELDER);

        assertThatThrownBy(() -> algorithm.calculate(input(withoutStats, population, quote("OFFICIAL", null, T0)), OFFICIAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("91");
        assertThatThrownBy(() -> algorithm.calculate(input(withoutStats, population, quote("MOMENTUM", "1.00", T0)), OFFICIAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // US6: determinismo

    @Test
    void resultDoesNotDependOnPopulationOrder() {
        List<PopulationEntry> population = List.of(a(), b(), c(), x(), g1(), g2(), d1(), f1(), withoutDribbles(f2()),
                entry(70, stats(60, 3, 0, 0, 0, 0, 0, "9.0"), Position.FORWARD));
        Map<Long, QuoteResult> expected = valuate(population);

        for (int seed = 0; seed < 10; seed++) {
            List<PopulationEntry> shuffled = new ArrayList<>(population);
            Collections.shuffle(shuffled, new Random(seed));
            assertThat(valuate(shuffled)).as("seed %d", seed).isEqualTo(expected);
        }
        assertThat(valuate(population)).isEqualTo(expected);
        assertThat(expected.values()).noneMatch(result -> result.status() == QuoteStatus.INSUFFICIENT_HISTORY);
    }

    @Test
    void resultDoesNotDependOnPositionOrder() {
        NormalizedPopulation population = algorithm.normalize(List.of(a(), b(), c(), x(), f1(), f2()));
        PopulationEntry player = x();

        QuoteResult forwardFirst = algorithm.calculate(new ValuationInput(X,
                new LinkedHashSet<>(List.of(Position.FORWARD, Position.MIDFIELDER)), player.stats(), population,
                List.of(), List.of()), OFFICIAL);
        QuoteResult midfielderFirst = algorithm.calculate(new ValuationInput(X,
                new LinkedHashSet<>(List.of(Position.MIDFIELDER, Position.FORWARD)), player.stats(), population,
                List.of(), List.of()), OFFICIAL);

        assertThat(forwardFirst).isEqualTo(midfielderFirst);
    }

    private Map<Long, QuoteResult> valuate(List<PopulationEntry> entries) {
        NormalizedPopulation population = algorithm.normalize(entries);
        Map<Long, QuoteResult> results = new TreeMap<>();
        for (PopulationEntry entry : entries) {
            results.put(entry.playerId(), algorithm.calculate(input(entry, population), OFFICIAL));
        }
        return results;
    }

    private static ValuationInput input(PopulationEntry entry, NormalizedPopulation population, PlayerQuote... previous) {
        return new ValuationInput(entry.playerId(), entry.positions(), entry.stats(), population, List.of(previous),
                List.of());
    }

    private static void assertCalculated(QuoteResult result, String score, String value) {
        assertThat(result.status()).isEqualTo(QuoteStatus.CALCULATED);
        assertThat(result.score()).isEqualByComparingTo(score);
        assertThat(result.value()).isEqualByComparingTo(value);
        assertThat(result.score().scale()).isEqualTo(4);
        assertThat(result.value().scale()).isEqualTo(4);
    }
}
