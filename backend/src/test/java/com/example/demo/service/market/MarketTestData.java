package com.example.demo.service.market;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.repository.PlayerRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Datos compartidos por los tests del mercado (026 y 027). Todos los contextos de test usan la misma base H2, así
 * que estos tests limpian antes y después de cada caso para no dejar superusuario, órdenes, operaciones,
 * tenencias ni auditorías a los demás. Los jugadores no se borran: cada test crea los suyos. El orden de borrado
 * respeta las claves foráneas: las operaciones referencian órdenes, y las órdenes y tenencias, usuarios.
 */
public final class MarketTestData {

    private MarketTestData() {
    }

    public static void clean(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("DELETE FROM trades");
        jdbcTemplate.execute("DELETE FROM orders");
        jdbcTemplate.execute("DELETE FROM holdings");
        jdbcTemplate.execute("DELETE FROM audit_logs");
        jdbcTemplate.execute("DELETE FROM api_keys");
        jdbcTemplate.execute("DELETE FROM users");
    }

    public static Player player(PlayerRepository playerRepository) {
        return playerRepository.save(Player.builder()
                .externalId("026-" + UUID.randomUUID())
                .fullName("Jugador 026")
                .team("Equipo 026")
                .league(League.PREMIER_LEAGUE)
                .marketValue(new BigDecimal("1.0000"))
                .build());
    }
}
