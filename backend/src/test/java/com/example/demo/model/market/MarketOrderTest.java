package com.example.demo.model.market;

import com.example.demo.model.Player;
import com.example.demo.model.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketOrderTest {

    private static final Instant T0 = Instant.parse("2026-10-27T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-27T11:00:00Z");

    private final User user = User.builder().build();
    private final Player player = Player.builder().build();

    private MarketOrder buyLimit(int quantity, String price) {
        return MarketOrder.limit(user, player, OrderSide.BUY, quantity, new BigDecimal(price), T0);
    }

    @Test
    void factoriesStartOpenAndUnfilled() {
        MarketOrder limit = buyLimit(10, "1.05");
        MarketOrder market = MarketOrder.market(user, player, OrderSide.SELL, 3, T0);

        for (MarketOrder order : new MarketOrder[]{limit, market}) {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.OPEN);
            assertThat(order.getFilledQuantity()).isZero();
            assertThat(order.getClosedAt()).isNull();
            assertThat(order.getCreatedAt()).isEqualTo(T0);
        }
        assertThat(limit.getLimitPrice()).isEqualByComparingTo("1.0500").hasScaleOf(4);
        assertThat(market.getLimitPrice()).isNull();
    }

    @Test
    void factoriesRejectInvalidData() {
        assertThatThrownBy(() -> MarketOrder.limit(user, player, OrderSide.BUY, 1, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> buyLimit(1, "0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> buyLimit(1, "-1.00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> buyLimit(0, "1.00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MarketOrder.market(user, player, OrderSide.BUY, -1, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fillsPartiallyAndThenCompletely() {
        MarketOrder order = buyLimit(10, "1.05");

        order.fill(4, T1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(order.getClosedAt()).isNull();
        assertThat(order.remaining()).isEqualTo(6);

        order.fill(6, T1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.getClosedAt()).isEqualTo(T1);
    }

    @Test
    void cannotFillMoreThanRemaining() {
        MarketOrder order = buyLimit(10, "1.05");

        assertThatThrownBy(() -> order.fill(11, T1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> order.fill(0, T1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(order.getFilledQuantity()).isZero();
    }

    @Test
    void cancelKeepsTheFilledQuantity() {
        MarketOrder order = buyLimit(10, "1.05");
        order.fill(4, T0);

        order.cancel(T1);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getClosedAt()).isEqualTo(T1);
        assertThat(order.getFilledQuantity()).isEqualTo(4);
    }

    @Test
    void terminalOrdersCannotChange() {
        MarketOrder filled = buyLimit(1, "1.05");
        filled.fill(1, T0);
        MarketOrder cancelled = buyLimit(1, "1.05");
        cancelled.cancel(T0);

        for (MarketOrder order : new MarketOrder[]{filled, cancelled}) {
            assertThatThrownBy(() -> order.fill(1, T1)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> order.cancel(T1)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void onlyActiveBuyLimitsReserveCredits() {
        MarketOrder open = buyLimit(10, "1.05");
        open.fill(4, T0);
        MarketOrder cancelled = buyLimit(10, "1.05");
        cancelled.cancel(T0);

        assertThat(open.reservedCredits()).isEqualByComparingTo("6.3000");
        assertThat(cancelled.reservedCredits()).isZero();
        assertThat(MarketOrder.market(user, player, OrderSide.BUY, 5, T0).reservedCredits()).isZero();
        assertThat(MarketOrder.limit(user, player, OrderSide.SELL, 5, BigDecimal.ONE, T0).reservedCredits()).isZero();
    }
}
