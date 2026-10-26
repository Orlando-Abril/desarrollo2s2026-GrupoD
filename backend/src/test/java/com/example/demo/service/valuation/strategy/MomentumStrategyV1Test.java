package com.example.demo.service.valuation.strategy;

import com.example.demo.model.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static com.example.demo.service.valuation.strategy.Metric.MINUTES;
import static com.example.demo.service.valuation.strategy.Metric.RED_CARDS;
import static com.example.demo.service.valuation.strategy.Metric.YELLOW_CARDS;
import static org.assertj.core.api.Assertions.assertThat;

class MomentumStrategyV1Test {

    private static final ValuationStrategyDefinition MOMENTUM = MomentumStrategyV1.DEFINITION;

    @Test
    void parameters() {
        assertThat(MOMENTUM.code()).isEqualTo("MOMENTUM");
        assertThat(MOMENTUM.version()).isEqualTo(1);
        assertThat(MOMENTUM.type()).isEqualTo(StrategyType.MOMENTUM);
        assertThat(MOMENTUM.baseValue()).isEqualByComparingTo("1.00");
        assertThat(MOMENTUM.scaleFactor()).isEqualByComparingTo("0.15");
        assertThat(MOMENTUM.maxVariation()).isEqualByComparingTo("0.15");
        assertThat(MOMENTUM.momentum()).isEqualTo(new MomentumParameters(5, 2,
                List.of(new BigDecimal("5"), new BigDecimal("4"), new BigDecimal("3"), new BigDecimal("2"),
                        new BigDecimal("1")),
                90, new BigDecimal("1.5")));
    }

    @ParameterizedTest
    @EnumSource(Position.class)
    void everyPositionDeclaresAllMetricsAndPositiveWeightsSumOne(Position position) {
        assertThat(MOMENTUM.weights().get(position).keySet()).containsExactlyInAnyOrder(Metric.values());
        BigDecimal positives = MOMENTUM.weights().get(position).values().stream()
                .filter(weight -> weight.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(positives).isEqualByComparingTo("1.00");
    }

    @ParameterizedTest
    @EnumSource(Position.class)
    void cardsPenalizeRedMoreThanYellowAndMinutesReward(Position position) {
        BigDecimal yellow = MOMENTUM.weight(position, YELLOW_CARDS);
        BigDecimal red = MOMENTUM.weight(position, RED_CARDS);

        assertThat(yellow.signum()).isNegative();
        assertThat(red).isLessThan(yellow);
        assertThat(MOMENTUM.weight(position, MINUTES).signum()).isPositive();
    }

    @ParameterizedTest
    @MethodSource("contractTable")
    void weightsMatchTheContract(Position position, String[] expected) {
        String[] row = Stream.of(Metric.values())
                .map(metric -> MOMENTUM.weight(position, metric).toPlainString())
                .toArray(String[]::new);

        assertThat(row).containsExactly(expected);
    }

    static Stream<Arguments> contractTable() {
        return Stream.of(
                Arguments.of(Position.GOALKEEPER,
                        new String[]{"0.00", "0.00", "0.00", "0.05", "0.00", "0.05", "0.60", "0.30", "-0.05", "-0.20"}),
                Arguments.of(Position.DEFENDER,
                        new String[]{"0.05", "0.05", "0.00", "0.10", "0.05", "0.30", "0.25", "0.20", "-0.10", "-0.20"}),
                Arguments.of(Position.MIDFIELDER,
                        new String[]{"0.20", "0.15", "0.05", "0.15", "0.10", "0.10", "0.15", "0.10", "-0.05", "-0.15"}),
                Arguments.of(Position.FORWARD,
                        new String[]{"0.30", "0.15", "0.10", "0.05", "0.10", "0.00", "0.20", "0.10", "-0.05", "-0.15"}));
    }
}
