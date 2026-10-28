package com.example.demo.repository.market;

import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.model.market.Holding;
import com.example.demo.support.UnitTestProfile;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@UnitTestProfile
class HoldingRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    @Autowired private HoldingRepository repository;
    @Autowired private TestEntityManager entityManager;

    private Holding saved(User user, Player player, int quantity) {
        Holding holding = repository.save(Holding.issue(user, player, quantity, NOW));
        entityManager.flush();
        return holding;
    }

    @Test
    void allowsOnlyOneHoldingPerUserAndPlayer() {
        User user = MarketFixtures.user(entityManager);
        Player player = MarketFixtures.player(entityManager);
        saved(user, player, 10);

        assertThatThrownBy(() -> saved(user, player, 5)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRejectsNegativeOrOverReservedHoldings() {
        Holding holding = saved(MarketFixtures.user(entityManager), MarketFixtures.player(entityManager), 10);

        assertThatThrownBy(() -> nativeUpdate("UPDATE holdings SET quantity = -1 WHERE id = " + holding.getId()))
                .isInstanceOf(PersistenceException.class);
        assertThatThrownBy(() -> nativeUpdate("UPDATE holdings SET reserved_quantity = 11 WHERE id = " + holding.getId()))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void findsPlayersWithoutAHoldingOfTheUser() {
        User user = MarketFixtures.user(entityManager);
        Player held = MarketFixtures.player(entityManager);
        Player missing = MarketFixtures.player(entityManager);
        saved(user, held, 100);

        assertThat(repository.findPlayerIdsWithoutHolding(user.getId()))
                .contains(missing.getId())
                .doesNotContain(held.getId());
    }

    @Test
    void sumsTokensAcrossUsers() {
        Player player = MarketFixtures.player(entityManager);
        saved(MarketFixtures.user(entityManager), player, 60);
        saved(MarketFixtures.user(entityManager), player, 40);

        assertThat(repository.sumQuantityByPlayerId(player.getId())).isEqualTo(100);
        assertThat(repository.sumQuantityByPlayerId(MarketFixtures.player(entityManager).getId())).isZero();
    }

    @Test
    void incrementsTheVersionOnEachChange() {
        Holding holding = saved(MarketFixtures.user(entityManager), MarketFixtures.player(entityManager), 10);
        Long initial = holding.getVersion();

        holding.reserve(2, NOW);
        entityManager.flush();

        assertThat(holding.getVersion()).isGreaterThan(initial);
    }

    @Test
    void offersNoDeleteOperation() {
        assertThat(Arrays.stream(HoldingRepository.class.getMethods()).map(java.lang.reflect.Method::getName))
                .noneMatch(name -> name.startsWith("delete"));
    }

    private void nativeUpdate(String sql) {
        entityManager.getEntityManager().createNativeQuery(sql).executeUpdate();
    }
}
