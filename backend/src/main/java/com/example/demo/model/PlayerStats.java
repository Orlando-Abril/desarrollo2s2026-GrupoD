package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Último conjunto de métricas crudas de WhoScored de un jugador (totales de la temporada en curso).
 * Comparte la clave primaria con {@link Player}; {@code Player} no conoce esta entidad.
 * Toda métrica puede ser {@code null}: ausente en la fuente, nunca reemplazada por cero.
 */
@Entity
@Table(name = "player_stats")
@Getter
@Setter
@NoArgsConstructor
public class PlayerStats {

    @Id
    @Column(name = "player_id")
    private Long playerId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id")
    private Player player;

    @Column(name = "whoscored_player_id", length = 32)
    private String whoscoredPlayerId;

    @Column(name = "minutes_played")
    private Integer minutesPlayed;

    private Integer goals;

    private Integer assists;

    private Integer shots;

    @Column(name = "key_passes")
    private Integer keyPasses;

    private Integer tackles;

    @Column(name = "yellow_cards")
    private Integer yellowCards;

    @Column(name = "red_cards")
    private Integer redCards;

    @Column(precision = 4, scale = 2)
    private BigDecimal rating;

    /** Momento en que el dato se obtuvo de WhoScored (un valor servido desde caché conserva el original). */
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    public PlayerStats(Player player) {
        this.player = player;
    }
}
