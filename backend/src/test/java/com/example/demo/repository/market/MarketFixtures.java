package com.example.demo.repository.market;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.User;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.util.UUID;

/** Usuarios y jugadores persistidos para los tests de repositorio del mercado. */
final class MarketFixtures {

    private MarketFixtures() {
    }

    static User user(TestEntityManager entityManager) {
        String suffix = UUID.randomUUID().toString();
        return entityManager.persist(User.builder().username("u-" + suffix).email(suffix + "@test.local")
                .passwordHash("x").build());
    }

    static Player player(TestEntityManager entityManager) {
        return entityManager.persist(Player.builder().externalId("026-" + UUID.randomUUID()).fullName("Jugador")
                .team("Equipo").league(League.PREMIER_LEAGUE).marketValue(new BigDecimal("1.0000")).build());
    }
}
