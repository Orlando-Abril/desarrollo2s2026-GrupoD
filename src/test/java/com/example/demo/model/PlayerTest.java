package com.example.demo.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerTest {

    @Test
    void builderDeberiaAsignarTodosLosCamposIncluyendoMultiplesPosiciones() {
        Player player = Player.builder()
                .id(1L)
                .externalId("ext-001")
                .fullName("Jude Bellingham")
                .team("Real Madrid")
                .league(League.LA_LIGA)
                .positions(Set.of(Position.MIDFIELDER, Position.FORWARD))
                .nationality("England")
                .age(21)
                .heightCm(186)
                .marketValue(new BigDecimal("180.00"))
                .build();

        assertThat(player.getId()).isEqualTo(1L);
        assertThat(player.getExternalId()).isEqualTo("ext-001");
        assertThat(player.getFullName()).isEqualTo("Jude Bellingham");
        assertThat(player.getTeam()).isEqualTo("Real Madrid");
        assertThat(player.getLeague()).isEqualTo(League.LA_LIGA);
        assertThat(player.getPositions()).containsExactlyInAnyOrder(Position.MIDFIELDER, Position.FORWARD);
        assertThat(player.getNationality()).isEqualTo("England");
        assertThat(player.getAge()).isEqualTo(21);
        assertThat(player.getHeightCm()).isEqualTo(186);
        assertThat(player.getMarketValue()).isEqualByComparingTo("180.00");
    }

    @Test
    void builderSinPositionsDeberiaUsarUnConjuntoVacioComoValorPorDefecto() {
        Player player = Player.builder()
                .fullName("Sin Posicion")
                .team("Equipo")
                .league(League.SERIE_A)
                .marketValue(BigDecimal.TEN)
                .build();

        assertThat(player.getPositions()).isNotNull().isEmpty();
    }

    @Test
    void constructorVacioMasSettersDeberianAsignarCadaCampo() {
        Player player = new Player();

        player.setId(2L);
        player.setExternalId("ext-002");
        player.setFullName("Mohamed Salah");
        player.setTeam("Liverpool");
        player.setLeague(League.PREMIER_LEAGUE);
        player.setPositions(Set.of(Position.FORWARD));
        player.setNationality("Egypt");
        player.setAge(32);
        player.setHeightCm(175);
        player.setMarketValue(new BigDecimal("65.00"));

        assertThat(player.getId()).isEqualTo(2L);
        assertThat(player.getExternalId()).isEqualTo("ext-002");
        assertThat(player.getFullName()).isEqualTo("Mohamed Salah");
        assertThat(player.getTeam()).isEqualTo("Liverpool");
        assertThat(player.getLeague()).isEqualTo(League.PREMIER_LEAGUE);
        assertThat(player.getPositions()).containsExactly(Position.FORWARD);
        assertThat(player.getNationality()).isEqualTo("Egypt");
        assertThat(player.getAge()).isEqualTo(32);
        assertThat(player.getHeightCm()).isEqualTo(175);
        assertThat(player.getMarketValue()).isEqualByComparingTo("65.00");
    }

    @Test
    void allArgsConstructorDeberiaAsignarTodosLosCampos() {
        Set<Position> positions = Set.of(Position.DEFENDER, Position.MIDFIELDER);

        Player player = new Player(3L, "ext-003", "William Saliba", "Arsenal", League.PREMIER_LEAGUE,
                positions, "France", 23, 192, new BigDecimal("80.00"));

        assertThat(player.getId()).isEqualTo(3L);
        assertThat(player.getExternalId()).isEqualTo("ext-003");
        assertThat(player.getFullName()).isEqualTo("William Saliba");
        assertThat(player.getTeam()).isEqualTo("Arsenal");
        assertThat(player.getLeague()).isEqualTo(League.PREMIER_LEAGUE);
        assertThat(player.getPositions()).containsExactlyInAnyOrder(Position.DEFENDER, Position.MIDFIELDER);
        assertThat(player.getNationality()).isEqualTo("France");
        assertThat(player.getAge()).isEqualTo(23);
        assertThat(player.getHeightCm()).isEqualTo(192);
        assertThat(player.getMarketValue()).isEqualByComparingTo("80.00");
    }
}
