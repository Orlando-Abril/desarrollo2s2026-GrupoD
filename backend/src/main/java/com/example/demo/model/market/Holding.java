package com.example.demo.model.market;

import com.example.demo.model.Player;
import com.example.demo.model.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Tokens de un jugador que tiene un usuario y cuántos están reservados por órdenes de venta abiertas
 * (spec 026, FR-010). Nunca queda negativa ni con más reservados que tokens; nunca se borra, ni siquiera en 0.
 */
@Entity
@Table(name = "holdings",
        uniqueConstraints = @UniqueConstraint(name = "uk_holdings_user_player", columnNames = {"user_id", "player_id"}),
        indexes = @Index(name = "idx_holdings_user", columnList = "user_id"),
        check = @CheckConstraint(name = "ck_holdings_quantities",
                constraint = "quantity >= 0 AND reserved_quantity >= 0 AND reserved_quantity <= quantity"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private Player player;

    @PositiveOrZero
    @Column(nullable = false)
    private int quantity;

    @PositiveOrZero
    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Holding issue(User user, Player player, int quantity, Instant now) {
        requirePositive(quantity);
        Holding holding = new Holding();
        holding.user = user;
        holding.player = player;
        holding.quantity = quantity;
        holding.createdAt = now;
        holding.updatedAt = now;
        return holding;
    }

    public int available() {
        return quantity - reservedQuantity;
    }

    public void credit(int n, Instant now) {
        requirePositive(n);
        quantity += n;
        updatedAt = now;
    }

    /** Venta MARKET: descuenta tokens no reservados. */
    public void debit(int n, Instant now) {
        requirePositive(n);
        requireState(n <= available(), "insufficient_available_tokens");
        quantity -= n;
        updatedAt = now;
    }

    public void reserve(int n, Instant now) {
        requirePositive(n);
        requireState(n <= available(), "insufficient_available_tokens");
        reservedQuantity += n;
        updatedAt = now;
    }

    public void release(int n, Instant now) {
        requirePositive(n);
        requireState(n <= reservedQuantity, "insufficient_reserved_tokens");
        reservedQuantity -= n;
        updatedAt = now;
    }

    /** Venta LIMIT calzada: los tokens salen de la reserva y de la tenencia. */
    public void consumeReserved(int n, Instant now) {
        requirePositive(n);
        requireState(n <= reservedQuantity, "insufficient_reserved_tokens");
        reservedQuantity -= n;
        quantity -= n;
        updatedAt = now;
    }

    private static void requirePositive(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    private static void requireState(boolean condition, String code) {
        if (!condition) {
            throw new IllegalStateException(code);
        }
    }
}
