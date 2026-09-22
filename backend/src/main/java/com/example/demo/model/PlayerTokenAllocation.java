package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "player_token_allocations")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerTokenAllocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false, unique = true, updatable = false)
    private Player player;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false, updatable = false)
    private User owner;

    @Column(name = "total_supply", nullable = false, updatable = false)
    @Builder.Default
    private int totalSupply = 100;

    @Column(name = "owner_quantity", nullable = false, updatable = false)
    @Builder.Default
    private int ownerQuantity = 100;

    @Column(name = "base_price", nullable = false, precision = 19, scale = 2, updatable = false)
    @Builder.Default
    private BigDecimal basePrice = new BigDecimal("1.00");

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void initialize() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        totalSupply = 100;
        ownerQuantity = 100;
        basePrice = new BigDecimal("1.00");
    }
}
