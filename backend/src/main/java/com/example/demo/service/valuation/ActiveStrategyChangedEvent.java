package com.example.demo.service.valuation;

/** Se publica dentro de la transacción de un cambio efectivo de la estrategia activa (spec 031). */
public record ActiveStrategyChangedEvent(String from, String to) {
}
