package com.example.demo.service.valuation.official;

import com.example.demo.model.Position;
import com.example.demo.model.QuoteStatus;
import com.example.demo.service.valuation.NormalizedPopulation;
import com.example.demo.service.valuation.PopulationEntry;
import com.example.demo.service.valuation.ValuationInput;
import com.example.demo.service.valuation.strategy.OfficialStrategyV1;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static com.example.demo.service.valuation.official.OfficialFixtures.entry;
import static com.example.demo.service.valuation.official.OfficialFixtures.stats;
import static org.assertj.core.api.Assertions.assertThat;

class OfficialMinMinutesPropertyTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(OfficialPerformanceAlgorithm.class);

    @Test
    void defaultsTo90WithoutProperty() {
        runner.run(context -> assertThat(context.getBean(OfficialPerformanceAlgorithm.class).normalize(List.of())
                .minMinutes()).isEqualTo(90));
    }

    @Test
    void propertyChangesTheThreshold() {
        runner.withPropertyValues("valuation.official.min-minutes=270").run(context -> {
            OfficialPerformanceAlgorithm algorithm = context.getBean(OfficialPerformanceAlgorithm.class);
            PopulationEntry player = entry(1, stats(200, 1, 1, 1, 1, 1, 1, "7.0"), Position.FORWARD);
            NormalizedPopulation population = algorithm.normalize(List.of(player));

            assertThat(population.minMinutes()).isEqualTo(270);
            assertThat(algorithm.calculate(new ValuationInput(player.playerId(), player.positions(), player.stats(),
                    population, List.of(), List.of()), OfficialStrategyV1.DEFINITION).status())
                    .isEqualTo(QuoteStatus.NO_STATS);
        });
    }

    @Test
    void nonPositiveThresholdPreventsStartup() {
        for (String value : new String[]{"0", "-5"}) {
            runner.withPropertyValues("valuation.official.min-minutes=" + value).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .rootCause().hasMessageContaining("min-minutes");
            });
        }
    }
}
