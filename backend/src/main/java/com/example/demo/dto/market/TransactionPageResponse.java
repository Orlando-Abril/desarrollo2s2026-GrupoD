package com.example.demo.dto.market;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

@Schema(name = "TransactionPageResponse", description = "Página estable del historial de calces")
public record TransactionPageResponse(
        @ArraySchema(schema = @Schema(implementation = TransactionResponse.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED)) List<TransactionResponse> items,
        @Schema(example = "0", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(example = "20", minimum = "1", maximum = "100", requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(example = "20", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int numberOfElements,
        @Schema(example = "25", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
        @Schema(example = "2", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int totalPages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean first,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean last) {

    public TransactionPageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (page < 0 || size < 1 || size > 100 || numberOfElements < 0 || numberOfElements > size
                || totalElements < 0 || totalPages < 0 || numberOfElements != items.size()
                || first != (page == 0) || last != (totalPages == 0 || page >= totalPages - 1)) {
            throw new IllegalArgumentException("invalid page metadata");
        }
    }
}
