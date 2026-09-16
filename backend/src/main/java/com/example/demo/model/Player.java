package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Modelo mínimo de jugador para el catálogo. {@code externalId} queda reservado
 * para mapear el registro con una API externa (Football-Data.org / WhoScored)
 * cuando se integre esa fuente de datos.
 */
@Entity
@Table(name = "players", indexes = @Index(name = "idx_players_external_id", columnList = "external_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private League league;

    /** Un jugador puede jugar en más de una posición (ej. DEFENDER + MIDFIELDER). */
    @ElementCollection(targetClass = Position.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "player_positions", joinColumns = @JoinColumn(name = "player_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "position", nullable = false)
    @Builder.Default
    private Set<Position> positions = new HashSet<>();

    private String nationality;

    private Integer age;

    @Column(name = "height_cm")
    private Integer heightCm;

    /** Base para el precio simulado en el mercado; se recalculará con rendimiento real más adelante. */
    @Column(name = "market_value", nullable = false)
    private BigDecimal marketValue;
}