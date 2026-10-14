package com.example.demo.service.valuation.strategy;

import com.example.demo.model.Position;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Definición inmutable y versionada de una estrategia de valuación. Los valores se declaran acá y los
 * interpretan los algoritmos (features 015 y 019). Una versión publicada no se modifica nunca: para cambiar
 * algo se crea una versión nueva (ver {@code specs/013-cotizacion-modelo/contracts/valuation-strategies.md} §5).
 *
 * @param weights  pesos por posición y métrica; las 4 posiciones declaran el mismo conjunto de métricas
 * @param momentum parámetros de forma reciente; obligatorio sólo para {@link StrategyType#MOMENTUM}
 */
public record ValuationStrategyDefinition(String code, int version, StrategyType type, BigDecimal baseValue,
                                          BigDecimal scaleFactor, BigDecimal maxVariation,
                                          Map<Position, Map<Metric, BigDecimal>> weights,
                                          MomentumParameters momentum) {

    public ValuationStrategyDefinition {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("La estrategia necesita un código");
        }
        String label = code + " v" + version;
        require(version >= 1, label, "la versión debe ser >= 1");
        require(type != null, label, "falta el tipo de algoritmo");
        require(isPositive(baseValue), label, "baseValue debe ser > 0");
        require(isPositive(scaleFactor), label, "scaleFactor debe ser > 0");
        require(isPositive(maxVariation) && maxVariation.compareTo(BigDecimal.ONE) <= 0, label,
                "maxVariation debe estar en (0, 1]");
        require((type == StrategyType.MOMENTUM) == (momentum != null), label,
                "los parámetros de MOMENTUM se declaran si y sólo si el tipo es MOMENTUM");
        weights = copyWeights(weights, label);
    }

    /** Peso declarado para la métrica en la posición; falla si la estrategia no pondera esa métrica. */
    public BigDecimal weight(Position position, Metric metric) {
        BigDecimal weight = weights.get(position).get(metric);
        if (weight == null) {
            throw new IllegalArgumentException(code + " v" + version + " no pondera " + metric);
        }
        return weight;
    }

    /** Métricas que pondera la estrategia (las mismas en todas las posiciones). */
    public Set<Metric> metrics() {
        return Collections.unmodifiableSet(EnumSet.copyOf(weights.get(Position.GOALKEEPER).keySet()));
    }

    /**
     * Huella SHA-256 (hexadecimal en minúscula) de todos los parámetros. Las posiciones y métricas se ordenan
     * por nombre y los números se normalizan ({@code 0.10} = {@code 0.1}), así que sólo cambia si cambia el
     * significado de la definición. La usa el test de protección de versiones publicadas.
     */
    public String fingerprint() {
        StringJoiner entries = new StringJoiner(";");
        weights.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().name()))
                .forEach(row -> row.getValue().entrySet().stream()
                        .sorted(Comparator.comparing(entry -> entry.getKey().name()))
                        .forEach(weight -> entries.add(row.getKey().name() + ":" + weight.getKey().name()
                                + "=" + normalize(weight.getValue()))));
        String canonical = String.join("|", code, String.valueOf(version), type.name(), normalize(baseValue),
                normalize(scaleFactor), normalize(maxVariation),
                momentum == null ? "-" : momentum.window() + "," + momentum.minHistory(),
                entries.toString());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static String normalize(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static Map<Position, Map<Metric, BigDecimal>> copyWeights(Map<Position, Map<Metric, BigDecimal>> source,
                                                                      String label) {
        require(source != null && source.keySet().containsAll(EnumSet.allOf(Position.class)), label,
                "debe declarar pesos para las 4 posiciones");
        Set<Metric> expected = source.get(Position.GOALKEEPER).keySet();
        require(!expected.isEmpty(), label, "debe ponderar al menos una métrica");
        Map<Position, Map<Metric, BigDecimal>> copy = new EnumMap<>(Position.class);
        for (Position position : Position.values()) {
            Map<Metric, BigDecimal> row = source.get(position);
            require(row.keySet().equals(expected) && !row.containsValue(null), label,
                    "todas las posiciones deben declarar las mismas métricas, sin pesos nulos (" + position + ")");
            copy.put(position, Collections.unmodifiableMap(new EnumMap<>(row)));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static void require(boolean condition, String label, String problem) {
        if (!condition) {
            throw new IllegalArgumentException("Estrategia " + label + " inválida: " + problem);
        }
    }
}
