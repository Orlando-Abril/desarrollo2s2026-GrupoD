package com.example.demo.dto.quote;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "RankingPageResponse", description = "Página estable del ranking")
public record RankingPageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RankingEntryResponse> items,
        @Schema(minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(minimum = "1", maximum = "100", requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
        @Schema(minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int totalPages) {
    public RankingPageResponse {
        items = items == null ? List.of() : List.copyOf(items);
        if (page < 0 || size < 1 || size > 100 || totalElements < 0) {
            throw new IllegalArgumentException("Metadatos de página inválidos");
        }
        int expectedPages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        if (totalPages != expectedPages) throw new IllegalArgumentException("totalPages inconsistente");
    }
}
