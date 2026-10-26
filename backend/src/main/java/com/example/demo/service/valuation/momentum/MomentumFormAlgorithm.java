package com.example.demo.service.valuation.momentum;

import com.example.demo.model.PlayerQuote;
import com.example.demo.model.PlayerStats;
import com.example.demo.model.Position;
import com.example.demo.service.valuation.QuoteResult;
import com.example.demo.service.valuation.StatsWindow;
import com.example.demo.service.valuation.ValuationAlgorithm;
import com.example.demo.service.valuation.ValuationInput;
import com.example.demo.service.valuation.strategy.Metric;
import com.example.demo.service.valuation.strategy.MomentumParameters;
import com.example.demo.service.valuation.strategy.StrategyType;
import com.example.demo.service.valuation.strategy.ValuationStrategyDefinition;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Estrategia 2 "MOMENTUM": la cotización sigue la forma reciente del jugador (contrato en
 * {@code specs/019-estrategia-momentum/contracts/momentum-algorithm.md}). Toma las últimas {@code window} ventanas
 * de estadísticas (feature 014), descartando las anteriores a un reinicio de temporada, y compara cada una contra
 * el promedio de temporada del propio jugador: los contadores por 90' valen {@code min(ratio / 2, 1)} y el rating
 * {@code 0.5 ± diferencia / (2 × ratingBand)}. Con los pesos de la posición (promedio si tiene varias):
 * <pre>
 * R = Σ ρ·a·r / Σ ρ·a          rendimiento de las ventanas jugadas (0.5 si no jugó ninguna)
 * D = Σ ρ·a / Σ ρ              disponibilidad
 * C = Σ (ρ / ρ₀)·(|w_Y|·amarillas + |w_R|·rojas)
 * S = clamp(R − w_MIN·(1 − D) − C, 0, 1)                         → score
 * v = clamp(2·(S − 0.5)·scaleFactor, ±maxVariation), o 0 sin datos nuevos
 * valor = round4(anterior × (1 + v))
 * </pre>
 * {@code ρ} son los pesos de recencia y {@code a = min(minutos / possibleMinutes, 1)}. Hay datos nuevos si el
 * snapshot más reciente es de otra corrida que la última cotización MOMENTUM y posterior a ella.
 */
@Component
public class MomentumFormAlgorithm implements ValuationAlgorithm {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final int SCALE = 4;
    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final List<Metric> COUNTERS = List.of(Metric.GOALS, Metric.ASSISTS, Metric.SHOTS,
            Metric.KEY_PASSES, Metric.DRIBBLES, Metric.TACKLES);

    @Override
    public StrategyType type() {
        return StrategyType.MOMENTUM;
    }

    @Override
    public QuoteResult calculate(ValuationInput input, ValuationStrategyDefinition strategy) {
        validate(input, strategy);
        if (input.positions().isEmpty()) {
            return QuoteResult.noStats();
        }
        MomentumParameters parameters = strategy.momentum();
        if (input.windows().size() < parameters.minHistory() - 1) {
            return QuoteResult.insufficientHistory();
        }

        List<StatsWindow> windows = selectWindows(input.windows());
        StatsWindow latest = windows.get(0);
        List<StatsWindow> considered = windows.subList(0, Math.min(parameters.window(), windows.size()));
        BigDecimal form = form(considered, input.stats(), averagedWeights(input, strategy), parameters);

        List<PlayerQuote> previousQuotes = input.previousQuotes();
        BigDecimal previous = previousQuotes.isEmpty() ? strategy.baseValue() : previousQuotes.get(0).getValue();
        BigDecimal variation = hasNewData(latest, previousQuotes) ? variation(form, strategy) : BigDecimal.ZERO;
        BigDecimal value = previous.multiply(BigDecimal.ONE.add(variation), MC).setScale(SCALE, RoundingMode.HALF_UP);
        return QuoteResult.calculated(value, form.setScale(SCALE, RoundingMode.HALF_UP), Map.of());
    }

