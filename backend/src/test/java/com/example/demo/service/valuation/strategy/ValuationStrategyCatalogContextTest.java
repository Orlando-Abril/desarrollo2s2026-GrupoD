package com.example.demo.service.valuation.strategy;

import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** En el contexto real ningún {@code .properties} define la estrategia activa: rige el valor por defecto del código. */
@SpringBootTest
@UnitTestProfile
class ValuationStrategyCatalogContextTest {

    @Autowired
    private ValuationStrategyCatalog catalog;

    @Test
    void activeStrategyIsOfficialByDefault() {
        assertThat(catalog.active().code()).isEqualTo("OFFICIAL");
    }
}
