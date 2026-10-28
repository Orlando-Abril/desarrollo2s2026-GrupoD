package com.example.demo.model.market;

import com.example.demo.model.Player;
import com.example.demo.model.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HoldingTest {

    private static final Instant T0 = Instant.parse("2026-10-27T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-27T11:00:00Z");

    private Holding holding(int quantity, int reserved) {
        Holding holding = Holding.issue(User.builder().build(), Player.builder().build(), quantity, T0);
        if (reserved > 0) {
            holding.reserve(reserved, T0);
        }
        return holding;
    }

    @Test
    void issueStartsWithoutReservations() {
        Holding holding = holding(100, 0);

        assertThat(holding.getQuantity()).isEqualTo(100);
        assertThat(holding.getReservedQuantity()).isZero();
        assertThat(holding.getCreatedAt()).isEqualTo(T0);
    }

    @Test
    void availableIsQuantityMinusReserved() {
        assertThat(holding(10, 4).available()).isEqualTo(6);
    }

    @Test
    void validOperationsUpdateQuantitiesAndTimestamp() {
        Holding holding = holding(10, 4);

        holding.credit(5, T1);
        holding.reserve(3, T1);
        holding.release(2, T1);
        holding.consumeReserved(4, T1);
        holding.debit(1, T1);

        assertThat(holding.getQuantity()).isEqualTo(10);
        assertThat(holding.getReservedQuantity()).isEqualTo(1);
        assertThat(holding.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void operationsThatWouldBreakAnInvariantChangeNothing() {
        assertUnchangedOnFailure((h, n) -> h.reserve(n, T1), 7);
        assertUnchangedOnFailure((h, n) -> h.debit(n, T1), 7);
        assertUnchangedOnFailure((h, n) -> h.release(n, T1), 5);
        assertUnchangedOnFailure((h, n) -> h.consumeReserved(n, T1), 5);
    }

    @Test
    void nonPositiveAmountsAreRejected() {
        Holding holding = holding(10, 4);

        for (BiConsumer<Holding, Integer> operation : java.util.List.<BiConsumer<Holding, Integer>>of(
                (h, n) -> h.credit(n, T1), (h, n) -> h.debit(n, T1), (h, n) -> h.reserve(n, T1),
                (h, n) -> h.release(n, T1), (h, n) -> h.consumeReserved(n, T1))) {
            assertThatThrownBy(() -> operation.accept(holding, 0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> operation.accept(holding, -1)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> Holding.issue(User.builder().build(), Player.builder().build(), 0, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertUnchangedOnFailure(BiConsumer<Holding, Integer> operation, int amount) {
        Holding holding = holding(10, 4);

        assertThatThrownBy(() -> operation.accept(holding, amount)).isInstanceOf(IllegalStateException.class);
        assertThat(holding.getQuantity()).isEqualTo(10);
        assertThat(holding.getReservedQuantity()).isEqualTo(4);
    }
}
