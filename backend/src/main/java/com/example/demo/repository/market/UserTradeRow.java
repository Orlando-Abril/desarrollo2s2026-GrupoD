package com.example.demo.repository.market;

import java.math.BigDecimal;
import java.time.Instant;

public record UserTradeRow(
        Long id,
        Long playerId,
        String playerName,
        Long buyerId,
        Long sellerId,
        int quantity,
        BigDecimal price,
        BigDecimal total,
        Instant executedAt) {
}
