package com.example.demo.service.market;

import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderSide;
import com.example.demo.model.market.OrderStatus;
import com.example.demo.model.market.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

/** Estado de una orden para la auditoría. */
public record OrderSnapshot(Long id, Long userId, Long playerId, OrderSide side, OrderType type, BigDecimal limitPrice,
                            int quantity, int filledQuantity, OrderStatus status, Instant createdAt, Instant closedAt) {

    public static OrderSnapshot of(MarketOrder order) {
        return new OrderSnapshot(order.getId(), order.getUser().getId(), order.getPlayer().getId(), order.getSide(),
                order.getType(), order.getLimitPrice(), order.getQuantity(), order.getFilledQuantity(),
                order.getStatus(), order.getCreatedAt(), order.getClosedAt());
    }
}
