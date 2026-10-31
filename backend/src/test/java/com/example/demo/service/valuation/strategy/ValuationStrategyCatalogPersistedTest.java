package com.example.demo.service.valuation.strategy;

import com.example.demo.exception.UnknownValuationStrategyException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValuationStrategyCatalogPersistedTest {

    private static final List<ValuationStrategyDefinition> DEFINITIONS =
            List.of(OfficialStrategyV1.DEFINITION, MomentumStrategyV1.DEFINITION);

    @Test
    void persistedActiveCodeTakesPrecedenceOverTheProperty() {
        ValuationStrategyCatalog catalog = new ValuationStrategyCatalog(DEFINITIONS, "OFFICIAL",
                () -> Optional.of("MOMENTUM"));

        assertThat(catalog.active().code()).isEqualTo("MOMENTUM");
        assertThat(catalog.alternative().code()).isEqualTo("OFFICIAL");
        assertThat(catalog.propertyDefault().code()).isEqualTo("OFFICIAL");
    }

    @Test
    void withoutPersistedCodeThePropertyIsUsed() {
        ValuationStrategyCatalog catalog = new ValuationStrategyCatalog(DEFINITIONS, "MOMENTUM", Optional::empty);

        assertThat(catalog.active().code()).isEqualTo("MOMENTUM");
        assertThat(catalog.alternative().code()).isEqualTo("OFFICIAL");
    }

    @Test
    void activeIsReadOnEveryCallSoChangesApplyImmediately() {
        AtomicReference<String> stored = new AtomicReference<>();
        ValuationStrategyCatalog catalog = new ValuationStrategyCatalog(DEFINITIONS, "OFFICIAL",
                () -> Optional.ofNullable(stored.get()));

        assertThat(catalog.active().code()).isEqualTo("OFFICIAL");
        stored.set("MOMENTUM");
        assertThat(catalog.active().code()).isEqualTo("MOMENTUM");
    }

    @Test
    void anUnknownPersistedCodePreventsStartup() {
        ValuationStrategyCatalog catalog = new ValuationStrategyCatalog(DEFINITIONS, "OFFICIAL",
                () -> Optional.of("RETIRED"));

        assertThatThrownBy(catalog::afterSingletonsInstantiated)
                .isInstanceOf(UnknownValuationStrategyException.class);
    }

    @Test
    void alternativeRequiresExactlyTwoStrategies() {
        ValuationStrategyCatalog catalog = new ValuationStrategyCatalog(List.of(OfficialStrategyV1.DEFINITION),
                "OFFICIAL", Optional::empty);

        assertThatThrownBy(catalog::alternative).isInstanceOf(IllegalStateException.class);
    }
}
