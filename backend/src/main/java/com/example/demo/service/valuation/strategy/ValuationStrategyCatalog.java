package com.example.demo.service.valuation.strategy;

import com.example.demo.exception.UnknownValuationStrategyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Catálogo de las estrategias de valuación definidas en código. {@link #all()} devuelve siempre todas: las dos
 * se calculan y guardan en cada corrida (feature 016). La estrategia activa sólo decide el precio de mercado;
 * se elige con {@code valuation.active-strategy} (por defecto {@code OFFICIAL}, sin declararla en ningún
 * {@code .properties}) y se puede sobrescribir con {@code VALUATION_ACTIVE_STRATEGY} o
 * {@code -Dvaluation.active-strategy}. Un código inexistente impide arrancar la aplicación.
 */
@Component
public class ValuationStrategyCatalog {

    private final List<ValuationStrategyDefinition> definitions;
    private final ValuationStrategyDefinition active;

    @Autowired
    public ValuationStrategyCatalog(@Value("${valuation.active-strategy:OFFICIAL}") String activeCode) {
        this(List.of(OfficialStrategyV1.DEFINITION, MomentumStrategyV1.DEFINITION), activeCode);
    }

    ValuationStrategyCatalog(List<ValuationStrategyDefinition> definitions, String activeCode) {
        this.definitions = List.copyOf(definitions);
        Set<String> codes = new HashSet<>();
        for (ValuationStrategyDefinition definition : this.definitions) {
            if (!codes.add(definition.code())) {
                throw new IllegalStateException("Código de estrategia duplicado en el catálogo: " + definition.code());
            }
        }
        this.active = find(activeCode.trim());
    }

    public List<ValuationStrategyDefinition> all() {
        return definitions;
    }

    public ValuationStrategyDefinition find(String code) {
        return definitions.stream()
                .filter(definition -> definition.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new UnknownValuationStrategyException(code,
                        definitions.stream().map(ValuationStrategyDefinition::code).toList()));
    }

    /** Estrategia que define el precio de mercado. */
    public ValuationStrategyDefinition active() {
        return active;
    }
}
