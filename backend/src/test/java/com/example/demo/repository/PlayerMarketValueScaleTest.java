package com.example.demo.repository;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code market_value} guarda 4 decimales, igual que la cotización (feature 016). */
@DataJpaTest
class PlayerMarketValueScaleTest {

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void keepsFourDecimals() {
        Long id = save("1.0437");

        assertThat(playerRepository.findById(id).orElseThrow().getMarketValue()).isEqualByComparingTo("1.0437");
    }

    @Test
    void keepsEightIntegerDigits() {
        Long id = save("35000000");

        assertThat(playerRepository.findById(id).orElseThrow().getMarketValue()).isEqualByComparingTo("35000000");
    }

    private Long save(String marketValue) {
        Long id = playerRepository.save(Player.builder()
                .fullName("Bukayo Saka")
                .team("Arsenal FC")
                .league(League.PREMIER_LEAGUE)
                .positions(Set.of(Position.FORWARD))
                .marketValue(new BigDecimal(marketValue))
                .build()).getId();
        entityManager.flush();
        entityManager.clear();
        return id;
    }
}
