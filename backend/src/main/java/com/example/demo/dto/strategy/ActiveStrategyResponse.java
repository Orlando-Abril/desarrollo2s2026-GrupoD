package com.example.demo.dto.strategy;

import com.example.demo.model.market.AuditActorType;
import com.example.demo.service.valuation.ActiveStrategyView;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Estrategia activa, alternativa y de mercado (spec 031). {@code changed} sólo aparece en la respuesta del
 * {@code PUT}; {@code changedAt} y {@code changedBy} son null si nunca hubo un cambio.
 */
@Schema(name = "ActiveStrategyResponse")
public record ActiveStrategyResponse(
        @Schema(description = "Estrategia que usará el próximo recálculo", requiredMode = Schema.RequiredMode.REQUIRED)
        StrategyRef active,
        @Schema(description = "La otra estrategia del catálogo", requiredMode = Schema.RequiredMode.REQUIRED)
        StrategyRef alternative,
        @Schema(description = "Estrategia que fijó el marketValue vigente", requiredMode = Schema.RequiredMode.REQUIRED)
        StrategyRef marketStrategy,
        @Schema(description = "true si el precio de mercado todavía no refleja la estrategia activa",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean pendingRecalculation,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Instant changedAt,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) ChangedBy changedBy,
        @Schema(description = "Sólo en PUT: false si la estrategia ya estaba activa")
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean changed) {

    @Schema(name = "ActiveStrategyChangedBy")
    public record ChangedBy(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AuditActorType actorType,
            @Schema(nullable = true) Long userId,
            @Schema(nullable = true) String username) {
    }

    public static ActiveStrategyResponse from(ActiveStrategyView view, Boolean changed) {
        ActiveStrategyView.ChangedBy by = view.changedBy();
        return new ActiveStrategyResponse(StrategyRef.of(view.active()), StrategyRef.of(view.alternative()),
                StrategyRef.of(view.market()), view.pendingRecalculation(), view.changedAt(),
                by == null ? null : new ChangedBy(by.actorType(), by.userId(), by.username()), changed);
    }
}
