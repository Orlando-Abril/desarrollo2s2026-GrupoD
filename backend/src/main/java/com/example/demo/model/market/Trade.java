package com.example.demo.model.market;

import com.example.demo.model.Player;
import com.example.demo.model.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * Operación ejecutada: un calce entre una orden de compra y una de venta (spec 026, FR-018). Inmutable:
 * {@code @Immutable} ignora los cambios y {@link #preventRemove()} rechaza el borrado por entidad, que
 * Hibernate sí permite. Los {@code DELETE} JPQL masivos y el SQL nativo quedan fuera de esta garantía.
 */
@Entity
@Table(name = "trades",
        indexes = {
                @Index(name = "idx_trades_buyer_executed", columnList = "buyer_id, executed_at"),
                @Index(name = "idx_trades_seller_executed", columnList = "seller_id, executed_at"),
                @Index(name = "idx_trades_player_executed", columnList = "player_id, executed_at")
        },
        check = @CheckConstraint(name = "ck_trades_values",
                constraint = "quantity > 0 AND price > 0 AND buyer_id <> seller_id"))
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trade {

    private static final int SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private Player player;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false, updatable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false, updatable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buy_order_id", nullable = false, updatable = false)
    private MarketOrder buyOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sell_order_id", nullable = false, updatable = false)
    private MarketOrder sellOrder;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(precision = 12, scale = 4, nullable = false, updatable = false)
    private BigDecimal price;

    /** Exactamente {@code price × quantity}: el precio tiene escala 4 y la cantidad es entera. */
    @Column(precision = 16, scale = 4, nullable = false, updatable = false)
    private BigDecimal total;

    @Column(name = "executed_at", nullable = false, updatable = false)
    private Instant executedAt;

    public static Trade of(MarketOrder buyOrder, MarketOrder sellOrder, int quantity, BigDecimal price,
                           Instant executedAt) {
        Objects.requireNonNull(buyOrder, "buyOrder");
        Objects.requireNonNull(sellOrder, "sellOrder");
        if (buyOrder.getSide() != OrderSide.BUY || sellOrder.getSide() != OrderSide.SELL) {
            throw new IllegalArgumentException("trade needs a BUY and a SELL order");
        }
        if (!sameId(buyOrder.getPlayer().getId(), sellOrder.getPlayer().getId())) {
            throw new IllegalArgumentException("both orders must be for the same player");
        }
        if (sameId(buyOrder.getUser().getId(), sellOrder.getUser().getId())) {
            throw new IllegalArgumentException("buyer and seller must differ");
        }
        if (quantity <= 0 || price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("quantity and price must be positive");
        }
        Trade trade = new Trade();
        trade.player = buyOrder.getPlayer();
        trade.buyer = buyOrder.getUser();
        trade.seller = sellOrder.getUser();
        trade.buyOrder = buyOrder;
        trade.sellOrder = sellOrder;
        trade.quantity = quantity;
        trade.price = price.setScale(SCALE, RoundingMode.HALF_EVEN);
        trade.total = trade.price.multiply(BigDecimal.valueOf(quantity));
        trade.executedAt = Objects.requireNonNull(executedAt, "executedAt");
        return trade;
    }

    @PreRemove
    void preventRemove() {
        throw new UnsupportedOperationException("trade_is_immutable");
    }

    private static boolean sameId(Long left, Long right) {
        return left != null && left.equals(right);
    }
}
