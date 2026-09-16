package com.example.demo.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyTest {

    @Test
    void builderDeberiaAsignarTodosLosCampos() {
        User owner = User.builder().username("abril").build();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastUsed = now.plusMinutes(5);

        ApiKey apiKey = ApiKey.builder()
                .id(1L)
                .keyHash("hash-123")
                .keyPrefix("sk_abcd1234")
                .owner(owner)
                .active(false)
                .createdAt(now)
                .lastUsedAt(lastUsed)
                .build();

        assertThat(apiKey.getId()).isEqualTo(1L);
        assertThat(apiKey.getKeyHash()).isEqualTo("hash-123");
        assertThat(apiKey.getKeyPrefix()).isEqualTo("sk_abcd1234");
        assertThat(apiKey.getOwner()).isEqualTo(owner);
        assertThat(apiKey.isActive()).isFalse();
        assertThat(apiKey.getCreatedAt()).isEqualTo(now);
        assertThat(apiKey.getLastUsedAt()).isEqualTo(lastUsed);
    }

    @Test
    void builderSinActiveDeberiaUsarTrueComoValorPorDefecto() {
        ApiKey apiKey = ApiKey.builder()
                .keyHash("hash-456")
                .keyPrefix("sk_defau")
                .build();

        assertThat(apiKey.isActive()).isTrue();
    }

    @Test
    void constructorVacioMasSettersDeberianAsignarCadaCampo() {
        ApiKey apiKey = new ApiKey();
        User owner = new User();
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime lastUsedAt = createdAt.plusHours(1);

        apiKey.setId(2L);
        apiKey.setKeyHash("hash-789");
        apiKey.setKeyPrefix("sk_zzzz9999");
        apiKey.setOwner(owner);
        apiKey.setActive(true);
        apiKey.setCreatedAt(createdAt);
        apiKey.setLastUsedAt(lastUsedAt);

        assertThat(apiKey.getId()).isEqualTo(2L);
        assertThat(apiKey.getKeyHash()).isEqualTo("hash-789");
        assertThat(apiKey.getKeyPrefix()).isEqualTo("sk_zzzz9999");
        assertThat(apiKey.getOwner()).isEqualTo(owner);
        assertThat(apiKey.isActive()).isTrue();
        assertThat(apiKey.getCreatedAt()).isEqualTo(createdAt);
        assertThat(apiKey.getLastUsedAt()).isEqualTo(lastUsedAt);
    }

    @Test
    void allArgsConstructorDeberiaAsignarTodosLosCampos() {
        User owner = User.builder().username("con-todo").build();
        LocalDateTime createdAt = LocalDateTime.now();

        ApiKey apiKey = new ApiKey(3L, "hash-all", "sk_all12345", owner, true, createdAt, null);

        assertThat(apiKey.getId()).isEqualTo(3L);
        assertThat(apiKey.getKeyHash()).isEqualTo("hash-all");
        assertThat(apiKey.getKeyPrefix()).isEqualTo("sk_all12345");
        assertThat(apiKey.getOwner()).isEqualTo(owner);
        assertThat(apiKey.isActive()).isTrue();
        assertThat(apiKey.getCreatedAt()).isEqualTo(createdAt);
        assertThat(apiKey.getLastUsedAt()).isNull();
    }

    @Test
    void onCreateDeberiaCompletarCreatedAtSoloCuandoEstaVacio() {
        ApiKey apiKey = new ApiKey();
        assertThat(apiKey.getCreatedAt()).isNull();

        apiKey.onCreate();
        LocalDateTime firstCreatedAt = apiKey.getCreatedAt();
        assertThat(firstCreatedAt).isNotNull();

        apiKey.onCreate();
        assertThat(apiKey.getCreatedAt()).isEqualTo(firstCreatedAt);
    }
}
