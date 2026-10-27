package com.example.demo.model.market;

import com.example.demo.model.Player;
import com.example.demo.model.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * Orden de compra o venta de tokens de un jugador (spec 026, FR-012). Se crea sólo con {@link #limit} o
 * {@link #market} y cambia sólo con {@link #fill} y {@link #cancel}; las LIMIT activas forman el libro.
 * La clase no se llama {@code Order} para no chocar con {@code jakarta.persistence.criteria.Order}.
 */
@Entity
@Table(name = "orders",
        indexes = {
                @Index(name = "idx_orders_book", columnList = "player_id, side, status, limit_price, created_at"),
                @Index(name = "idx_orders_user_created", columnList = "user_id, created_at")
        },
        check = @CheckConstraint(name = "ck_orders_quantities_and_price",
                constraint = "quantity > 0 AND filled_quantity >= 0 AND filled_quantity <= quantity"
                        + " AND ((type = 'LIMIT' AND limit_price IS NOT NULL AND limit_price > 0) OR (type = 'MARKET' AND limit_price IS NULL))"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketOrder {

    private static final int PRICE_SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private Player player;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false, updatable = false)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false, updatable = false)
    private OrderType type;

    /** Sólo en las LIMIT; nulo en las MARKET. */
    @Column(name = "limit_price", precision = 12, scale = 4, updatable = false)
    private BigDecimal limitPrice;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "filled_quantity", nullable = false)
    private int filledQuantity;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Nulo salvo en {@code FILLED} y {@code CANCELLED}. */
    @Column(name = "closed_at")
    private Instant closedAt;

    public static MarketOrder limit(User user, Player player, OrderSide side, int quantity, BigDecimal limitPrice,
                                    Instant now) {
        if (limitPrice == null || limitPrice.signum() <= 0) {
            throw new IllegalArgumentException("limitPrice must be positive");
        }
        MarketOrder order = open(user, player, side, OrderType.LIMIT, quantity, now);
        order.limitPrice = limitPrice.setScale(PRICE_SCALE, RoundingMode.HALF_EVEN);
        return order;
    }

    public static MarketOrder market(User user, Player player, OrderSide side, int quantity, Instant now) {
        return open(user, player, side, OrderType.MARKET, quantity, now);
    }

    private static MarketOrder open(User user, Player player, OrderSide side, OrderType type, int quantity,
                                    Instant now) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        MarketOrder order = new MarketOrder();
        order.user = Objects.requireNonNull(user, "user");
        order.player = Objects.requireNonNull(player, "player");
        order.side = Objects.requireNonNull(side, "side");
        order.type = type;
        order.quantity = quantity;
        order.status = OrderStatus.OPEN;
        order.createdAt = now;
        order.updatedAt = now;
        return order;
    }

    public int remaining() {
        return quantity - filledQuantity;
    }

    public void fill(int n, Instant now) {
        requireActive();
        if (n <= 0 || n > remaining()) {
            throw new IllegalArgumentException("fill quantity must be between 1 and remaining");
        }
        filledQuantity += n;
        updatedAt = now;
        if (remaining() == 0) {
            status = OrderStatus.FILLED;
            closedAt = now;
        } else {
            status = OrderStatus.PARTIALLY_FILLED;
        }
    }

    public void cancel(Instant now) {
        requireActive();
        status = OrderStatus.CANCELLED;
        updatedAt = now;
        closedAt = now;
    }

    /** Créditos que reserva: lo pendiente al precio límite, sólo en una compra LIMIT activa. */
    public BigDecimal reservedCredits() {
        if (side != OrderSide.BUY || type != OrderType.LIMIT || !status.isActive()) {
            return BigDecimal.ZERO.setScale(PRICE_SCALE);
        }
        return limitPrice.multiply(BigDecimal.valueOf(remaining()));
    }

    private void requireActive() {
        if (!status.isActive()) {
            throw new IllegalStateException("order_not_active");
        }
    }
}
