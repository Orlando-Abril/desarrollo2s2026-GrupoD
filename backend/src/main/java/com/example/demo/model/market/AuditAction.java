package com.example.demo.model.market;

/**
 * Acción registrada en un {@link AuditLog}. Las features siguientes del mercado agregan
 * {@code ORDER_CREATED}, {@code ORDER_FILLED}, {@code ORDER_CANCELLED}, {@code TRADE_EXECUTED}
 * y {@code BALANCE_CHANGED}.
 */
public enum AuditAction {
    SUPERUSER_CREATED,
    TOKENS_ISSUED
}
