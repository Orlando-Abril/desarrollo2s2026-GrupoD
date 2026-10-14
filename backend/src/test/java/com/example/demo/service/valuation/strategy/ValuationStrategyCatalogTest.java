package com.example.demo.service.valuation.strategy;

import com.example.demo.exception.UnknownValuationStrategyException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValuationStrategyCatalogTest {

    private final ValuationStrategyCatalog catalog = new ValuationStrategyCatalog("OFFICIAL");

    @Test
    void allReturnsBothStrategiesInOrderAsAnUnmodifiableList() {
        assertThat(catalog.all()).containsExactly(OfficialStrategyV1.DEFINITION, MomentumStrategyV1.DEFINITION);
        assertThatThrownBy(() -> catalog.all().add(OfficialStrategyV1.DEFINITION))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void findsEachStrategyByCode() {
        assertThat(catalog.find("OFFICIAL")).isSameAs(OfficialStrategyV1.DEFINITION);
        assertThat(catalog.find("MOMENTUM")).isSameAs(MomentumStrategyV1.DEFINITION);
    }

    @Test
    void unknownCodeNamesTheRequestedAndTheValidCodes() {
        assertThatThrownBy(() -> catalog.find("XYZ"))
                .isInstanceOf(UnknownValuationStrategyException.class)
                .hasMessage("Estrategia de valuación desconocida: 'XYZ'. Códigos válidos: OFFICIAL, MOMENTUM");
    }

    @Test
    void rejectsDuplicatedCodes() {
        List<ValuationStrategyDefinition> duplicated = List.of(OfficialStrategyV1.DEFINITION, OfficialStrategyV1.DEFINITION);

        assertThatThrownBy(() -> new ValuationStrategyCatalog(duplicated, "OFFICIAL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OFFICIAL");
    }
}
