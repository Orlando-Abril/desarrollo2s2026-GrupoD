package com.example.demo.service.valuation;

import com.example.demo.model.PlayerQuote;
import com.example.demo.model.Position;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.example.demo.service.valuation.official.OfficialFixtures.T0;
import static com.example.demo.service.valuation.official.OfficialFixtures.quote;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValuationInputTest {

    private static final Instant NEWER = T0.plusSeconds(3600);

    @Test
    void playerIdIsRequired() {
        assertThatThrownBy(() -> new ValuationInput(null, Set.of(), null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void positionsAreRequiredButMayBeEmpty() {
        assertThatThrownBy(() -> new ValuationInput(1L, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThat(new ValuationInput(1L, Set.of(), null, null, null, null).positions()).isEmpty();
    }

    @Test
    void nullListsBecomeEmptyAndStatsAndPopulationMayBeNull() {
        ValuationInput input = new ValuationInput(1L, Set.of(Position.FORWARD), null, null, null, null);

        assertThat(input.previousQuotes()).isEmpty();
        assertThat(input.windows()).isEmpty();
        assertThat(input.stats()).isNull();
        assertThat(input.population()).isNull();
    }

    @Test
    void collectionsAreImmutableCopies() {
        Set<Position> positions = new HashSet<>(Set.of(Position.FORWARD));
        List<PlayerQuote> quotes = new ArrayList<>(List.of(quote("OFFICIAL", "1.00", T0)));

        ValuationInput input = new ValuationInput(1L, positions, null, null, quotes, List.of());
        positions.add(Position.DEFENDER);
        quotes.clear();

        assertThat(input.positions()).containsExactly(Position.FORWARD);
        assertThat(input.previousQuotes()).hasSize(1);
        assertThatThrownBy(() -> input.positions().add(Position.GOALKEEPER))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> input.previousQuotes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void previousQuotesOldestFirstAreRejected() {
        List<PlayerQuote> ascending = List.of(quote("OFFICIAL", "1.00", T0), quote("OFFICIAL", "1.05", NEWER));

        assertThatThrownBy(() -> new ValuationInput(7L, Set.of(), null, null, ascending, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("de la más nueva a la más vieja")
                .hasMessageContaining("7");
    }

    @Test
    void previousQuotesNewestFirstAndTiesAreAccepted() {
        List<PlayerQuote> descending = List.of(quote("OFFICIAL", "1.05", NEWER), quote("OFFICIAL", "1.00", T0));
        List<PlayerQuote> tie = List.of(quote("OFFICIAL", "1.05", T0), quote("OFFICIAL", "1.00", T0));

        assertThat(new ValuationInput(1L, Set.of(), null, null, descending, null).previousQuotes()).hasSize(2);
        assertThat(new ValuationInput(1L, Set.of(), null, null, tie, null).previousQuotes()).hasSize(2);
    }

    @Test
    void previousQuoteWithoutCalculatedAtIsRejected() {
        List<PlayerQuote> quotes = List.of(quote("OFFICIAL", "1.00", null));

        assertThatThrownBy(() -> new ValuationInput(1L, Set.of(), null, null, quotes, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("calculatedAt");
    }
}
