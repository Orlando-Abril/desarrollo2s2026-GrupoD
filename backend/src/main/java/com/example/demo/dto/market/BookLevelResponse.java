package com.example.demo.dto.market;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "BookLevelResponse", description = "Un precio del libro y la cantidad pendiente total a ese precio")
public record BookLevelResponse(
        @Schema(example = "1.1000") BigDecimal price,
        @Schema(example = "7") long quantity) {
}
