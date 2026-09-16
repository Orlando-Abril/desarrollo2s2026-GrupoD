package com.example.demo.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-key-at-least-32-bytes-long-0123456789";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 3600000L);

    @Test
    void generateTokenConUsernameValidoDeberiaDevolverUnTokenNoVacio() {
        String token = jwtUtil.generateToken("abril");

        assertThat(token).isNotBlank();
    }

    @Test
    void generateTokenConUsernameNuloDeberiaLanzarIllegalArgumentException() {
        assertThatThrownBy(() -> jwtUtil.generateToken(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateTokenConUsernameVacioDeberiaLanzarIllegalArgumentException() {
        assertThatThrownBy(() -> jwtUtil.generateToken(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isTokenValidDeberiaSerTrueAntesDeExpirar() {
        String token = jwtUtil.generateToken("abril");

        assertThat(jwtUtil.isTokenValid(token)).isTrue();
    }

    @Test
    void extractUsernameDeberiaDevolverElUsernameOriginal() {
        String token = jwtUtil.generateToken("abril");

        assertThat(jwtUtil.extractUsername(token)).isEqualTo("abril");
    }

    @Test
    void isTokenValidDeberiaSerFalseParaUnTokenExpirado() {
        JwtUtil jwtUtilConExpiracionEnElPasado = new JwtUtil(SECRET, -1000L);
        String tokenExpirado = jwtUtilConExpiracionEnElPasado.generateToken("abril");

        assertThat(jwtUtil.isTokenValid(tokenExpirado)).isFalse();
    }

    @Test
    void extractUsernameDeberiaLanzarExcepcionDeJjwtParaUnTokenConFirmaAlterada() {
        String token = jwtUtil.generateToken("abril");
        String tokenConFirmaAlterada = token.substring(0, token.length() - 1)
                + (token.endsWith("a") ? "b" : "a");

        assertThatThrownBy(() -> jwtUtil.extractUsername(tokenConFirmaAlterada))
                .isInstanceOf(JwtException.class);
    }
}
