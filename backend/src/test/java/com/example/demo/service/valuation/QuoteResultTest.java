package com.example.demo.service.valuation;

import com.example.demo.model.Position;
import com.example.demo.model.QuoteStatus;
import com.example.demo.service.valuation.strategy.Metric;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteResultTest {

    private static final BigDecimal VALUE = new BigDecimal("1.0960");
    private static final BigDecimal SCORE = new BigDecimal("0.9600");

    @Test
    void calculatedRequiresValueAndScore() {
        QuoteResult result = QuoteResult.calculated(VALUE, SCORE, Map.of());

        assertThat(result.status()).isEqualTo(QuoteStatus.CALCULATED);
        assertThat(result.value()).isEqualTo(VALUE);
        assertThat(result.score()).isEqualTo(SCORE);
        assertThatThrownBy(() -> QuoteResult.calculated(null, SCORE, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QuoteResult.calculated(VALUE, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void otherStatusesHaveNoValueScoreNorExclusions() {
        for (QuoteResult result : new QuoteResult[]{QuoteResult.noStats(), QuoteResult.insufficientHistory()}) {
            assertThat(result.value()).isNull();
            assertThat(result.score()).isNull();
            assertThat(result.excludedMetrics()).isEmpty();
        }
        assertThat(QuoteResult.noStats().status()).isEqualTo(QuoteStatus.NO_STATS);
        assertThat(QuoteResult.insufficientHistory().status()).isEqualTo(QuoteStatus.INSUFFICIENT_HISTORY);
    }

    @Test
    void noStatsRejectsValueOrScore() {
        assertThatThrownBy(() -> new QuoteResult(VALUE, null, QuoteStatus.NO_STATS, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuoteResult(null, SCORE, QuoteStatus.NO_STATS, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuoteResult(null, null, QuoteStatus.NO_STATS,
                Map.of(Position.MIDFIELDER, Set.of(Metric.DRIBBLES))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void statusIsRequired() {
        assertThatThrownBy(() -> new QuoteResult(VALUE, SCORE, null, Map.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void excludedMetricsAreAnImmutableDefensiveCopy() {
        Set<Metric> metrics = EnumSet.of(Metric.DRIBBLES);
        Map<Position, Set<Metric>> source = new HashMap<>(Map.of(Position.MIDFIELDER, metrics));

        QuoteResult result = QuoteResult.calculated(VALUE, SCORE, source);
        source.put(Position.FORWARD, Set.of(Metric.GOALS));
        metrics.add(Metric.SHOTS);

        assertThat(result.excludedMetrics()).containsExactly(Map.entry(Position.MIDFIELDER, Set.of(Metric.DRIBBLES)));
        assertThatThrownBy(() -> result.excludedMetrics().put(Position.FORWARD, Set.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.excludedMetrics().get(Position.MIDFIELDER).add(Metric.GOALS))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullExclusionsBecomeEmpty() {
        assertThat(QuoteResult.calculated(VALUE, SCORE, null).excludedMetrics()).isEmpty();
    }
}
