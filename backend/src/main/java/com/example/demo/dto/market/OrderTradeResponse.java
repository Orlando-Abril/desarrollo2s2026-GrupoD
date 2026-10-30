package com.example.demo.dto.market;

import com.example.demo.model.market.Trade;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "OrderTradeResponse", description = "Operación ejecutada por la orden")
public record OrderTradeResponse(
        @Schema(example = "501") Long id,
        @Schema(example = "4") int quantity,
        @Schema(example = "1.2000") BigDecimal price,
        @Schema(example = "4.8000") BigDecimal total,
        @Schema(example = "2026-10-29T15:04:05.123Z") Instant executedAt) {

    public static OrderTradeResponse of(Trade trade) {
        return new OrderTradeResponse(trade.getId(), trade.getQuantity(), trade.getPrice(), trade.getTotal(),
                trade.getExecutedAt());
    }
}
