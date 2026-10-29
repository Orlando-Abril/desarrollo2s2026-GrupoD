package com.example.demo.service.market;

import com.example.demo.model.market.Trade;

import java.math.BigDecimal;
import java.time.Instant;

/** Estado de una operación ejecutada para la auditoría. */
public record TradeSnapshot(Long id, Long playerId, Long buyerId, Long sellerId, Long buyOrderId, Long sellOrderId,
                            int quantity, BigDecimal price, BigDecimal total, Instant executedAt) {

    public static TradeSnapshot of(Trade trade) {
        return new TradeSnapshot(trade.getId(), trade.getPlayer().getId(), trade.getBuyer().getId(),
                trade.getSeller().getId(), trade.getBuyOrder().getId(), trade.getSellOrder().getId(),
                trade.getQuantity(), trade.getPrice(), trade.getTotal(), trade.getExecutedAt());
    }
}
