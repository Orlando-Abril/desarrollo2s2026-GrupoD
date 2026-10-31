package com.example.demo.model.market;

/**
 * Acción registrada en un {@link AuditLog}. La spec 026 registra la creación del superusuario y la emisión de
 * tokens; la spec 027 registra las órdenes (creación, calce y cancelación), las operaciones ejecutadas y los
 * cambios de saldo y de tenencia que produce cada calce. La spec 031 registra el cambio de la estrategia activa y,
 * al terminar un recálculo, el cambio de la estrategia que fija el precio de mercado.
 */
public enum AuditAction {
    SUPERUSER_CREATED,
    TOKENS_ISSUED,
    ORDER_CREATED,
    ORDER_FILLED,
    ORDER_CANCELLED,
    TRADE_EXECUTED,
    BALANCE_CHANGED,
    HOLDING_CHANGED,
    ACTIVE_STRATEGY_CHANGED,
    MARKET_STRATEGY_APPLIED
}
