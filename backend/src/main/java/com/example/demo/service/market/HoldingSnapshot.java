package com.example.demo.service.market;

import com.example.demo.model.market.Holding;

/** Estado de una tenencia para la auditoría. */
public record HoldingSnapshot(Long id, Long userId, Long playerId, int quantity, int reservedQuantity) {

    public static HoldingSnapshot of(Holding holding) {
        return new HoldingSnapshot(holding.getId(), holding.getUser().getId(), holding.getPlayer().getId(),
                holding.getQuantity(), holding.getReservedQuantity());
    }
}
