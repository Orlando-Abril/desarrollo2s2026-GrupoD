package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
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
 * Copia histórica e inmutable de los totales de estadísticas persistidos para un jugador.
 * {@link Player} no conoce esta entidad y cada versión de origen sólo puede capturarse una vez.
 */
@Entity
@Table(name = "player_stats_snapshots",
        indexes = @Index(name = "idx_player_stats_snapshots_player_captured",
                columnList = "player_id, captured_at"),
        uniqueConstraints = @UniqueConstraint(name = "uk_player_stats_snapshots_player_source",
                columnNames = {"player_id", "source_fetched_at"}))
@Immutable
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlayerStatsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private Player player;

    @Column(name = "minutes_played", updatable = false)
    private Integer minutesPlayed;

    @Column(updatable = false)
    private Integer goals;

    @Column(updatable = false)
    private Integer assists;

    @Column(updatable = false)
    private Integer shots;

    @Column(name = "key_passes", updatable = false)
    private Integer keyPasses;

    @Column(updatable = false)
    private Integer tackles;

    @Column(updatable = false)
    private Integer dribbles;

    @Column(name = "yellow_cards", updatable = false)
    private Integer yellowCards;

    @Column(name = "red_cards", updatable = false)
    private Integer redCards;

    @Column(precision = 4, scale = 2, updatable = false)
    private BigDecimal rating;

    @NotNull
    @Column(name = "source_fetched_at", nullable = false, updatable = false)
    private Instant sourceFetchedAt;

    @NotNull
    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    @NotNull
    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;
}
