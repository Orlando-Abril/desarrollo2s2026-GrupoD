package com.example.demo.service.valuation.strategy;

import com.example.demo.exception.UnknownValuationStrategyException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ValuationStrategyActivePropertyTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(ValuationStrategyCatalog.class);

    @Test
    void defaultsToOfficialWithoutProperty() {
        runner.run(context -> assertThat(context.getBean(ValuationStrategyCatalog.class).active())
                .isSameAs(OfficialStrategyV1.DEFINITION));
    }

    @Test
    void propertySelectsMomentumAndKeepsBothStrategies() {
        runner.withPropertyValues("valuation.active-strategy=MOMENTUM").run(context -> {
            ValuationStrategyCatalog catalog = context.getBean(ValuationStrategyCatalog.class);
            assertThat(catalog.active()).isSameAs(MomentumStrategyV1.DEFINITION);
            assertThat(catalog.all()).hasSize(2);
        });
    }

    @Test
    void surroundingSpacesAreIgnored() {
        runner.withPropertyValues("valuation.active-strategy= MOMENTUM ").run(context ->
                assertThat(context.getBean(ValuationStrategyCatalog.class).active())
                        .isSameAs(MomentumStrategyV1.DEFINITION));
    }

    @Test
    void unknownCodePreventsStartup() {
        runner.withPropertyValues("valuation.active-strategy=XYZ").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(UnknownValuationStrategyException.class)
                    .hasMessageContaining("'XYZ'")
                    .hasMessageContaining("OFFICIAL, MOMENTUM");
        });
    }
}
