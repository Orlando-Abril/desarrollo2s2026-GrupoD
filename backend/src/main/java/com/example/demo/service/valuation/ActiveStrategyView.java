package com.example.demo.service.valuation;

import com.example.demo.model.market.AuditActorType;
import com.example.demo.service.valuation.strategy.ValuationStrategyDefinition;

import java.time.Instant;

/**
 * Estado de la configuración de estrategias (spec 031).
 *
 * @param market   estrategia con que está fijado el {@code marketValue} vigente
 * @param changedBy nulo si nunca hubo un cambio de estrategia activa
 */
public record ActiveStrategyView(ValuationStrategyDefinition active, ValuationStrategyDefinition alternative,
                                 ValuationStrategyDefinition market, Instant changedAt, ChangedBy changedBy) {

    public boolean pendingRecalculation() {
        return !market.code().equals(active.code());
    }

    public record ChangedBy(AuditActorType actorType, Long userId, String username) {
    }
}
