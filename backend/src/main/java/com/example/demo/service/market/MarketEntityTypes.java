package com.example.demo.service.market;

/** Tipos de entidad que registra la auditoría del mercado ({@code AuditLog.entityType}). */
final class MarketEntityTypes {

    static final String ORDER = "ORDER";
    static final String TRADE = "TRADE";
    static final String USER = "USER";
    static final String HOLDING = "HOLDING";

    private MarketEntityTypes() {
    }
}
