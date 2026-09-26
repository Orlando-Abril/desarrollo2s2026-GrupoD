package com.example.demo.repository;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.PlayerStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PlayerStatsRepositoryTest {

    @Autowired
    private PlayerStatsRepository statsRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Player saka;

    @BeforeEach
    void setUp() {
        saka = playerRepository.save(Player.builder().externalId("3180").fullName("Bukayo Saka")
                .team("Arsenal FC").league(League.PREMIER_LEAGUE).marketValue(new BigDecimal("1.00")).build());
    }

    @Test
    void sharesPrimaryKeyWithPlayerAndKeepsOneRowPerPlayer() {
        statsRepository.saveAndFlush(stats(new PlayerStats(saka), 450, new BigDecimal("7.39")));
        entityManager.clear();

        PlayerStats updated = statsRepository.findById(saka.getId()).orElseThrow();
        updated.setGoals(4);
        statsRepository.saveAndFlush(updated);
        entityManager.clear();

        assertThat(statsRepository.count()).isOne();
        PlayerStats stored = statsRepository.findById(saka.getId()).orElseThrow();
        assertThat(stored.getPlayerId()).isEqualTo(saka.getId());
        assertThat(stored.getGoals()).isEqualTo(4);
        assertThat(stored.getRating()).isEqualByComparingTo("7.39");
    }

    @Test
    void replacingTheSetStoresNullMetricsInsteadOfKeepingPreviousValues() {
        statsRepository.saveAndFlush(stats(new PlayerStats(saka), 450, new BigDecimal("7.39")));
        entityManager.clear();

        PlayerStats replaced = statsRepository.findById(saka.getId()).orElseThrow();
        stats(replaced, null, null);
        statsRepository.saveAndFlush(replaced);
        entityManager.clear();

        PlayerStats stored = statsRepository.findById(saka.getId()).orElseThrow();
        assertThat(stored.getMinutesPlayed()).isNull();
        assertThat(stored.getRating()).isNull();
        assertThat(stored.getGoals()).isEqualTo(3);
    }

    @Test
    void doesNotModifyThePlayer() {
        statsRepository.saveAndFlush(stats(new PlayerStats(saka), 450, new BigDecimal("7.39")));
        entityManager.clear();

        Player player = playerRepository.findById(saka.getId()).orElseThrow();
        assertThat(player.getFullName()).isEqualTo("Bukayo Saka");
        assertThat(player.getTeam()).isEqualTo("Arsenal FC");
        assertThat(playerRepository.count()).isOne();
    }

    private PlayerStats stats(PlayerStats target, Integer minutes, BigDecimal rating) {
        target.setWhoscoredPlayerId("123761");
        target.setMinutesPlayed(minutes);
        target.setGoals(3);
        target.setAssists(1);
        target.setShots(19);
        target.setKeyPasses(13);
        target.setTackles(5);
        target.setYellowCards(0);
        target.setRedCards(0);
        target.setRating(rating);
        target.setFetchedAt(Instant.parse("2026-09-25T13:30:00Z"));
        return target;
    }
}
