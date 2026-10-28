package com.example.demo.model.market;

import com.example.demo.model.Player;
import com.example.demo.model.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeTest {

    private static final Instant T0 = Instant.parse("2026-10-27T10:00:00Z");

    private final User buyer = User.builder().id(1L).build();
    private final User seller = User.builder().id(2L).build();
    private final Player player = Player.builder().id(10L).build();

    private MarketOrder order(User user, Player of, OrderSide side) {
        return MarketOrder.limit(user, of, side, 10, new BigDecimal("1.0437"), T0);
    }

    @Test
    void computesTheExactTotalAndTakesThePartiesFromTheOrders() {
        Trade trade = Trade.of(order(buyer, player, OrderSide.BUY), order(seller, player, OrderSide.SELL), 3,
                new BigDecimal("1.0437"), T0);

        assertThat(trade.getTotal()).isEqualByComparingTo("3.1311").hasScaleOf(4);
        assertThat(trade.getBuyer()).isSameAs(buyer);
        assertThat(trade.getSeller()).isSameAs(seller);
        assertThat(trade.getPlayer()).isSameAs(player);
        assertThat(trade.getExecutedAt()).isEqualTo(T0);
    }

    @Test
    void rejectsInconsistentTrades() {
        MarketOrder buy = order(buyer, player, OrderSide.BUY);
        MarketOrder sell = order(seller, player, OrderSide.SELL);
        BigDecimal price = BigDecimal.ONE;

        assertRejected(buy, order(buyer, player, OrderSide.SELL), 1, price);
        assertRejected(buy, order(seller, Player.builder().id(11L).build(), OrderSide.SELL), 1, price);
        assertRejected(sell, buy, 1, price);
        assertRejected(buy, sell, 0, price);
        assertRejected(buy, sell, 1, BigDecimal.ZERO);
        assertRejected(buy, sell, 1, new BigDecimal("-1"));
    }

    private void assertRejected(MarketOrder buy, MarketOrder sell, int quantity, BigDecimal price) {
        assertThatThrownBy(() -> Trade.of(buy, sell, quantity, price, T0)).isInstanceOf(IllegalArgumentException.class);
    }
}
