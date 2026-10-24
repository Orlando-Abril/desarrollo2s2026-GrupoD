package com.example.demo.service.valuation.official;

import com.example.demo.model.PlayerQuote;
import com.example.demo.model.Position;
import com.example.demo.service.valuation.MetricNormalizer;
import com.example.demo.service.valuation.NormalizedPopulation;
import com.example.demo.service.valuation.PopulationEntry;
import com.example.demo.service.valuation.QuoteResult;
import com.example.demo.service.valuation.ValuationAlgorithm;
import com.example.demo.service.valuation.ValuationInput;
import com.example.demo.service.valuation.strategy.Metric;
import com.example.demo.service.valuation.strategy.StrategyType;
import com.example.demo.service.valuation.strategy.ValuationStrategyDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Estrategia 1 "OFFICIAL": performance ponderada por posición según la fórmula de la consigna (contrato en
 * {@code specs/015-estrategia-oficial/contracts/valuation-strategies.md}). Por posición,
 * {@code score_p = Σ peso'(p, m) × normalizado(m)}, donde los pesos de métricas sin dato en toda la posición se
 * reparten proporcionalmente; el score es el promedio de las posiciones del jugador, redondeado a 4 decimales, y
 * el valor {@code baseValue + score × scaleFactor}, acotado a ±{@code maxVariation} de la cotización vigente y
 * redondeado a 4 decimales HALF_UP.
 * <p>
 * El umbral de minutos ({@code valuation.official.min-minutes}, 90 por defecto) se usa para armar la población;
 * dentro de {@link #calculate} el corte del jugador sale de {@link NormalizedPopulation#minMinutes()}.
 */
@Component
public class OfficialPerformanceAlgorithm implements ValuationAlgorithm {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final int SCALE = 4;

    private final int minMinutes;

    public OfficialPerformanceAlgorithm(@Value("${valuation.official.min-minutes:90}") int minMinutes) {
        if (minMinutes <= 0) {
            throw new IllegalArgumentException("valuation.official.min-minutes debe ser > 0: " + minMinutes);
        }
        this.minMinutes = minMinutes;
    }

    @Override
    public StrategyType type() {
        return StrategyType.OFFICIAL;
    }

    /** Arma la población de la corrida con el umbral configurado. Se llama una vez por corrida. */
    public NormalizedPopulation normalize(Collection<PopulationEntry> entries) {
        return new MetricNormalizer(minMinutes).normalize(entries);
    }

    @Override
    public NormalizedPopulation preparePopulation(Collection<PopulationEntry> entries) {
        return normalize(entries);
    }

    @Override
    public QuoteResult calculate(ValuationInput input, ValuationStrategyDefinition strategy) {
        validate(input, strategy);
        NormalizedPopulation population = input.population();
        if (!MetricNormalizer.isEligible(input.positions(), input.stats(), population.minMinutes())
                || !population.contains(input.playerId())) {
            return QuoteResult.noStats();
        }

        Map<Position, Set<Metric>> excluded = new EnumMap<>(Position.class);
        BigDecimal sum = BigDecimal.ZERO;
        int scored = 0;
        for (Position position : Position.values()) {
            if (!input.positions().contains(position)) {
                continue;
            }
            Set<Metric> positionExcluded = excludedMetrics(strategy, population, position);
            if (!positionExcluded.isEmpty()) {
                excluded.put(position, positionExcluded);
            }
            BigDecimal positionScore = positionScore(strategy, population.normalized(input.playerId(), position),
                    position, positionExcluded);
            if (positionScore != null) {
                sum = sum.add(positionScore);
                scored++;
            }
        }
        if (scored == 0) {
            return QuoteResult.noStats();
        }

        BigDecimal average = sum.divide(BigDecimal.valueOf(scored), MC);
        BigDecimal score = clampToUnit(average).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal raw = strategy.baseValue().add(score.multiply(strategy.scaleFactor()));
        if (!input.previousQuotes().isEmpty()) {
            raw = cap(raw, input.previousQuotes().get(0).getValue(), strategy.maxVariation());
        }
        return QuoteResult.calculated(raw.setScale(SCALE, RoundingMode.HALF_UP), score, excluded);
    }

    /** Contrato de entrada: no depende del jugador, así que falla antes de evaluar NO_STATS. */
    private void validate(ValuationInput input, ValuationStrategyDefinition strategy) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");
        if (strategy.type() != StrategyType.OFFICIAL) {
            throw new IllegalArgumentException("OfficialPerformanceAlgorithm no calcula estrategias " + strategy.type()
                    + " (" + strategy.code() + " v" + strategy.version() + ")");
        }
        if (input.population() == null) {
            throw new IllegalArgumentException("OFFICIAL necesita la población normalizada (jugador "
                    + input.playerId() + ")");
        }
        if (input.population().minMinutes() != minMinutes) {
            throw new IllegalArgumentException("La población se armó con umbral " + input.population().minMinutes()
                    + " y el algoritmo usa " + minMinutes);
        }
        validatePreviousQuotes(input, strategy);
    }

    private static void validatePreviousQuotes(ValuationInput input, ValuationStrategyDefinition strategy) {
        List<PlayerQuote> previous = input.previousQuotes();
        for (PlayerQuote quote : previous) {
            if (!strategy.code().equals(quote.getStrategyCode())) {
                throw new IllegalArgumentException("La cotización previa del jugador " + input.playerId() + " es de "
                        + quote.getStrategyCode() + " y se está calculando " + strategy.code());
            }
        }
        if (!previous.isEmpty() && previous.get(0).getValue() == null) {
            throw new IllegalArgumentException("La cotización anterior del jugador " + input.playerId()
                    + " no tiene valor; 016 debe guardar NO_STATS con el precio conservado");
        }
    }

    /** Métricas con peso &gt; 0 que ningún jugador de la posición tiene informadas. */
    private static Set<Metric> excludedMetrics(ValuationStrategyDefinition strategy, NormalizedPopulation population,
                                               Position position) {
        Set<Metric> excluded = EnumSet.noneOf(Metric.class);
        for (Metric metric : population.metricsWithoutData(position)) {
            if (strategy.metrics().contains(metric) && strategy.weight(position, metric).signum() > 0) {
                excluded.add(metric);
            }
        }
        return excluded;
    }

    /** Score de la posición con los pesos repartidos; {@code null} si no queda ninguna métrica con peso. */
    private static BigDecimal positionScore(ValuationStrategyDefinition strategy, Map<Metric, BigDecimal> normalized,
                                            Position position, Set<Metric> excluded) {
        BigDecimal remaining = BigDecimal.ZERO;
        for (Metric metric : strategy.metrics()) {
            if (!excluded.contains(metric)) {
                remaining = remaining.add(strategy.weight(position, metric));
            }
        }
        if (remaining.signum() == 0) {
            return null;
        }
        BigDecimal score = BigDecimal.ZERO;
        for (Metric metric : strategy.metrics()) {
            if (excluded.contains(metric)) {
                continue;
            }
            BigDecimal value = normalized.get(metric);
            if (value == null) {
                throw new IllegalArgumentException("OFFICIAL no sabe normalizar la métrica " + metric);
            }
            score = score.add(strategy.weight(position, metric).divide(remaining, MC).multiply(value));
        }
        return score;
    }

    private static BigDecimal cap(BigDecimal raw, BigDecimal previous, BigDecimal maxVariation) {
        BigDecimal lower = previous.multiply(BigDecimal.ONE.subtract(maxVariation));
        BigDecimal upper = previous.multiply(BigDecimal.ONE.add(maxVariation));
        return raw.max(lower).min(upper);
    }

    private static BigDecimal clampToUnit(BigDecimal value) {
        return value.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }
}
