package com.example.demo.service.valuation;

import java.time.Instant;
import java.util.UUID;

/** Registra qué estrategia fijó el precio de mercado al terminar una corrida (spec 031, research R4). */
@FunctionalInterface
public interface MarketStrategyRecorder {

    MarketStrategyRecorder NONE = (code, appliedAt, runId) -> { };

    void recordMarketStrategy(String code, Instant appliedAt, UUID runId);
}
