package com.example.demo.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeagueTest {

    @Test
    void deberiaContenerLasCincoGrandesLigas() {
        assertThat(League.values()).containsExactly(
                League.PREMIER_LEAGUE,
                League.LA_LIGA,
                League.SERIE_A,
                League.BUNDESLIGA,
                League.LIGUE_1
        );
    }

    @Test
    void valueOfDeberiaResolverCadaConstante() {
        for (League league : League.values()) {
            assertThat(League.valueOf(league.name())).isEqualTo(league);
        }
    }
}