    /** Contrato de entrada: no depende del jugador, así que falla antes de evaluar los estados. */
    private static void validate(ValuationInput input, ValuationStrategyDefinition strategy) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");
        if (strategy.type() != StrategyType.MOMENTUM) {
            throw new IllegalArgumentException("MomentumFormAlgorithm no calcula estrategias " + strategy.type()
                    + " (" + strategy.code() + " v" + strategy.version() + ")");
        }
        List<PlayerQuote> previous = input.previousQuotes();
        for (PlayerQuote quote : previous) {
            if (!strategy.code().equals(quote.getStrategyCode())) {
                throw new IllegalArgumentException("La cotización previa del jugador " + input.playerId() + " es de "
                        + quote.getStrategyCode() + " y se está calculando " + strategy.code());
            }
        }
        if (!previous.isEmpty() && previous.get(0).getValue() == null) {
            throw new IllegalArgumentException("La cotización anterior del jugador " + input.playerId()
                    + " no tiene valor; 016 debe guardar el precio conservado");
        }
    }

    /** De la más reciente a la más vieja, sin las anteriores a la ventana de reinicio de temporada más reciente. */
    private static List<StatsWindow> selectWindows(List<StatsWindow> windows) {
        List<StatsWindow> newestFirst = new ArrayList<>(windows);
        newestFirst.sort(Comparator.comparing(StatsWindow::toCapturedAt)
                .thenComparing(StatsWindow::currentSnapshotId).reversed());
        for (int i = 0; i < newestFirst.size(); i++) {
            if (newestFirst.get(i).seasonReset()) {
                return newestFirst.subList(0, i + 1);
            }
        }
        return newestFirst;
    }

    private static boolean hasNewData(StatsWindow latest, List<PlayerQuote> previousQuotes) {
        if (previousQuotes.isEmpty()) {
            return true;
        }
        PlayerQuote previous = previousQuotes.get(0);
        return !latest.currentRunId().equals(previous.getRunId())
                && latest.toCapturedAt().isAfter(previous.getCalculatedAt());
    }

    private static Map<Metric, BigDecimal> averagedWeights(ValuationInput input, ValuationStrategyDefinition strategy) {
        BigDecimal count = BigDecimal.valueOf(input.positions().size());
        Map<Metric, BigDecimal> averaged = new EnumMap<>(Metric.class);
        for (Metric metric : strategy.metrics()) {
            BigDecimal sum = BigDecimal.ZERO;
            for (Position position : input.positions()) {
                sum = sum.add(strategy.weight(position, metric));
            }
            averaged.put(metric, sum.divide(count, MC));
        }
        return averaged;
    }

    private static BigDecimal form(List<StatsWindow> windows, PlayerStats stats, Map<Metric, BigDecimal> weights,
                                   MomentumParameters parameters) {
        BigDecimal newestWeight = parameters.recencyWeights().get(0);
        BigDecimal yellowWeight = weight(weights, Metric.YELLOW_CARDS).abs();
        BigDecimal redWeight = weight(weights, Metric.RED_CARDS).abs();
        BigDecimal performance = BigDecimal.ZERO;
        BigDecimal playedWeight = BigDecimal.ZERO;
        BigDecimal recencyTotal = BigDecimal.ZERO;
        BigDecimal cards = BigDecimal.ZERO;
        for (int j = 0; j < windows.size(); j++) {
            StatsWindow window = windows.get(j);
            BigDecimal recency = parameters.recencyWeights().get(j);
            BigDecimal availability = availability(window, parameters);
            recencyTotal = recencyTotal.add(recency);
            playedWeight = playedWeight.add(recency.multiply(availability, MC));
            BigDecimal booked = yellowWeight.multiply(count(window.yellowCards()))
                    .add(redWeight.multiply(count(window.redCards())));
            cards = cards.add(recency.divide(newestWeight, MC).multiply(booked, MC));
            if (availability.signum() > 0) {
                performance = performance.add(recency.multiply(availability, MC)
                        .multiply(windowPerformance(window, stats, weights, parameters), MC));
            }
        }
        BigDecimal recent = playedWeight.signum() > 0 ? performance.divide(playedWeight, MC) : HALF;
        BigDecimal available = playedWeight.divide(recencyTotal, MC);
        BigDecimal absence = weight(weights, Metric.MINUTES).multiply(BigDecimal.ONE.subtract(available), MC);
        return clampToUnit(recent.subtract(absence).subtract(cards));
    }

    private static BigDecimal availability(StatsWindow window, MomentumParameters parameters) {
        int minutes = count(window.minutesPlayed()).intValue();
        if (minutes <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(parameters.possibleMinutes()), MC)
                .min(BigDecimal.ONE);
    }

    /** Rendimiento de una ventana jugada contra el promedio de temporada; 0.5 si no hay métricas con dato. */
    private static BigDecimal windowPerformance(StatsWindow window, PlayerStats stats, Map<Metric, BigDecimal> weights,
                                                MomentumParameters parameters) {
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (Metric metric : COUNTERS) {
            BigDecimal weight = weight(weights, metric);
            BigDecimal score = weight.signum() > 0 ? counterScore(window, stats, metric) : null;
            if (score != null) {
                sum = sum.add(weight.multiply(score, MC));
                total = total.add(weight);
            }
        }
        BigDecimal ratingWeight = weight(weights, Metric.RATING);
        BigDecimal rating = ratingWeight.signum() > 0 ? ratingScore(window, stats, parameters) : null;
        if (rating != null) {
            sum = sum.add(ratingWeight.multiply(rating, MC));
            total = total.add(ratingWeight);
        }
        return total.signum() > 0 ? sum.divide(total, MC) : HALF;
    }

    /** {@code min(ratio / 2, 1)} del contador por minuto contra el de temporada; null si falta el dato. */
    private static BigDecimal counterScore(StatsWindow window, PlayerStats stats, Metric metric) {
        Integer windowValue = windowValue(window, metric);
        if (windowValue == null || stats == null || stats.getMinutesPlayed() == null || stats.getMinutesPlayed() <= 0) {
            return null;
        }
        Integer seasonValue = seasonValue(stats, metric);
        if (seasonValue == null || seasonValue <= 0) {
            return null;
        }
        BigDecimal ratio = BigDecimal.valueOf((long) windowValue * stats.getMinutesPlayed())
                .divide(BigDecimal.valueOf((long) window.minutesPlayed() * seasonValue), MC);
        return ratio.divide(TWO, MC).min(BigDecimal.ONE);
    }

    private static BigDecimal ratingScore(StatsWindow window, PlayerStats stats, MomentumParameters parameters) {
        if (window.rating() == null || stats == null || stats.getRating() == null || stats.getRating().signum() <= 0) {
            return null;
        }
        BigDecimal difference = window.rating().subtract(stats.getRating());
        return clampToUnit(HALF.add(difference.divide(TWO.multiply(parameters.ratingBand()), MC)));
    }

    private static Integer windowValue(StatsWindow window, Metric metric) {
        return switch (metric) {
            case GOALS -> window.goals();
            case ASSISTS -> window.assists();
            case SHOTS -> window.shots();
            case KEY_PASSES -> window.keyPasses();
            case DRIBBLES -> window.dribbles();
            case TACKLES -> window.tackles();
            default -> throw new IllegalArgumentException("MOMENTUM no compara " + metric + " como contador");
        };
    }

    private static Integer seasonValue(PlayerStats stats, Metric metric) {
        return switch (metric) {
            case GOALS -> stats.getGoals();
            case ASSISTS -> stats.getAssists();
            case SHOTS -> stats.getShots();
            case KEY_PASSES -> stats.getKeyPasses();
            case DRIBBLES -> stats.getDribbles();
            case TACKLES -> stats.getTackles();
            default -> throw new IllegalArgumentException("MOMENTUM no compara " + metric + " como contador");
        };
    }

    private static BigDecimal variation(BigDecimal form, ValuationStrategyDefinition strategy) {
        BigDecimal raw = TWO.multiply(form.subtract(HALF), MC).multiply(strategy.scaleFactor(), MC);
        return raw.max(strategy.maxVariation().negate()).min(strategy.maxVariation());
    }

    private static BigDecimal weight(Map<Metric, BigDecimal> weights, Metric metric) {
        return weights.getOrDefault(metric, BigDecimal.ZERO);
    }

    private static BigDecimal count(Integer value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }

    private static BigDecimal clampToUnit(BigDecimal value) {
        return value.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }
}
