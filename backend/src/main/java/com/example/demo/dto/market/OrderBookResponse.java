package com.example.demo.dto.market;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(name = "OrderBookResponse", description = "Libro de un jugador agregado por precio y su cotización vigente")
public record OrderBookResponse(
        @Schema(example = "42") Long playerId,
        @Schema(example = "1.2000") BigDecimal marketValue,
        @Schema(description = "Ventas de menor a mayor precio") List<BookLevelResponse> sells,
        @Schema(description = "Compras de mayor a menor precio") List<BookLevelResponse> buys) {
}
