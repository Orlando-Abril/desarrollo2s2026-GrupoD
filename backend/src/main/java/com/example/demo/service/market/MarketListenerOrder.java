package com.example.demo.service.market;

/**
 * Orden de los listeners del mercado sobre {@code QuoteRunCompletedEvent} (spec 027, research D7): la emisión de
 * tokens de los jugadores nuevos tiene que correr antes del reprecio, así un jugador emitido en un recálculo recibe
 * la orden de venta del superusuario en ese mismo recálculo.
 */
public final class MarketListenerOrder {

    public static final int TOKEN_ISSUANCE = 0;
    public static final int MARKET_MAKER = 100;

    private MarketListenerOrder() {
    }
}
