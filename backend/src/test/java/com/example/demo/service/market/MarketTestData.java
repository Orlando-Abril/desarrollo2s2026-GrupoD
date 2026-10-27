package com.example.demo.service.market;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.repository.PlayerRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Datos compartidos por los tests de la 026 que habilitan el superusuario. Todos los contextos de test usan
 * la misma base H2, así que estos tests limpian antes y después de cada caso para no dejar superusuario,
 * tenencias ni auditorías a los demás. Los jugadores no se borran: cada test crea los suyos.
 */
final class MarketTestData {

    private MarketTestData() {
    }

    static void clean(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("DELETE FROM holdings");
        jdbcTemplate.execute("DELETE FROM audit_logs");
        jdbcTemplate.execute("DELETE FROM api_keys");
        jdbcTemplate.execute("DELETE FROM users");
    }

    static Player player(PlayerRepository playerRepository) {
        return playerRepository.save(Player.builder()
                .externalId("026-" + UUID.randomUUID())
                .fullName("Jugador 026")
                .team("Equipo 026")
                .league(League.PREMIER_LEAGUE)
                .marketValue(new BigDecimal("1.0000"))
                .build());
    }
}
