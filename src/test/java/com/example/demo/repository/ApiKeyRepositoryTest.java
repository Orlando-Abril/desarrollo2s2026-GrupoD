package com.example.demo.repository;

import com.example.demo.model.ApiKey;
import com.example.demo.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integración con base H2 embebida (autoconfigurada por {@link DataJpaTest})
 * para validar la persistencia y las búsquedas de {@link ApiKeyRepository}.
 */
@DataJpaTest
class ApiKeyRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    private User persistirUsuario(String username, String email) {
        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash("hashed-password")
                .build();
        return userRepository.save(user);
    }

    @Test
    void deberiaGuardarYRecuperarUnaApiKeyPorId() {
        User owner = persistirUsuario("abril", "abril@ruidomarketing.com.ar");
        ApiKey apiKey = ApiKey.builder()
                .keyHash("hash-123")
                .keyPrefix("sk_abcd")
                .owner(owner)
                .build();

        ApiKey guardada = apiKeyRepository.save(apiKey);

        Optional<ApiKey> encontrada = apiKeyRepository.findById(guardada.getId());
        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().isActive()).isTrue();
        assertThat(encontrada.get().getCreatedAt()).isNotNull();
    }

    @Test
    void findByKeyHashDeberiaEncontrarLaApiKeyExistente() {
        User owner = persistirUsuario("orlando", "orlando@example.com");
        apiKeyRepository.save(ApiKey.builder()
                .keyHash("hash-unico")
                .keyPrefix("sk_1234")
                .owner(owner)
                .build());

        Optional<ApiKey> encontrada = apiKeyRepository.findByKeyHash("hash-unico");

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getKeyPrefix()).isEqualTo("sk_1234");
    }

    @Test
    void findByKeyHashDeberiaRetornarVacioSiNoExiste() {
        Optional<ApiKey> encontrada = apiKeyRepository.findByKeyHash("no-existe");

        assertThat(encontrada).isEmpty();
    }

    @Test
    void findByOwnerIdDeberiaEncontrarLaApiKeyDelUsuario() {
        User owner = persistirUsuario("gruod", "gruod@example.com");
        ApiKey apiKey = apiKeyRepository.save(ApiKey.builder()
                .keyHash("hash-owner")
                .keyPrefix("sk_owne")
                .owner(owner)
                .build());

        Optional<ApiKey> encontrada = apiKeyRepository.findByOwnerId(owner.getId());

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getId()).isEqualTo(apiKey.getId());
    }

    @Test
    void findByOwnerIdDeberiaRetornarVacioSiElUsuarioNoTieneApiKey() {
        User owner = persistirUsuario("sin-key", "sin-key@example.com");

        Optional<ApiKey> encontrada = apiKeyRepository.findByOwnerId(owner.getId());

        assertThat(encontrada).isEmpty();
    }
}
