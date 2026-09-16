package com.example.demo.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void builderDeberiaAsignarTodosLosCampos() {
        ApiKey apiKey = ApiKey.builder().keyHash("hash").keyPrefix("sk_prefix1").build();
        LocalDateTime createdAt = LocalDateTime.now();

        User user = User.builder()
                .id(10L)
                .username("abril")
                .email("abril@ruidomarketing.com.ar")
                .passwordHash("hashed-password")
                .role(Role.ADMIN)
                .balance(new BigDecimal("500.00"))
                .createdAt(createdAt)
                .apiKey(apiKey)
                .build();

        assertThat(user.getId()).isEqualTo(10L);
        assertThat(user.getUsername()).isEqualTo("abril");
        assertThat(user.getEmail()).isEqualTo("abril@ruidomarketing.com.ar");
        assertThat(user.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.getBalance()).isEqualByComparingTo("500.00");
        assertThat(user.getCreatedAt()).isEqualTo(createdAt);
        assertThat(user.getApiKey()).isEqualTo(apiKey);
    }

    @Test
    void builderSinRoleNiBalanceDeberiaUsarValoresPorDefecto() {
        User user = User.builder()
                .username("nuevo-usuario")
                .email("nuevo@example.com")
                .passwordHash("x")
                .build();

        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void constructorVacioMasSettersDeberianAsignarCadaCampo() {
        User user = new User();
        ApiKey apiKey = new ApiKey();
        LocalDateTime createdAt = LocalDateTime.now();

        user.setId(20L);
        user.setUsername("otro");
        user.setEmail("otro@example.com");
        user.setPasswordHash("otro-hash");
        user.setRole(Role.USER);
        user.setBalance(new BigDecimal("250.50"));
        user.setCreatedAt(createdAt);
        user.setApiKey(apiKey);

        assertThat(user.getId()).isEqualTo(20L);
        assertThat(user.getUsername()).isEqualTo("otro");
        assertThat(user.getEmail()).isEqualTo("otro@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("otro-hash");
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getBalance()).isEqualByComparingTo("250.50");
        assertThat(user.getCreatedAt()).isEqualTo(createdAt);
        assertThat(user.getApiKey()).isEqualTo(apiKey);
    }

    @Test
    void allArgsConstructorDeberiaAsignarTodosLosCampos() {
        ApiKey apiKey = new ApiKey();
        LocalDateTime createdAt = LocalDateTime.now();

        User user = new User(30L, "todo", "todo@example.com", "hash", Role.ADMIN,
                new BigDecimal("1.00"), createdAt, apiKey);

        assertThat(user.getId()).isEqualTo(30L);
        assertThat(user.getUsername()).isEqualTo("todo");
        assertThat(user.getEmail()).isEqualTo("todo@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("hash");
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.getBalance()).isEqualByComparingTo("1.00");
        assertThat(user.getCreatedAt()).isEqualTo(createdAt);
        assertThat(user.getApiKey()).isEqualTo(apiKey);
    }

    @Test
    void onCreateDeberiaCompletarCreatedAtSoloCuandoEstaVacio() {
        User user = new User();
        assertThat(user.getCreatedAt()).isNull();

        user.onCreate();
        LocalDateTime firstCreatedAt = user.getCreatedAt();
        assertThat(firstCreatedAt).isNotNull();

        user.onCreate();
        assertThat(user.getCreatedAt()).isEqualTo(firstCreatedAt);
    }
}
