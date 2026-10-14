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

/**
 * OFFICIAL v1 (contracts/valuation-strategies.md §2 y §3). MIDFIELDER es exactamente la fórmula de la consigna:
 * {@code 0.25·goals + 0.15·assists + 0.10·shots + 0.10·keyPasses + 0.10·dribbles + 0.10·tackles + 0.20·rating};
 * las demás posiciones redistribuyen esos pesos, siempre no negativos y sumando 1.00.
 */
public final class OfficialStrategyV1 {

    public static final ValuationStrategyDefinition DEFINITION = new ValuationStrategyDefinition(
            "OFFICIAL", 1, StrategyType.OFFICIAL,
            new BigDecimal("1.00"), new BigDecimal("0.10"), new BigDecimal("0.20"),
            Map.of(
                    //                           goals   assists shots   keyPass dribble tackles rating
                    Position.GOALKEEPER, weights("0.00", "0.05", "0.00", "0.10", "0.00", "0.15", "0.70"),
                    Position.DEFENDER,   weights("0.10", "0.10", "0.05", "0.10", "0.05", "0.30", "0.30"),
                    Position.MIDFIELDER, weights("0.25", "0.15", "0.10", "0.10", "0.10", "0.10", "0.20"),
                    Position.FORWARD,    weights("0.30", "0.15", "0.15", "0.05", "0.10", "0.05", "0.20")),
            null);

    private OfficialStrategyV1() {
    }

    private static Map<Metric, BigDecimal> weights(String goals, String assists, String shots, String keyPasses,
                                                   String dribbles, String tackles, String rating) {
        Map<Metric, BigDecimal> row = new EnumMap<>(Metric.class);
        row.put(GOALS, new BigDecimal(goals));
        row.put(ASSISTS, new BigDecimal(assists));
        row.put(SHOTS, new BigDecimal(shots));
        row.put(KEY_PASSES, new BigDecimal(keyPasses));
        row.put(DRIBBLES, new BigDecimal(dribbles));
        row.put(TACKLES, new BigDecimal(tackles));
        row.put(RATING, new BigDecimal(rating));
        return row;
    }
}
