package com.example.demo.service;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import com.example.demo.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerCatalogQueryServiceTest {
    @Mock PlayerRepository repository;

    @Test
    void mapsLocalResultsWithoutCallingAnAdapter() {
        Player player = Player.builder().id(1L).externalId("10").fullName("Player")
                .team("Arsenal FC").league(League.PREMIER_LEAGUE).positions(Set.of(Position.FORWARD))
                .marketValue(new BigDecimal("1.00")).build();
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(player));
        var result = new PlayerCatalogQueryService(repository)
                .findPlayers(League.PREMIER_LEAGUE, "arsenal fc", Position.FORWARD);
        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.externalId()).isEqualTo("10");
            assertThat(dto.positions()).containsExactly(Position.FORWARD);
        });
    }

    @Test
    void emptyCatalogReturnsEmptyList() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of());
        var result = new PlayerCatalogQueryService(repository).findPlayers(null, null, null);
        assertThat(result).isEmpty();
    }
}
