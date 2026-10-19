package com.example.demo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro histórico e inmutable del precio del token de un jugador según una estrategia y versión
 * en un instante. Nunca se actualiza ni se borra: cada corrida agrega registros nuevos.
 * La estrategia se referencia por código y versión, no por FK (no existe tabla de estrategias);
 * {@link Player} no conoce esta entidad.
 */
@Entity
@Table(name = "player_quotes", indexes = {
        @Index(name = "idx_player_quotes_player_strategy_time", columnList = "player_id, strategy_code, calculated_at"),
        @Index(name = "idx_player_quotes_strategy_time", columnList = "strategy_code, calculated_at")
})
@Immutable
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlayerQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private Player player;

    @NotBlank
    @Column(name = "strategy_code", length = 32, nullable = false, updatable = false)
    private String strategyCode;

    /** {@code Integer} para que omitirla se rechace por obligatoria y no pase como 0. */
    @NotNull
    @Min(1)
    @Column(name = "strategy_version", nullable = false, updatable = false)
    private Integer strategyVersion;

    /** Precio del token en créditos. La columna no se llama {@code value} porque es palabra reservada en H2. */
    @NotNull
    @PositiveOrZero
    @Column(name = "quote_value", precision = 12, scale = 4, nullable = false, updatable = false)
    private BigDecimal value;

    /** Obligatorio sólo si {@code status} es {@link QuoteStatus#CALCULATED}. */
    @Column(precision = 12, scale = 4, updatable = false)
    private BigDecimal score;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false, updatable = false)
    private QuoteStatus status;

    @NotNull
    @Column(name = "calculated_at", nullable = false, updatable = false)
    private Instant calculatedAt;

    @NotNull
    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @NotBlank
    @Column(name = "correlation_id", length = 64, nullable = false, updatable = false)
    private String correlationId;

    @PrePersist
    void validate() {
        if (status == QuoteStatus.CALCULATED && score == null) {
            throw new IllegalStateException("score es obligatorio para CALCULATED");
        }
    }
}
