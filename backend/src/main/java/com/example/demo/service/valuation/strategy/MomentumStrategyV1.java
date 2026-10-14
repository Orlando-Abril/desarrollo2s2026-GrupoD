package com.example.demo.service.valuation.strategy;

import com.example.demo.model.Position;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static com.example.demo.service.valuation.strategy.Metric.ASSISTS;
import static com.example.demo.service.valuation.strategy.Metric.DRIBBLES;
import static com.example.demo.service.valuation.strategy.Metric.GOALS;
import static com.example.demo.service.valuation.strategy.Metric.KEY_PASSES;
import static com.example.demo.service.valuation.strategy.Metric.RATING;
import static com.example.demo.service.valuation.strategy.Metric.SHOTS;
import static com.example.demo.service.valuation.strategy.Metric.TACKLES;
import static com.example.demo.service.valuation.strategy.Metric.MINUTES;
import static com.example.demo.service.valuation.strategy.Metric.RED_CARDS;
import static com.example.demo.service.valuation.strategy.Metric.YELLOW_CARDS;

/**
 * MOMENTUM v1 (contracts/valuation-strategies.md §2 y §4): forma reciente sobre las últimas 4 cotizaciones
 * (mínimo 2), con premio a la disponibilidad (minutos) y penalización por tarjetas (pesos negativos, mayor
 * para la roja). En cada posición los pesos positivos suman 1.00.
 */
public final class MomentumStrategyV1 {

    public static final ValuationStrategyDefinition DEFINITION = new ValuationStrategyDefinition(
            "MOMENTUM", 1, StrategyType.MOMENTUM,
            new BigDecimal("1.00"), new BigDecimal("0.15"), new BigDecimal("0.30"),
            Map.of(
                    //                           goals   assists shots   keyPass dribble tackles rating  minutes yellow   red
                    Position.GOALKEEPER, weights("0.00", "0.00", "0.00", "0.05", "0.00", "0.05", "0.60", "0.30", "-0.05", "-0.20"),
                    Position.DEFENDER,   weights("0.05", "0.05", "0.00", "0.10", "0.05", "0.30", "0.25", "0.20", "-0.10", "-0.20"),
                    Position.MIDFIELDER, weights("0.20", "0.15", "0.05", "0.15", "0.10", "0.10", "0.15", "0.10", "-0.05", "-0.15"),
                    Position.FORWARD,    weights("0.30", "0.15", "0.10", "0.05", "0.10", "0.00", "0.20", "0.10", "-0.05", "-0.15")),
            new MomentumParameters(4, 2));

    private MomentumStrategyV1() {
    }

    @SuppressWarnings("java:S107") // una columna por métrica: la tabla de pesos se lee igual que en el contrato
    private static Map<Metric, BigDecimal> weights(String goals, String assists, String shots, String keyPasses,
                                                   String dribbles, String tackles, String rating, String minutes,
                                                   String yellowCards, String redCards) {
        Map<Metric, BigDecimal> row = new EnumMap<>(Metric.class);
        row.put(GOALS, new BigDecimal(goals));
        row.put(ASSISTS, new BigDecimal(assists));
        row.put(SHOTS, new BigDecimal(shots));
        row.put(KEY_PASSES, new BigDecimal(keyPasses));
        row.put(DRIBBLES, new BigDecimal(dribbles));
        row.put(TACKLES, new BigDecimal(tackles));
        row.put(RATING, new BigDecimal(rating));
        row.put(MINUTES, new BigDecimal(minutes));
        row.put(YELLOW_CARDS, new BigDecimal(yellowCards));
        row.put(RED_CARDS, new BigDecimal(redCards));
        return row;
    }
}
