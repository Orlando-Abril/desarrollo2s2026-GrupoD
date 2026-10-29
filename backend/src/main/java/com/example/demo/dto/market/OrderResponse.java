package com.example.demo.dto.market;

import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderSide;
import com.example.demo.model.market.OrderStatus;
import com.example.demo.model.market.OrderType;
import com.example.demo.model.market.Trade;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Schema(name = "OrderResponse", description = "Orden en su estado final del pedido, con las operaciones que ejecutó")
public record OrderResponse(
        @Schema(example = "1001") Long id,
        @Schema(example = "42") Long playerId,
        @Schema(example = "BUY") OrderSide side,
        @Schema(example = "LIMIT") OrderType type,
        @Schema(example = "1.3000", nullable = true) BigDecimal limitPrice,
        @Schema(example = "10") int quantity,
        @Schema(example = "4") int filledQuantity,
        @Schema(example = "6") int remainingQuantity,
        @Schema(example = "PARTIALLY_FILLED") OrderStatus status,
        @Schema(example = "1.2000", nullable = true, description = "Nulo si no se ejecutó nada") BigDecimal averagePrice,
        @Schema(example = "2026-10-29T15:04:05.123Z") Instant createdAt,
        @Schema(nullable = true, description = "Fecha de cierre; nula mientras la orden sigue en el libro") Instant closedAt,
        List<OrderTradeResponse> trades) {

    private static final int SCALE = 4;

    /** Respuesta con {@code trades} como operaciones mostradas y como base del precio promedio. */
    public static OrderResponse of(MarketOrder order, List<Trade> trades) {
        return of(order, trades, trades);
    }

    /**
     * {@code executed} son todas las operaciones de la orden (para el precio promedio) y {@code shown} las que
     * viajan en la respuesta: en una cancelación, el promedio usa las operaciones previas pero la lista va vacía.
     */
    public static OrderResponse of(MarketOrder order, List<Trade> executed, List<Trade> shown) {
        return new OrderResponse(order.getId(), order.getPlayer().getId(), order.getSide(), order.getType(),
                order.getLimitPrice(), order.getQuantity(), order.getFilledQuantity(), order.remaining(),
                order.getStatus(), averagePrice(order.getFilledQuantity(), executed), order.getCreatedAt(),
                order.getClosedAt(), shown.stream().map(OrderTradeResponse::of).toList());
    }

    private static BigDecimal averagePrice(int filledQuantity, List<Trade> trades) {
        if (filledQuantity == 0) {
            return null;
        }
        BigDecimal total = trades.stream().map(Trade::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(filledQuantity), SCALE, RoundingMode.HALF_EVEN);
    }
}
