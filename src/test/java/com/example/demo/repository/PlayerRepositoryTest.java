package com.example.demo.repository;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integración con base H2 embebida (autoconfigurada por {@link DataJpaTest})
 * para validar la persistencia, las búsquedas y el filtrado dinámico mediante
 * {@link Specification} de {@link PlayerRepository}.
 */
@DataJpaTest
class PlayerRepositoryTest {

    @Autowired
    private PlayerRepository playerRepository;

    private Player messi;
    private Player deBruyne;
    private Player alisson;

    @BeforeEach
    void setUp() {
        messi = playerRepository.save(Player.builder()
                .externalId("ext-messi")
                .fullName("Lionel Messi")
                .team("Inter Miami")
                .league(League.LA_LIGA)
                .positions(Set.of(Position.FORWARD))
                .nationality("Argentina")
                .age(37)
                .heightCm(170)
                .marketValue(new BigDecimal("35000000"))
                .build());

        deBruyne = playerRepository.save(Player.builder()
                .externalId("ext-debruyne")
                .fullName("Kevin De Bruyne")
                .team("Manchester City")
                .league(League.PREMIER_LEAGUE)
                .positions(Set.of(Position.MIDFIELDER))
                .nationality("Belgica")
                .age(33)
                .heightCm(181)
                .marketValue(new BigDecimal("40000000"))
                .build());

        alisson = playerRepository.save(Player.builder()
                .externalId("ext-alisson")
                .fullName("Alisson Becker")
                .team("Liverpool")
                .league(League.PREMIER_LEAGUE)
                .positions(Set.of(Position.GOALKEEPER))
                .nationality("Brasil")
                .age(31)
                .heightCm(191)
                .marketValue(new BigDecimal("30000000"))
                .build());
    }

    @Test
    void deberiaGuardarYRecuperarUnJugadorPorId() {
        Optional<Player> encontrado = playerRepository.findById(messi.getId());

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getFullName()).isEqualTo("Lionel Messi");
        assertThat(encontrado.get().getPositions()).containsExactly(Position.FORWARD);
    }

    @Test
    void findByExternalIdDeberiaEncontrarElJugadorExistente() {
        Optional<Player> encontrado = playerRepository.findByExternalId("ext-debruyne");

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getTeam()).isEqualTo("Manchester City");
    }

    @Test
    void findByExternalIdDeberiaRetornarVacioSiNoExiste() {
        Optional<Player> encontrado = playerRepository.findByExternalId("no-existe");

        assertThat(encontrado).isEmpty();
    }

    @Test
    void specificationDeberiaFiltrarPorLiga() {
        Specification<Player> porLiga = tieneLiga(League.PREMIER_LEAGUE);

        List<Player> resultado = playerRepository.findAll(porLiga);

        assertThat(resultado)
                .extracting(Player::getFullName)
                .containsExactlyInAnyOrder("Kevin De Bruyne", "Alisson Becker");
    }

    @Test
    void specificationDeberiaFiltrarPorEquipo() {
        Specification<Player> porEquipo = tieneEquipo("Liverpool");

        List<Player> resultado = playerRepository.findAll(porEquipo);

        assertThat(resultado)
                .extracting(Player::getFullName)
                .containsExactly("Alisson Becker");
    }

    @Test
    void specificationDeberiaFiltrarPorPosicion() {
        Specification<Player> porPosicion = tienePosicion(Position.GOALKEEPER);

        List<Player> resultado = playerRepository.findAll(porPosicion);

        assertThat(resultado)
                .extracting(Player::getFullName)
                .containsExactly("Alisson Becker");
    }

    @Test
    void specificationDeberiaCombinarFiltrosDeLigaYPosicion() {
        Specification<Player> filtroCombinado = Specification.allOf(
                tieneLiga(League.PREMIER_LEAGUE),
                tienePosicion(Position.MIDFIELDER));

        List<Player> resultado = playerRepository.findAll(filtroCombinado);

        assertThat(resultado)
                .extracting(Player::getFullName)
                .containsExactly("Kevin De Bruyne");
    }

    @Test
    void specificationSinCriteriosDeberiaRetornarTodosLosJugadores() {
        List<Player> resultado = playerRepository.findAll(Specification.<Player>unrestricted());

        assertThat(resultado).hasSize(3);
    }

    private static Specification<Player> tieneLiga(League league) {
        return (root, query, cb) -> cb.equal(root.get("league"), league);
    }

    private static Specification<Player> tieneEquipo(String team) {
        return (root, query, cb) -> cb.equal(root.get("team"), team);
    }

    private static Specification<Player> tienePosicion(Position position) {
        return (root, query, cb) -> {
            query.distinct(true);
            return cb.isMember(position, root.get("positions"));
        };
    }
}
