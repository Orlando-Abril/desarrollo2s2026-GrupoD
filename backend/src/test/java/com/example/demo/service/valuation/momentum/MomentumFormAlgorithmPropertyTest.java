package com.example.demo.service.valuation.momentum;

import com.example.demo.model.PlayerQuote;
import com.example.demo.model.PlayerStats;
import com.example.demo.model.Position;
import com.example.demo.model.QuoteStatus;
import com.example.demo.service.valuation.QuoteResult;
import com.example.demo.service.valuation.ValuationInput;
import com.example.demo.service.valuation.strategy.MomentumStrategyV1;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static com.example.demo.service.valuation.momentum.MomentumFixtures.input;
import static com.example.demo.service.valuation.momentum.MomentumFixtures.momentumQuote;
import static com.example.demo.service.valuation.momentum.MomentumFixtures.season;
import static com.example.demo.service.valuation.momentum.MomentumFixtures.window;
import static com.example.demo.service.valuation.momentum.MomentumFixtures.windows;
import static org.assertj.core.api.Assertions.assertThat;

/** Para entradas generadas con semilla fija: score en [0, 1], variación dentro de ±15% y resultado determinístico. */
class MomentumFormAlgorithmPropertyTest {

    private static final MomentumFormAlgorithm ALGORITHM = new MomentumFormAlgorithm();
    private static final BigDecimal MAX_VARIATION = new BigDecimal("0.15");
    private static final BigDecimal ROUNDING = new BigDecimal("0.00005");

    private final Random random = new Random(19);

    @Test
    void scoreAndVariationStayWithinBounds() {
        for (int i = 0; i < 500; i++) {
            ValuationInput input = randomInput();

            QuoteResult result = ALGORITHM.calculate(input, MomentumStrategyV1.DEFINITION);

            assertThat(ALGORITHM.calculate(input, MomentumStrategyV1.DEFINITION)).isEqualTo(result);
            assertThat(result.status()).isEqualTo(QuoteStatus.CALCULATED);
            assertThat(result.score()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
            BigDecimal previous = input.previousQuotes().isEmpty() ? BigDecimal.ONE
                    : input.previousQuotes().get(0).getValue();
            BigDecimal limit = previous.multiply(MAX_VARIATION).add(ROUNDING);
            assertThat(result.value().subtract(previous.setScale(4, RoundingMode.HALF_UP)).abs())
                    .as("caso %d", i).isLessThanOrEqualTo(limit);
        }
    }

    private ValuationInput randomInput() {
        List<MomentumFixtures.Spec> specs = new ArrayList<>();
        int count = 1 + random.nextInt(7);
        for (int i = 0; i < count; i++) {
            specs.add(window(nullable(181), nullable(6), nullable(6), nullable(6), nullable(6), nullable(6),
                    nullable(6), random.nextInt(8) == 0 ? null : rating(), random.nextInt(3), random.nextInt(3)));
        }
        PlayerStats stats = random.nextInt(8) == 0 ? null
                : season(nullable(3000), nullable(30), nullable(20), nullable(80), nullable(60), nullable(60),
                nullable(80), random.nextInt(8) == 0 ? null : rating());
        List<PlayerQuote> quotes = random.nextBoolean() ? List.of()
                : List.of(momentumQuote(BigDecimal.valueOf(50 + random.nextInt(251), 2).toPlainString()));
        return input(positions(), stats, quotes, windows(specs));
    }

    private Integer nullable(int bound) {
        return random.nextInt(8) == 0 ? null : random.nextInt(bound);
    }

    private String rating() {
        return BigDecimal.valueOf(400 + random.nextInt(601), 2).toPlainString();
    }

    private Set<Position> positions() {
        Set<Position> positions = EnumSet.noneOf(Position.class);
        for (Position position : Position.values()) {
            if (random.nextBoolean()) {
                positions.add(position);
            }
        }
        if (positions.isEmpty()) {
            positions.add(Position.values()[random.nextInt(Position.values().length)]);
        }
        return positions;
    }
}
