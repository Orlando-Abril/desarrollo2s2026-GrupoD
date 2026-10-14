package com.example.demo.service.valuation.strategy;

import com.example.demo.model.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static com.example.demo.service.valuation.strategy.Metric.ASSISTS;
import static com.example.demo.service.valuation.strategy.Metric.DRIBBLES;
import static com.example.demo.service.valuation.strategy.Metric.GOALS;
import static com.example.demo.service.valuation.strategy.Metric.KEY_PASSES;
import static com.example.demo.service.valuation.strategy.Metric.RATING;
import static com.example.demo.service.valuation.strategy.Metric.SHOTS;
import static com.example.demo.service.valuation.strategy.Metric.TACKLES;
import static org.assertj.core.api.Assertions.assertThat;

class OfficialStrategyV1Test {

    private static final ValuationStrategyDefinition OFFICIAL = OfficialStrategyV1.DEFINITION;

    @Test
    void parameters() {
        assertThat(OFFICIAL.code()).isEqualTo("OFFICIAL");
        assertThat(OFFICIAL.version()).isEqualTo(1);
        assertThat(OFFICIAL.type()).isEqualTo(StrategyType.OFFICIAL);
        assertThat(OFFICIAL.baseValue()).isEqualByComparingTo("1.00");
        assertThat(OFFICIAL.scaleFactor()).isEqualByComparingTo("0.10");
        assertThat(OFFICIAL.maxVariation()).isEqualByComparingTo("0.20");
        assertThat(OFFICIAL.momentum()).isNull();
    }

    @ParameterizedTest
    @EnumSource(Position.class)
    void everyPositionDeclaresTheSevenMetricsWithNonNegativeWeightsSummingOne(Position position) {
        assertThat(OFFICIAL.weights().get(position).keySet())
                .containsExactlyInAnyOrder(GOALS, ASSISTS, SHOTS, KEY_PASSES, DRIBBLES, TACKLES, RATING);
        assertThat(OFFICIAL.weights().get(position).values()).allMatch(weight -> weight.signum() >= 0);
        assertThat(sum(position)).isEqualByComparingTo("1.00");
    }

    @Test
    void midfielderIsTheAssignmentFormula() {
        assertThat(row(Position.MIDFIELDER)).containsExactly("0.25", "0.15", "0.10", "0.10", "0.10", "0.10", "0.20");
    }

    @ParameterizedTest
    @MethodSource("contractTable")
    void weightsMatchTheContract(Position position, String[] expected) {
        assertThat(row(position)).containsExactly(expected);
    }

    static Stream<Arguments> contractTable() {
        return Stream.of(
                Arguments.of(Position.GOALKEEPER, new String[]{"0.00", "0.05", "0.00", "0.10", "0.00", "0.15", "0.70"}),
                Arguments.of(Position.DEFENDER, new String[]{"0.10", "0.10", "0.05", "0.10", "0.05", "0.30", "0.30"}),
                Arguments.of(Position.MIDFIELDER, new String[]{"0.25", "0.15", "0.10", "0.10", "0.10", "0.10", "0.20"}),
                Arguments.of(Position.FORWARD, new String[]{"0.30", "0.15", "0.15", "0.05", "0.10", "0.05", "0.20"}));
    }

    @Test
    void forwardsWeighGoalsAndShotsAndDefendersWeighTacklesMoreThanTheReference() {
        assertThat(OFFICIAL.weight(Position.FORWARD, GOALS)).isGreaterThan(OFFICIAL.weight(Position.MIDFIELDER, GOALS));
        assertThat(OFFICIAL.weight(Position.FORWARD, SHOTS)).isGreaterThan(OFFICIAL.weight(Position.MIDFIELDER, SHOTS));
        assertThat(OFFICIAL.weight(Position.DEFENDER, TACKLES))
                .isGreaterThan(OFFICIAL.weight(Position.MIDFIELDER, TACKLES));
    }

    private static String[] row(Position position) {
        return Stream.of(GOALS, ASSISTS, SHOTS, KEY_PASSES, DRIBBLES, TACKLES, RATING)
                .map(metric -> OFFICIAL.weight(position, metric).toPlainString())
                .toArray(String[]::new);
    }

    private static BigDecimal sum(Position position) {
        return OFFICIAL.weights().get(position).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
