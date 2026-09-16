package com.example.demo.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PositionTest {

    @Test
    void deberiaContenerLasCuatroPosicionesEsperadas() {
        assertThat(Position.values()).containsExactly(
                Position.GOALKEEPER,
                Position.DEFENDER,
                Position.MIDFIELDER,
                Position.FORWARD
        );
    }

    @Test
    void valueOfDeberiaResolverCadaConstante() {
        for (Position position : Position.values()) {
            assertThat(Position.valueOf(position.name())).isEqualTo(position);
        }
    }
}
