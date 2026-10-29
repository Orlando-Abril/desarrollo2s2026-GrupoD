package com.example.demo.repository.market;

import com.example.demo.model.User;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@UnitTestProfile
class MarketUserRepositoryTest {

    @Autowired private MarketUserRepository repository;
    @Autowired private TestEntityManager entityManager;

    @Test
    void readsTheBalanceOfANewUser() {
        User user = MarketFixtures.user(entityManager);

        assertThat(repository.findBalanceById(user.getId())).get(org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL)
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void addsToTheBalance() {
        User user = MarketFixtures.user(entityManager);

        assertThat(repository.addToBalance(user.getId(), new BigDecimal("25.50"))).isEqualTo(1);
        assertThat(repository.addToBalance(-1L, BigDecimal.ONE)).isZero();
        assertThat(repository.findBalanceById(user.getId())).get(org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL)
                .isEqualByComparingTo("1025.50");
    }

    @Test
    void locksTheUser() {
        User user = MarketFixtures.user(entityManager);

        assertThat(repository.findLockedById(user.getId())).get().extracting(User::getId).isEqualTo(user.getId());
    }

    /**
     * {@code users.balance} es {@code numeric(38,4)} desde la spec 027 (research D5): un total con 4 decimales
     * se guarda sin redondear.
     */
    @Test
    void balanceColumnKeepsFourDecimals() {
        User user = MarketFixtures.user(entityManager);

        repository.addToBalance(user.getId(), new BigDecimal("3.1311"));

        assertThat(repository.findBalanceById(user.getId())).get(org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL)
                .isEqualByComparingTo("1003.1311");
    }
}
