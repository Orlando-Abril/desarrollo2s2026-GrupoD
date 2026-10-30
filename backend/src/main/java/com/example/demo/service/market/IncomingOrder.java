package com.example.demo.service.market;

import com.example.demo.model.market.OrderSide;
import com.example.demo.model.market.OrderType;

import java.math.BigDecimal;
import java.util.Objects;

/** Orden que llega al motor de calce. {@code limitPrice} es nulo en una MARKET. */
public record IncomingOrder(Long userId, OrderSide side, OrderType type, BigDecimal limitPrice, int quantity) {

    public IncomingOrder {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(type, "type");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (type == OrderType.LIMIT && (limitPrice == null || limitPrice.signum() <= 0)) {
            throw new IllegalArgumentException("LIMIT order requires a positive limitPrice");
        }
        if (type == OrderType.MARKET && limitPrice != null) {
            throw new IllegalArgumentException("MARKET order must not have a limitPrice");
        }
    }
}
