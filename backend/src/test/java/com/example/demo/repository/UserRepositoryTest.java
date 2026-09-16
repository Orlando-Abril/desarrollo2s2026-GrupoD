package com.example.demo.repository;

import com.example.demo.model.Role;
import com.example.demo.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integración con base H2 embebida (autoconfigurada por {@link DataJpaTest})
 * para validar la persistencia y las búsquedas de {@link UserRepository}.
 */
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private User buildUser(String username, String email) {
        return User.builder()
                .username(username)
                .email(email)
                .passwordHash("hashed-password")
                .role(Role.USER)
                .balance(new BigDecimal("1000.00"))
                .build();
    }

    @Test
    void deberiaGuardarYRecuperarUnUsuarioPorId() {
        User guardado = userRepository.save(buildUser("abril", "abril@ruidomarketing.com.ar"));

        Optional<User> encontrado = userRepository.findById(guardado.getId());

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getUsername()).isEqualTo("abril");
        assertThat(encontrado.get().getCreatedAt()).isNotNull();
    }

    @Test
    void findByUsernameDeberiaEncontrarUsuarioExistente() {
        userRepository.save(buildUser("orlando", "orlando@example.com"));

        Optional<User> encontrado = userRepository.findByUsername("orlando");

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getEmail()).isEqualTo("orlando@example.com");
    }

    @Test
    void findByUsernameDeberiaRetornarVacioSiNoExiste() {
        Optional<User> encontrado = userRepository.findByUsername("no-existe");

        assertThat(encontrado).isEmpty();
    }

    @Test
    void findByEmailDeberiaEncontrarUsuarioExistente() {
        userRepository.save(buildUser("gruod", "gruod@example.com"));

        Optional<User> encontrado = userRepository.findByEmail("gruod@example.com");

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getUsername()).isEqualTo("gruod");
    }

    @Test
    void existsByUsernameDeberiaReflejarSiElUsernameEstaOcupado() {
        userRepository.save(buildUser("existente", "existente@example.com"));

        assertThat(userRepository.existsByUsername("existente")).isTrue();
        assertThat(userRepository.existsByUsername("libre")).isFalse();
    }

    @Test
    void existsByEmailDeberiaReflejarSiElEmailEstaOcupado() {
        userRepository.save(buildUser("con-email", "ocupado@example.com"));

        assertThat(userRepository.existsByEmail("ocupado@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("libre@example.com")).isFalse();
    }
}
