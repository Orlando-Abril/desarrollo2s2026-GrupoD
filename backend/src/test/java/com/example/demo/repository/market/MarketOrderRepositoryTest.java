package com.example.demo.repository.market;

import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderSide;
import com.example.demo.support.UnitTestProfile;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@UnitTestProfile
class MarketOrderRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-10-27T10:00:00Z");

    @Autowired private MarketOrderRepository repository;
    @Autowired private TestEntityManager entityManager;

    private MarketOrder buyLimit(User user, Player player, int quantity, String price, int filled) {
        MarketOrder order = MarketOrder.limit(user, player, OrderSide.BUY, quantity, new BigDecimal(price), T0);
        if (filled > 0) {
            order.fill(filled, T0);
        }
        return repository.save(order);
    }

    @Test
    void reservedCreditsCountOnlyActiveBuyLimits() {
        User user = MarketFixtures.user(entityManager);
        Player player = MarketFixtures.player(entityManager);
        buyLimit(user, player, 10, "1.0500", 4);
        buyLimit(user, player, 5, "2.0000", 2);
        buyLimit(user, player, 3, "1.0000", 3);
        buyLimit(user, player, 4, "1.0000", 0).cancel(T0);
        repository.save(MarketOrder.market(user, player, OrderSide.BUY, 7, T0));
        repository.save(MarketOrder.limit(user, player, OrderSide.SELL, 8, new BigDecimal("1.2000"), T0));
        entityManager.flush();

        assertThat(repository.sumReservedCredits(user.getId())).isEqualByComparingTo("12.3000").hasScaleOf(4);
    }

    @Test
    void reservedCreditsAreZeroWithoutOrders() {
        assertThat(repository.sumReservedCredits(MarketFixtures.user(entityManager).getId()))
                .isEqualByComparingTo("0");
    }

    @Test
    void theDatabaseRejectsAPriceThatDoesNotMatchTheType() {
        User user = MarketFixtures.user(entityManager);
        Player player = MarketFixtures.player(entityManager);

        assertThatThrownBy(() -> insertOrder(user, player, "MARKET", "1.0000"))
                .isInstanceOf(PersistenceException.class);
        assertThatThrownBy(() -> insertOrder(user, player, "LIMIT", null))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void pagesTheOrdersOfAUserFromNewestToOldest() {
        User user = MarketFixtures.user(entityManager);
        Player player = MarketFixtures.player(entityManager);
        MarketOrder older = repository.save(MarketOrder.market(user, player, OrderSide.BUY, 1, T0));
        MarketOrder newer = repository.save(MarketOrder.market(user, player, OrderSide.BUY, 1, T0.plusSeconds(60)));
        MarketOrder sameInstant = repository.save(MarketOrder.market(user, player, OrderSide.BUY, 1, T0.plusSeconds(60)));

        assertThat(repository.findByUserIdOrderByCreatedAtDescIdDesc(user.getId(), PageRequest.of(0, 2)))
                .containsExactly(sameInstant, newer);
        assertThat(repository.findByUserIdOrderByCreatedAtDescIdDesc(user.getId(), PageRequest.of(1, 2)))
                .containsExactly(older);
    }

    private void insertOrder(User user, Player player, String type, String limitPrice) {
        entityManager.getEntityManager().createNativeQuery("INSERT INTO orders (user_id, player_id, side, type,"
                        + " limit_price, quantity, filled_quantity, status, created_at, updated_at)"
                        + " VALUES (?, ?, 'BUY', ?, ?, 1, 0, 'OPEN', ?, ?)")
                .setParameter(1, user.getId())
                .setParameter(2, player.getId())
                .setParameter(3, type)
                .setParameter(4, limitPrice == null ? null : new BigDecimal(limitPrice))
                .setParameter(5, T0)
                .setParameter(6, T0)
                .executeUpdate();
    }
}
