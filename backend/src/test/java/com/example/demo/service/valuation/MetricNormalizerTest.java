package com.example.demo.service.valuation;

import com.example.demo.model.Position;
import com.example.demo.service.valuation.strategy.Metric;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.demo.service.valuation.official.OfficialFixtures.A;
import static com.example.demo.service.valuation.official.OfficialFixtures.B;
import static com.example.demo.service.valuation.official.OfficialFixtures.C;
import static com.example.demo.service.valuation.official.OfficialFixtures.X;
import static com.example.demo.service.valuation.official.OfficialFixtures.a;
import static com.example.demo.service.valuation.official.OfficialFixtures.b;
import static com.example.demo.service.valuation.official.OfficialFixtures.c;
import static com.example.demo.service.valuation.official.OfficialFixtures.entry;
import static com.example.demo.service.valuation.official.OfficialFixtures.f1;
import static com.example.demo.service.valuation.official.OfficialFixtures.f2;
import static com.example.demo.service.valuation.official.OfficialFixtures.stats;
import static com.example.demo.service.valuation.official.OfficialFixtures.withMinutes;
import static com.example.demo.service.valuation.official.OfficialFixtures.withoutDribbles;
import static com.example.demo.service.valuation.official.OfficialFixtures.x;
import static com.example.demo.service.valuation.strategy.Metric.ASSISTS;
import static com.example.demo.service.valuation.strategy.Metric.DRIBBLES;
import static com.example.demo.service.valuation.strategy.Metric.GOALS;
import static com.example.demo.service.valuation.strategy.Metric.KEY_PASSES;
import static com.example.demo.service.valuation.strategy.Metric.RATING;
import static com.example.demo.service.valuation.strategy.Metric.SHOTS;
import static com.example.demo.service.valuation.strategy.Metric.TACKLES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricNormalizerTest {

    private static final List<Metric> COUNTS = List.of(GOALS, ASSISTS, SHOTS, KEY_PASSES, DRIBBLES, TACKLES);

    private final MetricNormalizer normalizer = new MetricNormalizer(90);

    @Test
    void countsArePer90Minutes() {
        // 5 goles en 450' = 1.0/90; 0 en 900' = 0; 1 en 180' = 0.5/90 → normalizado 0.5
        NormalizedPopulation population = normalizer.normalize(List.of(
                entry(1, stats(450, 5, 0, 0, 0, 0, 0, "7.0"), Position.FORWARD),
                entry(2, stats(900, 0, 0, 0, 0, 0, 0, "7.0"), Position.FORWARD),
                entry(3, stats(180, 1, 0, 0, 0, 0, 0, "7.0"), Position.FORWARD)));

        assertThat(population.normalized(1L, Position.FORWARD).get(GOALS)).isEqualByComparingTo("1");
        assertThat(population.normalized(2L, Position.FORWARD).get(GOALS)).isEqualByComparingTo("0");
        assertThat(population.normalized(3L, Position.FORWARD).get(GOALS)).isEqualByComparingTo("0.5");
    }

    @Test
    void minMaxWithinThePosition() {
        NormalizedPopulation population = normalizer.normalize(List.of(a(), b(), c()));

        for (Metric metric : COUNTS) {
            assertThat(population.normalized(A, Position.MIDFIELDER).get(metric)).as("A %s", metric).isEqualByComparingTo("1");
            assertThat(population.normalized(B, Position.MIDFIELDER).get(metric)).as("B %s", metric).isEqualByComparingTo("0");
            assertThat(population.normalized(C, Position.MIDFIELDER).get(metric)).as("C %s", metric).isEqualByComparingTo("0.5");
        }
    }

    @Test
    void singlePlayerOrEqualValuesGiveHalf() {
        NormalizedPopulation single = normalizer.normalize(List.of(a()));
        NormalizedPopulation equal = normalizer.normalize(List.of(
                entry(1, stats(900, 2, 2, 2, 2, 2, 2, "7.0"), Position.DEFENDER),
                entry(2, stats(900, 2, 2, 2, 2, 2, 2, "6.0"), Position.DEFENDER)));

        for (Metric metric : COUNTS) {
            assertThat(single.normalized(A, Position.MIDFIELDER).get(metric)).isEqualByComparingTo("0.5");
            assertThat(equal.normalized(2L, Position.DEFENDER).get(metric)).isEqualByComparingTo("0.5");
        }
    }

    @Test
    void individualNullCountsAsZeroAndIsNotWithoutData() {
        NormalizedPopulation population = normalizer.normalize(List.of(
                entry(1, stats(900, 4, 0, null, 0, 0, 0, "7.0"), Position.FORWARD),
                entry(2, stats(900, 2, 0, 10, 0, 0, 0, "7.0"), Position.FORWARD)));

        assertThat(population.normalized(1L, Position.FORWARD).get(SHOTS)).isEqualByComparingTo("0");
        assertThat(population.normalized(2L, Position.FORWARD).get(SHOTS)).isEqualByComparingTo("1");
        assertThat(population.metricsWithoutData(Position.FORWARD)).isEmpty();
    }

    @Test
    void ratingIsOverTenAndClamped() {
        NormalizedPopulation population = normalizer.normalize(List.of(
                entry(1, stats(900, 0, 0, 0, 0, 0, 0, "7.0"), Position.FORWARD),
                entry(2, stats(900, 0, 0, 0, 0, 0, 0, "11"), Position.FORWARD),
                entry(3, stats(900, 0, 0, 0, 0, 0, 0, "-1"), Position.FORWARD)));

        assertThat(population.normalized(1L, Position.FORWARD).get(RATING)).isEqualByComparingTo("0.70");
        assertThat(population.normalized(2L, Position.FORWARD).get(RATING)).isEqualByComparingTo("1");
        assertThat(population.normalized(3L, Position.FORWARD).get(RATING)).isEqualByComparingTo("0");
    }

    @Test
    void multiPositionPlayerIsNormalizedInEachPopulation() {
        NormalizedPopulation population = normalizer.normalize(List.of(a(), b(), c(), x(), f1(), f2()));

        assertThat(population.normalized(X, Position.MIDFIELDER).get(DRIBBLES)).isEqualByComparingTo("1");
        assertThat(population.normalized(X, Position.FORWARD).get(DRIBBLES)).isEqualByComparingTo("0.5");
        assertThat(population.normalized(X, Position.FORWARD).get(GOALS)).isEqualByComparingTo("0.125");
    }

    @Test
    void duplicatePlayerIsRejected() {
        assertThatThrownBy(() -> normalizer.normalize(List.of(a(), a())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(A));
    }

    @Test
    void emptyPopulationDoesNotFail() {
        NormalizedPopulation population = normalizer.normalize(List.of());

        assertThat(population.minMinutes()).isEqualTo(90);
        assertThat(population.contains(A)).isFalse();
        for (Position position : Position.values()) {
            assertThat(population.metricsWithoutData(position)).isEmpty();
        }
    }

    @Test
    void populationIsImmutable() {
        NormalizedPopulation population = normalizer.normalize(List.of(a(), withoutDribbles(b())));

        Map<Metric, BigDecimal> values = population.normalized(A, Position.MIDFIELDER);
        Set<Metric> withoutData = population.metricsWithoutData(Position.MIDFIELDER);
        assertThatThrownBy(() -> values.put(GOALS, BigDecimal.ONE)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> withoutData.add(GOALS)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void thresholdMustBePositive() {
        assertThatThrownBy(() -> new MetricNormalizer(0)).isInstanceOf(IllegalArgumentException.class);
    }

    // US2: elegibilidad

    @Test
    void ineligiblePlayersAreLeftOut() {
        NormalizedPopulation population = normalizer.normalize(List.of(
                entry(1, null, Position.FORWARD),
                entry(2, stats(900, 1, 1, 1, 1, 1, 1, "7.0")),
                entry(3, stats(null, 1, 1, 1, 1, 1, 1, "7.0"), Position.FORWARD),
                entry(4, stats(0, 1, 1, 1, 1, 1, 1, "7.0"), Position.FORWARD),
                entry(5, stats(89, 1, 1, 1, 1, 1, 1, "7.0"), Position.FORWARD),
                entry(6, stats(90, 1, 1, 1, 1, 1, 1, "7.0"), Position.FORWARD)));

        assertThat(List.of(1L, 2L, 3L, 4L, 5L)).noneMatch(population::contains);
        assertThat(population.contains(6L)).isTrue();
    }

    @Test
    void ineligiblePlayersDoNotMoveTheExtremes() {
        NormalizedPopulation base = normalizer.normalize(List.of(a(), b(), c()));
        NormalizedPopulation withOutlier = normalizer.normalize(List.of(a(), b(), c(),
                entry(99, stats(89, 10, 10, 10, 10, 10, 10, "10"), Position.MIDFIELDER)));

        for (long id : new long[]{A, B, C}) {
            assertThat(withOutlier.normalized(id, Position.MIDFIELDER)).isEqualTo(base.normalized(id, Position.MIDFIELDER));
        }
    }

    @Test
    void eligibilityRuleIsShared() {
        assertThat(MetricNormalizer.isEligible(Set.of(Position.FORWARD), stats(90, 0, 0, 0, 0, 0, 0, null), 90)).isTrue();
        assertThat(MetricNormalizer.isEligible(Set.of(Position.FORWARD), withMinutes(a(), 89).stats(), 90)).isFalse();
        assertThat(MetricNormalizer.isEligible(Set.of(), a().stats(), 90)).isFalse();
        assertThat(MetricNormalizer.isEligible(Set.of(Position.FORWARD), null, 90)).isFalse();
    }

    // US3: métricas sin dato

    @Test
    void metricWithoutDataInTheWholePositionIsReported() {
        NormalizedPopulation population = normalizer.normalize(List.of(
                withoutDribbles(a()), withoutDribbles(b()), withoutDribbles(c()), f1(), f2()));

        assertThat(population.metricsWithoutData(Position.MIDFIELDER)).containsExactly(DRIBBLES);
        assertThat(population.metricsWithoutData(Position.FORWARD)).isEmpty();
    }

    @Test
    void oneValueIsEnoughToKeepTheMetric() {
        NormalizedPopulation population = normalizer.normalize(List.of(withoutDribbles(a()), withoutDribbles(b()), c()));

        assertThat(population.metricsWithoutData(Position.MIDFIELDER)).isEmpty();
    }

    @Test
    void ratingWithoutDataIsReported() {
        NormalizedPopulation population = normalizer.normalize(List.of(
                entry(1, stats(900, 1, 1, 1, 1, 1, 1, null), Position.DEFENDER),
                entry(2, stats(900, 2, 2, 2, 2, 2, 2, null), Position.DEFENDER)));

        assertThat(population.metricsWithoutData(Position.DEFENDER)).containsExactly(RATING);
    }
}
