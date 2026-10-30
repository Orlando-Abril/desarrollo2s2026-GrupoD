package com.example.demo.dto.market;

import com.example.demo.model.market.OrderSide;
import com.example.demo.model.market.OrderStatus;
import com.example.demo.model.market.OrderType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(name = "MyOrderResponse", description = "Orden activa del usuario autenticado, con el nombre de su jugador")
public record MyOrderResponse(
        @Schema(example = "1001") Long id,
        @Schema(example = "42") Long playerId,
        @Schema(example = "Lionel Messi") String playerName,
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
        @Schema(description = "Siempre vacía: la lista no muestra operaciones") List<OrderTradeResponse> trades) {

    /** La respuesta de la orden, con el mismo formato que {@code POST /orders/buy}, más el nombre del jugador. */
    public static MyOrderResponse of(OrderResponse order, String playerName) {
        return new MyOrderResponse(order.id(), order.playerId(), playerName, order.side(), order.type(),
                order.limitPrice(), order.quantity(), order.filledQuantity(), order.remainingQuantity(),
                order.status(), order.averagePrice(), order.createdAt(), order.closedAt(), order.trades());
    }
}
