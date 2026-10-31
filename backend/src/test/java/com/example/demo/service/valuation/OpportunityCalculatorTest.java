package com.example.demo.service.valuation;

import com.example.demo.dto.quote.OpportunityItemResponse;
import com.example.demo.model.League;
import com.example.demo.model.Position;
import com.example.demo.model.QuoteStatus;
import com.example.demo.service.valuation.OpportunityCalculator.Candidate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.example.demo.model.QuoteStatus.CALCULATED;
import static com.example.demo.model.QuoteStatus.INSUFFICIENT_HISTORY;
import static com.example.demo.model.QuoteStatus.NO_STATS;
import static org.assertj.core.api.Assertions.assertThat;

class OpportunityCalculatorTest {

    private static final Instant AT = Instant.parse("2026-10-28T03:00:00Z");

    @Test
    void anAlternativeAboveTheMarketPriceIsUndervalued() {
        OpportunityCalculator.Result result = classify(candidate(1, "A", "1.0000", "1.2000"));

        assertThat(result.overvalued()).isEmpty();
        OpportunityItemResponse item = result.undervalued().get(0);
        assertThat(item.marketValue()).isEqualTo(bd("1.0000"));
        assertThat(item.alternativeQuote()).isEqualTo(bd("1.2000"));
        assertThat(item.alternativeQuoteCalculatedAt()).isEqualTo(AT);
        assertThat(item.difference()).isEqualTo(bd("0.2000"));
        assertThat(item.differencePercent()).isEqualTo(bd("20.00"));
    }

    @Test
    void anAlternativeBelowTheMarketPriceIsOvervalued() {
        OpportunityCalculator.Result result = classify(candidate(1, "A", "2.0000", "1.5000"));

        assertThat(result.undervalued()).isEmpty();
        assertThat(result.overvalued().get(0).difference()).isEqualTo(bd("-0.5000"));
        assertThat(result.overvalued().get(0).differencePercent()).isEqualTo(bd("-25.00"));
    }

    @Test
    void aZeroDifferenceIsInNeitherList() {
        OpportunityCalculator.Result result = classify(candidate(1, "A", "1.5000", "1.5000"));

        assertThat(result.undervalued()).isEmpty();
        assertThat(result.overvalued()).isEmpty();
    }

    @Test
    void excludesNonCalculatedQuotesMissingAlternativesAndNonPositivePrices() {
        OpportunityCalculator.Result result = classify(
                candidate(1, "Alt no stats", "1.0000", "2.0000", NO_STATS, CALCULATED),
                candidate(2, "Alt insufficient", "1.0000", "2.0000", INSUFFICIENT_HISTORY, CALCULATED),
                candidate(3, "Market no stats", "1.0000", "2.0000", CALCULATED, NO_STATS),
                candidate(4, "Market insufficient", "1.0000", "2.0000", CALCULATED, INSUFFICIENT_HISTORY),
                candidate(5, "Market without quote", "1.0000", "2.0000", CALCULATED, null),
                candidate(6, "Without alternative", "1.0000", null, null, CALCULATED),
                candidate(7, "Null market value", null, "2.0000"),
                candidate(8, "Zero market value", "0.0000", "2.0000"),
                candidate(9, "Kept", "1.0000", "2.0000"));

        assertThat(result.undervalued()).extracting(OpportunityItemResponse::name).containsExactly("Kept");
        assertThat(result.overvalued()).isEmpty();
    }

    @Test
    void sortsByUnroundedAbsolutePercentThenNameAndId() {
        OpportunityCalculator.Result result = classify(
                candidate(1, "Small", "1.0000", "1.1000"),
                candidate(2, "Big", "1.0000", "1.5000"),
                candidate(5, "Tie", "3.0000", "3.3333"),
                candidate(4, "Tie", "3.0000", "3.3333"),
                candidate(3, "Almost tie", "3.0000", "3.3332"),
                candidate(6, "Down small", "2.0000", "1.9000"),
                candidate(7, "Down big", "2.0000", "1.0000"));

        assertThat(result.undervalued()).extracting(OpportunityItemResponse::playerId)
                .containsExactly(2L, 4L, 5L, 3L, 1L);
        assertThat(result.undervalued().get(1).differencePercent()).isEqualTo(bd("11.11"));
        assertThat(result.undervalued().get(3).differencePercent()).isEqualTo(bd("11.11"));
        assertThat(result.overvalued()).extracting(OpportunityItemResponse::playerId).containsExactly(7L, 6L);
    }

    @Test
    void cutsEachListAtTheLimit() {
        OpportunityCalculator.Result result = OpportunityCalculator.classify(List.of(
                candidate(1, "A", "1.0000", "1.1000"), candidate(2, "B", "1.0000", "1.2000"),
                candidate(3, "C", "1.0000", "1.3000"), candidate(4, "D", "1.0000", "0.9000"),
                candidate(5, "E", "1.0000", "0.8000")), 2);

        assertThat(result.undervalued()).extracting(OpportunityItemResponse::playerId).containsExactly(3L, 2L);
        assertThat(result.overvalued()).extracting(OpportunityItemResponse::playerId).containsExactly(5L, 4L);
    }

    @Test
    void roundsThePercentHalfEvenOnlyForPresentation() {
        OpportunityCalculator.Result result = classify(candidate(1, "A", "8.0000", "8.0002"),
                candidate(2, "B", "3.0000", "2.0000"));

        assertThat(result.undervalued().get(0).differencePercent()).isEqualTo(bd("0.00"));
        assertThat(result.undervalued().get(0).difference()).isEqualTo(bd("0.0002"));
        assertThat(result.overvalued().get(0).differencePercent()).isEqualTo(bd("-33.33"));
    }

    private static OpportunityCalculator.Result classify(Candidate... candidates) {
        return OpportunityCalculator.classify(List.of(candidates), 10);
    }

    private static Candidate candidate(long id, String name, String marketValue, String alternative) {
        return candidate(id, name, marketValue, alternative, alternative == null ? null : CALCULATED, CALCULATED);
    }

    private static Candidate candidate(long id, String name, String marketValue, String alternative,
                                       QuoteStatus alternativeStatus, QuoteStatus marketStatus) {
        return new Candidate(id, name, "Club", League.LA_LIGA, Set.of(Position.FORWARD),
                marketValue == null ? null : bd(marketValue), alternative == null ? null : bd(alternative),
                alternativeStatus, alternative == null ? null : AT, marketStatus);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
