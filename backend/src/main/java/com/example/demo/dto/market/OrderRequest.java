package com.example.demo.dto.market;

import com.example.demo.model.market.OrderType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;

@Schema(name = "OrderRequest", description = "Orden de compra o venta de tokens de un jugador")
public record OrderRequest(
        @Schema(example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "es obligatorio") Long playerId,
        @Schema(example = "10", description = "Entero mayor a 0", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonDeserialize(using = StrictIntegerDeserializer.class)
        @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor a 0") Integer quantity,
        @Schema(example = "LIMIT", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "es obligatorio") OrderType type,
        @Schema(example = "1.3000", nullable = true,
                description = "Obligatorio en LIMIT, prohibido en MARKET; hasta 4 decimales; dentro de ±20% de la cotización vigente")
        @Positive(message = "debe ser mayor a 0")
        @Digits(integer = 8, fraction = 4, message = "admite hasta 8 enteros y 4 decimales") BigDecimal limitPrice) {
}
