package com.example.demo.service.valuation.strategy;

import com.example.demo.model.Position;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValuationStrategyDefinitionTest {

    /** Datos de una definición, editables para construir variantes inválidas. */
    record Draft(String code, int version, StrategyType type, BigDecimal baseValue, BigDecimal scaleFactor,
                 BigDecimal maxVariation, Map<Position, Map<Metric, BigDecimal>> weights,
                 MomentumParameters momentum) {

        Draft withCode(String value) {
            return new Draft(value, version, type, baseValue, scaleFactor, maxVariation, weights, momentum);
        }

        Draft withVersion(int value) {
            return new Draft(code, value, type, baseValue, scaleFactor, maxVariation, weights, momentum);
        }

        Draft withType(StrategyType value) {
            return new Draft(code, version, value, baseValue, scaleFactor, maxVariation, weights, momentum);
        }

        Draft withBaseValue(String value) {
            return new Draft(code, version, type, new BigDecimal(value), scaleFactor, maxVariation, weights, momentum);
        }

        Draft withScaleFactor(String value) {
            return new Draft(code, version, type, baseValue, new BigDecimal(value), maxVariation, weights, momentum);
        }

        Draft withMaxVariation(String value) {
            return new Draft(code, version, type, baseValue, scaleFactor, new BigDecimal(value), weights, momentum);
        }

        Draft withWeights(Map<Position, Map<Metric, BigDecimal>> value) {
            return new Draft(code, version, type, baseValue, scaleFactor, maxVariation, value, momentum);
        }

        Draft withMomentum(MomentumParameters value) {
            return new Draft(code, version, type, baseValue, scaleFactor, maxVariation, weights, value);
        }

        Draft withWeight(Position position, Metric metric, String value) {
            Map<Position, Map<Metric, BigDecimal>> copy = new EnumMap<>(Position.class);
            weights.forEach((p, row) -> copy.put(p, new EnumMap<>(row)));
            copy.get(position).put(metric, new BigDecimal(value));
            return withWeights(copy);
        }

        ValuationStrategyDefinition build() {
            return new ValuationStrategyDefinition(code, version, type, baseValue, scaleFactor, maxVariation,
                    weights, momentum);
        }
    }

    static Draft official() {
        Map<Position, Map<Metric, BigDecimal>> weights = new EnumMap<>(Position.class);
        for (Position position : Position.values()) {
            Map<Metric, BigDecimal> row = new EnumMap<>(Metric.class);
            row.put(Metric.GOALS, new BigDecimal("0.50"));
            row.put(Metric.RATING, new BigDecimal("0.50"));
            weights.put(position, row);
        }
        return new Draft("TEST", 1, StrategyType.OFFICIAL, new BigDecimal("1.00"), new BigDecimal("0.10"),
                new BigDecimal("0.20"), weights, null);
    }

    static MomentumParameters params(int window, int minHistory, List<String> recencyWeights, int possibleMinutes,
                                     String ratingBand) {
        return new MomentumParameters(window, minHistory,
                recencyWeights == null ? null : recencyWeights.stream().map(w -> w == null ? null : new BigDecimal(w))
                        .toList(),
                possibleMinutes, ratingBand == null ? null : new BigDecimal(ratingBand));
    }

    static MomentumParameters params() {
        return params(5, 2, List.of("5", "4", "3", "2", "1"), 90, "1.5");
    }

    static Draft momentum() {
        return official().withType(StrategyType.MOMENTUM).withMomentum(params());
    }

    @ParameterizedTest
    @MethodSource("invalidDrafts")
    void rejectsInvalidDefinitions(UnaryOperator<Draft> breakIt) {
        Draft draft = breakIt.apply(official());

        assertThatThrownBy(draft::build).isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Named<UnaryOperator<Draft>>> invalidDrafts() {
        return Stream.of(
                Named.of("código en blanco", d -> d.withCode(" ")),
                Named.of("versión 0", d -> d.withVersion(0)),
                Named.of("baseValue 0", d -> d.withBaseValue("0")),
                Named.of("scaleFactor 0", d -> d.withScaleFactor("0")),
                Named.of("maxVariation 0", d -> d.withMaxVariation("0")),
                Named.of("maxVariation 1.01", d -> d.withMaxVariation("1.01")),
                Named.of("sin GOALKEEPER", d -> {
                    Map<Position, Map<Metric, BigDecimal>> weights = new EnumMap<>(d.weights());
                    weights.remove(Position.GOALKEEPER);
                    return d.withWeights(weights);
                }),
                Named.of("posiciones con distintas métricas", d -> d.withWeight(Position.FORWARD, Metric.SHOTS, "0.10")),
                Named.of("MOMENTUM sin parámetros", d -> d.withType(StrategyType.MOMENTUM)),
                Named.of("OFFICIAL con parámetros de MOMENTUM", d -> d.withMomentum(params())));
    }

    @ParameterizedTest
    @MethodSource("invalidMomentumParameters")
    void rejectsInvalidMomentumParameters(Runnable build) {
        assertThatThrownBy(build::run).isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Named<Runnable>> invalidMomentumParameters() {
        List<String> five = List.of("5", "4", "3", "2", "1");
        return Stream.of(
                Named.of("window 0", () -> params(0, 2, List.of(), 90, "1.5")),
                Named.of("minHistory 1", () -> params(5, 1, five, 90, "1.5")),
                Named.of("minHistory > window + 1", () -> params(5, 7, five, 90, "1.5")),
                Named.of("recencyWeights nulo", () -> params(5, 2, null, 90, "1.5")),
                Named.of("recencyWeights de otro tamaño", () -> params(5, 2, List.of("4", "3", "2", "1"), 90, "1.5")),
                Named.of("recencyWeights con nulo", () -> params(5, 2, java.util.Arrays.asList("5", "4", null, "2", "1"),
                        90, "1.5")),
                Named.of("recencyWeights con 0", () -> params(5, 2, List.of("5", "4", "3", "2", "0"), 90, "1.5")),
                Named.of("recencyWeights no decrecientes", () -> params(5, 2, List.of("5", "4", "4", "2", "1"), 90,
                        "1.5")),
                Named.of("possibleMinutes 0", () -> params(5, 2, five, 0, "1.5")),
                Named.of("ratingBand nulo", () -> params(5, 2, five, 90, null)),
                Named.of("ratingBand 0", () -> params(5, 2, five, 90, "0")));
    }

    @Test
    void momentumParametersCopyTheRecencyWeights() {
        List<BigDecimal> source = new ArrayList<>(List.of(BigDecimal.TEN, BigDecimal.ONE));
        MomentumParameters parameters = new MomentumParameters(2, 2, source, 90, BigDecimal.ONE);

        source.set(0, new BigDecimal("20"));

        assertThat(parameters.recencyWeights()).containsExactly(BigDecimal.TEN, BigDecimal.ONE);
        assertThatThrownBy(() -> parameters.recencyWeights().add(BigDecimal.ONE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void acceptsMaxVariationOfOneAndMomentumWithParameters() {
        assertThatCode(official().withMaxVariation("1")::build).doesNotThrowAnyException();
        assertThatCode(momentum()::build).doesNotThrowAnyException();
    }

    @Test
    void weightsAreAnImmutableCopy() {
        Map<Position, Map<Metric, BigDecimal>> source = new HashMap<>();
        official().weights().forEach((position, row) -> source.put(position, new HashMap<>(row)));
        ValuationStrategyDefinition definition = official().withWeights(source).build();

        source.get(Position.FORWARD).put(Metric.GOALS, new BigDecimal("0.99"));

        assertThat(definition.weight(Position.FORWARD, Metric.GOALS)).isEqualByComparingTo("0.50");
        Map<Metric, BigDecimal> forward = definition.weights().get(Position.FORWARD);
        assertThatThrownBy(() -> forward.put(Metric.GOALS, BigDecimal.ONE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Nested
    class Fingerprint {

        @Test
        void isLowercaseSha256Hex() {
            assertThat(official().build().fingerprint()).matches("[0-9a-f]{64}");
        }

        @Test
        void ignoresTrailingZeros() {
            assertThat(official().withWeight(Position.FORWARD, Metric.GOALS, "0.10").build().fingerprint())
                    .isEqualTo(official().withWeight(Position.FORWARD, Metric.GOALS, "0.1").build().fingerprint());
        }

        @Test
        void ignoresInsertionOrderOfTheMaps() {
            Map<Position, Map<Metric, BigDecimal>> reversed = new LinkedHashMap<>();
            List<Position> positions = new ArrayList<>(List.of(Position.values()));
            Collections.reverse(positions);
            for (Position position : positions) {
                Map<Metric, BigDecimal> row = new LinkedHashMap<>();
                row.put(Metric.RATING, new BigDecimal("0.50"));
                row.put(Metric.GOALS, new BigDecimal("0.50"));
                reversed.put(position, row);
            }

            assertThat(official().withWeights(reversed).build().fingerprint())
                    .isEqualTo(official().build().fingerprint());
        }

        @Test
        void officialKeepsTheFormatWithoutMomentum() throws Exception {
            String canonical = "TEST|1|OFFICIAL|1|0.1|0.2|-|"
                    + "DEFENDER:GOALS=0.5;DEFENDER:RATING=0.5;FORWARD:GOALS=0.5;FORWARD:RATING=0.5;"
                    + "GOALKEEPER:GOALS=0.5;GOALKEEPER:RATING=0.5;MIDFIELDER:GOALS=0.5;MIDFIELDER:RATING=0.5";
            String expected = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));

            assertThat(official().build().fingerprint()).isEqualTo(expected);
        }

        @ParameterizedTest
        @MethodSource("singleChanges")
        void changesWhenAnyParameterChanges(UnaryOperator<Draft> change) {
            assertThat(change.apply(momentum()).build().fingerprint()).isNotEqualTo(momentum().build().fingerprint());
        }

        static Stream<Named<UnaryOperator<Draft>>> singleChanges() {
            return Stream.of(
                    Named.of("type", d -> d.withType(StrategyType.OFFICIAL).withMomentum(null)),
                    Named.of("baseValue", d -> d.withBaseValue("1.01")),
                    Named.of("scaleFactor", d -> d.withScaleFactor("0.11")),
                    Named.of("maxVariation", d -> d.withMaxVariation("0.21")),
                    Named.of("momentum.window", d -> d.withMomentum(params(4, 2, List.of("4", "3", "2", "1"), 90, "1.5"))),
                    Named.of("momentum.minHistory", d -> d.withMomentum(params(5, 3, List.of("5", "4", "3", "2", "1"), 90, "1.5"))),
                    Named.of("momentum.recencyWeights", d -> d.withMomentum(params(5, 2, List.of("6", "4", "3", "2", "1"), 90, "1.5"))),
                    Named.of("momentum.possibleMinutes", d -> d.withMomentum(params(5, 2, List.of("5", "4", "3", "2", "1"), 95, "1.5"))),
                    Named.of("momentum.ratingBand", d -> d.withMomentum(params(5, 2, List.of("5", "4", "3", "2", "1"), 90, "2"))),
                    Named.of("un peso", d -> d.withWeight(Position.DEFENDER, Metric.RATING, "0.51")));
        }
    }

    @Test
    void weightReturnsTheDeclaredValueAndRejectsUndeclaredMetrics() {
        ValuationStrategyDefinition definition = official().build();

        assertThat(definition.weight(Position.DEFENDER, Metric.RATING)).isEqualByComparingTo("0.50");
        assertThat(definition.metrics()).containsExactly(Metric.GOALS, Metric.RATING);
        assertThatThrownBy(() -> definition.weight(Position.DEFENDER, Metric.RED_CARDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RED_CARDS");
    }
}
