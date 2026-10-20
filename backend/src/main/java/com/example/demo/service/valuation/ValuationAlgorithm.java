package com.example.demo.service.valuation;

import com.example.demo.service.valuation.strategy.StrategyType;
import com.example.demo.service.valuation.strategy.ValuationStrategyDefinition;

/**
 * Algoritmo que interpreta una definición de estrategia y valúa a un jugador. La corrida de recálculo
 * (feature 016) elige la implementación según {@code definition.type()}: OFFICIAL (feature 015) y MOMENTUM
 * (feature 019) implementan esta misma interfaz con el mismo {@link ValuationInput}.
 */
public interface ValuationAlgorithm {

    /** Tipo de definición que interpreta este algoritmo. */
    StrategyType type();

    /**
     * Valúa a un jugador. Es un cálculo puro: no persiste ni consulta nada.
     *
     * @throws IllegalArgumentException si {@code strategy.type()} no es {@link #type()} o la entrada es inválida
     */
    QuoteResult calculate(ValuationInput input, ValuationStrategyDefinition strategy);
}
