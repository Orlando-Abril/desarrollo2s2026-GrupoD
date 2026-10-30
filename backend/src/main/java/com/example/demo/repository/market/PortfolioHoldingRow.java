package com.example.demo.repository.market;

import java.math.BigDecimal;

public record PortfolioHoldingRow(
        Long holdingId,
        Long playerId,
        String playerName,
        BigDecimal marketValue,
        int quantity,
        int reservedQuantity) {
}
