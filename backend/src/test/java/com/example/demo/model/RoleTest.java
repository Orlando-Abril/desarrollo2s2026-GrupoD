package com.example.demo.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @Test
    void deberiaContenerLosDosValoresEsperados() {
        assertThat(Role.values()).containsExactly(Role.USER, Role.ADMIN);
    }

    @Test
    void valueOfDeberiaResolverCadaConstante() {
        assertThat(Role.valueOf("USER")).isEqualTo(Role.USER);
        assertThat(Role.valueOf("ADMIN")).isEqualTo(Role.ADMIN);
    }
}
