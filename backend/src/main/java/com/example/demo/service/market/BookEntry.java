package com.example.demo.service.market;

import com.example.demo.model.market.MarketOrder;

import java.math.BigDecimal;

/** Orden del libro vista por el motor de calce: sin JPA, con lo que le falta ejecutar. */
public record BookEntry(Long orderId, Long userId, BigDecimal limitPrice, int remaining) {

    public static BookEntry of(MarketOrder order) {
        return new BookEntry(order.getId(), order.getUser().getId(), order.getLimitPrice(), order.remaining());
    }
}
