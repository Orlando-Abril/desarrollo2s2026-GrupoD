package com.example.demo.model.market;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Estado de una {@link MarketOrder}. {@code FILLED} y {@code CANCELLED} son terminales. */
public enum OrderStatus {
    OPEN,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED;

    /** Estados de una orden que sigue en el libro (y reserva créditos o tokens si es LIMIT). */
    public static final Set<OrderStatus> ACTIVE = Collections.unmodifiableSet(EnumSet.of(OPEN, PARTIALLY_FILLED));

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
