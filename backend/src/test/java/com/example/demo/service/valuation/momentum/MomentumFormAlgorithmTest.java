package com.example.demo.service.valuation.momentum;

import com.example.demo.model.PlayerQuote;
import com.example.demo.model.PlayerStats;
import com.example.demo.model.Position;
import com.example.demo.model.QuoteStatus;
import com.example.demo.service.valuation.QuoteResult;
import com.example.demo.service.valuation.StatsWindow;
import com.example.demo.service.valuation.ValuationInput;
import com.example.demo.service.valuation.strategy.MomentumStrategyV1;
import com.example.demo.service.valuation.strategy.OfficialStrategyV1;
import com.example.demo.service.valuation.strategy.StrategyType;
import com.example.demo.service.valuation.strategy.ValuationStrategyDefinition;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static com.example.demo.service.valuation.momentum.MomentumFixtures.PLAYER_ID;
import static com.example.demo.service.valuation.momentum.MomentumFixtures.idle;
import static com.example.demo.service.valuation.momentum.MomentumFixtures.input;
import static com.example.demo.service.valuation.momentum.MomentumFixtures.momentumQuote;
import static com.example.demo.service.valuation.momentum.MomentumFixtures.quote;
import static com.example.demo.service.valuation.momentum.MomentumFixtures.repeated;
import static com.example.demo.service.valuation.momentum.MomentumFixtures.runOfWindow;
import static com.example.demo.service.valuation.momentum.MomentumFixtures.season;
import static com.example.demo.service.valuation.momentum.MomentumFixtures.window;
import static com.example.demo.service.valuation.momentum.MomentumFixtures.windows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Escenarios de la spec 019 y ejemplos de {@code contracts/momentum-algorithm.md} §5. */
class MomentumFormAlgorithmTest {

    private static final MomentumFormAlgorithm ALGORITHM = new MomentumFormAlgorithm();
    private static final ValuationStrategyDefinition MOMENTUM = MomentumStrategyV1.DEFINITION;

    // Contrato §5.1: mediocampista, 900' con 2 goles, 1 asistencia, 10 tiros, 15 pases clave, 10 gambetas, 20 quites
    private static PlayerStats midfielderSeason() {
        return season(900, 2, 1, 10, 15, 10, 20, "7.00");
    }

    private static List<StatsWindow> midfielderWindows() {
        return windows(
                window(90, 0, 0, 1, 1, 1, 2, "6.50", 0, 0),
                window(90, 1, 0, 2, 2, 1, 2, "7.50", 0, 0),
                window(90, 1, 1, 2, 3, 2, 3, "8.00", 1, 0));
    }

    // Contrato §5.5: delantero con 900', 5 goles, 2 asistencias, 20 tiros, 5 pases clave, 10 gambetas, 1 quite
    private static PlayerStats forwardSeason() {
        return season(900, 5, 2, 20, 5, 10, 1, "7.00");
    }

    private static MomentumFixtures.Spec forwardTop() {
        return window(90, 2, 1, 5, 2, 3, 0, "10.00", 0, 0);
    }

    // Promedio por 90' de 1 en cada contador
    private static PlayerStats evenSeason() {
        return season(900, 10, 10, 10, 10, 10, 10, "7.00");
    }

    private static MomentumFixtures.Spec evenNeutral() {
        return window(90, 1, 1, 1, 1, 1, 1, "7.00", 0, 0);
    }

    private static MomentumFixtures.Spec evenTop() {
        return window(90, 2, 2, 2, 2, 2, 2, "8.50", 0, 0);
    }

    private static MomentumFixtures.Spec evenBottom(int minutes) {
        return window(minutes, 0, 0, 0, 0, 0, 0, "5.50", 0, 0);
    }

    private static QuoteResult calculate(ValuationInput input) {
        return ALGORITHM.calculate(input, MOMENTUM);
    }

    private static QuoteResult calculate(Position position, PlayerStats stats, List<PlayerQuote> quotes,
                                         List<StatsWindow> windows) {
        return calculate(input(position, stats, quotes, windows));
    }

    private static void assertCalculated(QuoteResult result, String score, String value) {
        assertThat(result.status()).isEqualTo(QuoteStatus.CALCULATED);
        assertThat(result.score()).isEqualByComparingTo(score);
        assertThat(result.value()).isEqualByComparingTo(value);
        assertThat(result.score().scale()).isEqualTo(4);
        assertThat(result.value().scale()).isEqualTo(4);
    }

    private static BigDecimal value(QuoteResult result) {
        return result.value();
    }

    @Test
    void interpretsMomentumDefinitions() {
        assertThat(ALGORITHM.type()).isEqualTo(StrategyType.MOMENTUM);
        assertThat(ALGORITHM.preparePopulation(List.of())).isNull();
    }

    @Nested
    class RecentForm {

        @Test
        void contractExample51MidfielderOnTheRiseWithARecentYellow() {
            assertCalculated(calculate(Position.MIDFIELDER, midfielderSeason(), List.of(momentumQuote("1.2000")),
                    midfielderWindows()), "0.6097", "1.2395");
        }

        @Test
        void contractExample52PlayingExactlyHisAverageDoesNotMove() {
            QuoteResult result = calculate(Position.MIDFIELDER, season(450, 5, 5, 10, 10, 5, 10, "7.00"), List.of(),
                    repeated(5, window(90, 1, 1, 2, 2, 1, 2, "7.00", 0, 0)));

            assertCalculated(result, "0.5000", "1.0000");
        }

        @Test
        void contractExample55ForwardAtTheTopRisesFifteenPercent() {
            assertCalculated(calculate(Position.FORWARD, forwardSeason(), List.of(), repeated(5, forwardTop())),
                    "1.0000", "1.1500");
        }

        @Test
        void contractExample58TwoPositionsAverageTheirWeights() {
            QuoteResult result = calculate(input(Set.of(Position.MIDFIELDER, Position.FORWARD), midfielderSeason(),
                    List.of(momentumQuote("1.2000")), midfielderWindows()));

            assertCalculated(result, "0.6201", "1.2433");
        }

        @Test
        void contractExample511DropsWindowsBeforeTheSeasonReset() {
            MomentumFixtures.Spec lastSeason = window(90, 0, 0, 0, 0, 0, 0, "4.00", 0, 0);
            List<StatsWindow> windows = windows(lastSeason, lastSeason, lastSeason, forwardTop().withSeasonReset(),
                    forwardTop());

            assertCalculated(calculate(Position.FORWARD, forwardSeason(), List.of(), windows), "1.0000", "1.1500");
        }

        @Test
        void windowsBeforeTheResetDoNotChangeTheResult() {
            List<StatsWindow> bad = windows(evenBottom(90), evenBottom(90), evenTop().withSeasonReset(), evenTop());
            List<StatsWindow> good = windows(evenTop(), evenTop(), evenTop().withSeasonReset(), evenTop());

            assertThat(calculate(Position.FORWARD, evenSeason(), List.of(), bad))
                    .isEqualTo(calculate(Position.FORWARD, evenSeason(), List.of(), good));
        }

        @Test
        void moreGoalsAndShotsPerNinetyMeansMoreVariationWithinTheCap() {
            List<StatsWindow> better = repeated(5, window(90, 1, 0, 3, 1, 1, 0, "7.00", 0, 0));
            List<StatsWindow> worse = repeated(5, window(90, 0, 0, 1, 1, 1, 0, "7.00", 0, 0));

            BigDecimal high = value(calculate(Position.FORWARD, forwardSeason(), List.of(), better));
            BigDecimal low = value(calculate(Position.FORWARD, forwardSeason(), List.of(), worse));

            assertThat(high).isGreaterThan(low).isBetween(new BigDecimal("0.85"), new BigDecimal("1.15"));
            assertThat(low).isBetween(new BigDecimal("0.85"), new BigDecimal("1.15"));
        }

        @Test
        void recentWindowsWeighMoreThanOldOnes() {
            MomentumFixtures.Spec high = window(90, 1, 1, 2, 3, 2, 3, "8.00", 0, 0);
            MomentumFixtures.Spec low = window(90, 0, 0, 0, 0, 0, 0, "6.00", 0, 0);

            BigDecimal risingNow = value(calculate(Position.MIDFIELDER, midfielderSeason(), List.of(),
                    windows(low, low, low, high, high)));
            BigDecimal risingBefore = value(calculate(Position.MIDFIELDER, midfielderSeason(), List.of(),
                    windows(high, high, low, low, low)));

            assertThat(risingNow).isGreaterThan(risingBefore);
        }

        @Test
        void onlyTheFiveMostRecentWindowsCount() {
            List<MomentumFixtures.Spec> recent = Collections.nCopies(5, evenNeutral());
            List<MomentumFixtures.Spec> withBadOld = new ArrayList<>(List.of(evenBottom(90), evenBottom(90)));
            withBadOld.addAll(recent);
            List<MomentumFixtures.Spec> withGoodOld = new ArrayList<>(List.of(evenTop(), evenTop()));
            withGoodOld.addAll(recent);

            assertThat(calculate(Position.MIDFIELDER, evenSeason(), List.of(), windows(withBadOld)))
                    .isEqualTo(calculate(Position.MIDFIELDER, evenSeason(), List.of(), windows(withGoodOld)));
        }

        @Test
        void eachPositionUsesItsOwnWeights() {
            List<StatsWindow> moreTackles = repeated(5, window(90, 1, 1, 1, 1, 1, 2, "7.00", 0, 0));
            List<StatsWindow> moreGoals = repeated(5, window(90, 2, 1, 1, 1, 1, 1, "7.00", 0, 0));

            assertThat(value(calculate(Position.DEFENDER, evenSeason(), List.of(), moreTackles)))
                    .isGreaterThan(value(calculate(Position.FORWARD, evenSeason(), List.of(), moreTackles)));
            assertThat(value(calculate(Position.FORWARD, evenSeason(), List.of(), moreGoals)))
                    .isGreaterThan(value(calculate(Position.DEFENDER, evenSeason(), List.of(), moreGoals)));
        }

        @Test
        void counterAtOneAndAHalfTimesHisAverageScoresSeventyFiveHundredths() {
            PlayerStats onlyGoals = season(810, 6, null, null, null, null, null, null);

            assertCalculated(calculate(Position.FORWARD, onlyGoals, List.of(),
                    windows(window(90, 1, null, null, null, null, null, null, 0, 0))), "0.7500", "1.0750");
            assertCalculated(calculate(Position.FORWARD, onlyGoals, List.of(),
                    windows(window(90, 0, null, null, null, null, null, null, 0, 0))), "0.0000", "0.8500");
        }

        @Test
        void ratingMovesByTheBand() {
            PlayerStats onlyRating = season(900, null, null, null, null, null, null, "7.00");

            assertThat(calculate(Position.FORWARD, onlyRating, List.of(),
                    windows(window(90, null, null, null, null, null, null, "7.00", 0, 0))).score())
                    .isEqualByComparingTo("0.5");
            assertThat(calculate(Position.FORWARD, onlyRating, List.of(),
                    windows(window(90, null, null, null, null, null, null, "8.50", 0, 0))).score())
                    .isEqualByComparingTo("1");
            assertThat(calculate(Position.FORWARD, onlyRating, List.of(),
                    windows(window(90, null, null, null, null, null, null, "5.50", 0, 0))).score())
                    .isEqualByComparingTo("0");
        }

        @Test
        void missingMetricIsExcludedInsteadOfLoweringThePrice() {
            QuoteResult complete = calculate(Position.FORWARD, evenSeason(), List.of(), windows(evenTop()));
            QuoteResult withoutDribbles = calculate(Position.FORWARD, evenSeason(), List.of(),
                    windows(window(90, 2, 2, 2, 2, null, 2, "8.50", 0, 0)));
            QuoteResult zeroDribbles = calculate(Position.FORWARD, evenSeason(), List.of(),
                    windows(window(90, 2, 2, 2, 2, 0, 2, "8.50", 0, 0)));

            assertThat(withoutDribbles).isEqualTo(complete);
            assertThat(zeroDribbles.value()).isLessThan(complete.value());
        }

        @Test
        void metricWithoutSeasonAverageIsExcluded() {
            PlayerStats noSeasonDribbles = season(900, 10, 10, 10, 10, 0, 10, "7.00");

            assertCalculated(calculate(Position.FORWARD, noSeasonDribbles, List.of(),
                    windows(window(90, 2, 2, 2, 2, 0, 2, "8.50", 0, 0))), "1.0000", "1.1500");
        }

        @Test
        void withoutSeasonStatsPerformanceIsNeutral() {
            assertCalculated(calculate(Position.FORWARD, null, List.of(), repeated(5, evenTop())), "0.5000", "1.0000");
        }

        @Test
        void aShortBadWindowHurtsLessThanAFullBadOne() {
            BigDecimal shortBad = value(calculate(Position.MIDFIELDER, evenSeason(), List.of(),
                    windows(evenTop(), evenBottom(10))));
            BigDecimal fullBad = value(calculate(Position.MIDFIELDER, evenSeason(), List.of(),
                    windows(evenTop(), evenBottom(90))));

            assertThat(shortBad).isGreaterThan(fullBad);
        }
    }

    @Nested
    class Cards {

        private List<StatsWindow> neutralWith(int index, Integer yellow, Integer red) {
            List<MomentumFixtures.Spec> specs = new ArrayList<>(Collections.nCopies(5, evenNeutral()));
            specs.set(index, evenNeutral().withCards(yellow, red));
            return windows(specs);
        }

        private BigDecimal neutralValue(List<StatsWindow> windows) {
            return value(calculate(Position.MIDFIELDER, evenSeason(), List.of(), windows));
        }

        @Test
        void aYellowLowersTheVariation() {
            assertThat(neutralValue(neutralWith(4, 1, 0))).isLessThan(neutralValue(neutralWith(4, 0, 0)));
        }

        @Test
        void aRedCostsMoreThanAYellow() {
            assertThat(neutralValue(neutralWith(4, 0, 1))).isLessThan(neutralValue(neutralWith(4, 1, 0)));
        }

        @Test
        void aRecentCardCostsMoreThanAnOldOne() {
            assertThat(neutralValue(neutralWith(4, 1, 0))).isLessThan(neutralValue(neutralWith(0, 1, 0)));
        }

        @Test
        void manyCardsNeverGoBelowTheCap() {
            List<StatsWindow> reds = repeated(5, evenNeutral().withCards(0, 5));

            assertCalculated(calculate(Position.MIDFIELDER, evenSeason(), List.of(momentumQuote("2.0000")), reds),
                    "0.0000", "1.7000");
        }

        @Test
        void nullCardsCountAsZero() {
            assertThat(calculate(Position.MIDFIELDER, evenSeason(), List.of(), neutralWith(4, null, null)))
                    .isEqualTo(calculate(Position.MIDFIELDER, evenSeason(), List.of(), neutralWith(4, 0, 0)));
        }

        @Test
        void aCardInAWindowWithoutMinutesStillCounts() {
            List<StatsWindow> clean = repeated(5, idle());
            List<StatsWindow> booked = windows(idle(), idle(), idle(), idle(), idle().withCards(1, 0));

            assertThat(neutralValue(booked)).isLessThan(neutralValue(clean));
        }

        @Test
        void contractExample57ForwardAtTheTopWithARecentRed() {
            List<MomentumFixtures.Spec> specs = new ArrayList<>(Collections.nCopies(5, forwardTop()));
            specs.set(4, forwardTop().withCards(0, 1));

            assertCalculated(calculate(Position.FORWARD, forwardSeason(), List.of(), windows(specs)), "0.8500", "1.1050");
        }
    }

    @Nested
    class Availability {

        @Test
        void contractExample53MidfielderWithoutMinutes() {
            assertCalculated(calculate(Position.MIDFIELDER, midfielderSeason(), List.of(), repeated(5, idle())),
                    "0.4000", "0.9700");
        }

        @Test
        void contractExample54GoalkeeperWithoutMinutes() {
            PlayerStats goalkeeper = season(900, 0, 0, 0, 5, 0, 5, "6.80");

            assertCalculated(calculate(Position.GOALKEEPER, goalkeeper, List.of(momentumQuote("1.1000")),
                    repeated(5, idle())), "0.2000", "1.0010");
        }

        @Test
        void contractExample56ForwardAtTheTopWithHalfTheMinutes() {
            assertCalculated(calculate(Position.FORWARD, forwardSeason(), List.of(),
                    repeated(5, window(45, 1, 1, 2, 1, 1, 0, "10.00", 0, 0))), "0.9500", "1.1350");
        }

        @Test
        void fewerMinutesWithTheSamePerformanceMeansLessVariation() {
            BigDecimal full = value(calculate(Position.MIDFIELDER, evenSeason(), List.of(),
                    repeated(5, window(90, 2, 2, 2, 2, 2, 2, "7.00", 0, 0))));
            BigDecimal half = value(calculate(Position.MIDFIELDER, evenSeason(), List.of(),
                    repeated(5, window(45, 1, 1, 1, 1, 1, 1, "7.00", 0, 0))));

            assertThat(half).isLessThan(full);
        }

        @Test
        void withoutMinutesThePriceGoesDown() {
            QuoteResult result = calculate(Position.FORWARD, forwardSeason(), List.of(momentumQuote("1.5000")),
                    repeated(5, idle()));

            assertThat(result.status()).isEqualTo(QuoteStatus.CALCULATED);
            assertThat(result.value()).isLessThan(new BigDecimal("1.5000"));
        }

        @Test
        void missingTheLastWindowCostsMoreThanMissingTheFirst() {
            BigDecimal missedLast = value(calculate(Position.MIDFIELDER, evenSeason(), List.of(),
                    windows(evenNeutral(), evenNeutral(), evenNeutral(), evenNeutral(), idle())));
            BigDecimal missedFirst = value(calculate(Position.MIDFIELDER, evenSeason(), List.of(),
                    windows(idle(), evenNeutral(), evenNeutral(), evenNeutral(), evenNeutral())));

            assertThat(missedLast).isLessThan(missedFirst);
        }

        @Test
        void availabilityOfAWindowIsCappedAtOne() {
            assertCalculated(calculate(Position.MIDFIELDER, evenSeason(), List.of(),
                    windows(window(180, 2, 2, 2, 2, 2, 2, "7.00", 0, 0))), "0.5000", "1.0000");
        }

        @Test
        void nullMinutesCountAsZero() {
            assertThat(calculate(Position.MIDFIELDER, evenSeason(), List.of(),
                    windows(evenNeutral(), evenNeutral().withMinutes(null))))
                    .isEqualTo(calculate(Position.MIDFIELDER, evenSeason(), List.of(),
                            windows(evenNeutral(), evenNeutral().withMinutes(0))));
        }
    }

    @Nested
    class HistoryAndValidation {

        @Test
        void withoutWindowsHistoryIsInsufficient() {
            QuoteResult result = calculate(Position.FORWARD, forwardSeason(), List.of(momentumQuote("1.2000")),
                    List.of());

            assertThat(result.status()).isEqualTo(QuoteStatus.INSUFFICIENT_HISTORY);
            assertThat(result.value()).isNull();
            assertThat(result.score()).isNull();
        }

        @Test
        void oneWindowIsEnoughAndStartsFromTheBaseValue() {
            assertCalculated(calculate(Position.FORWARD, forwardSeason(), List.of(), windows(forwardTop())),
                    "1.0000", "1.1500");
        }

        @Test
        void appliesOverThePreviousMomentumQuote() {
            assertCalculated(calculate(Position.FORWARD, forwardSeason(), List.of(momentumQuote("1.2000")),
                    windows(forwardTop())), "1.0000", "1.3800");
        }

        @Test
        void appliesOverAnInsufficientHistoryQuote() {
            PlayerQuote previous = quote("MOMENTUM", "1.0000", QuoteStatus.INSUFFICIENT_HISTORY,
                    MomentumFixtures.PREVIOUS_AT, MomentumFixtures.PREVIOUS_RUN);

            assertCalculated(calculate(Position.FORWARD, forwardSeason(), List.of(previous), windows(forwardTop())),
                    "1.0000", "1.1500");
        }

        // (5) La primera corrida con un snapshot previo se cubre en QuoteRecalculationE2ETest.

        @Test
        void contractExample510WithoutNewDataKeepsTheValue() {
            PlayerQuote sameRun = momentumQuote("1.2000", MomentumFixtures.PREVIOUS_AT, runOfWindow(2));

            assertCalculated(calculate(Position.MIDFIELDER, midfielderSeason(), List.of(sameRun), midfielderWindows()),
                    "0.6097", "1.2000");
        }

        @Test
        void aPreviousQuoteAfterTheLatestSnapshotMeansNoNewData() {
            List<StatsWindow> windows = midfielderWindows();
            Instant afterSnapshot = windows.get(2).toCapturedAt().plus(Duration.ofDays(1));
            PlayerQuote laterRun = momentumQuote("1.2000", afterSnapshot, new UUID(9, 9));

            assertCalculated(calculate(Position.MIDFIELDER, midfielderSeason(), List.of(laterRun), windows),
                    "0.6097", "1.2000");
        }

        @Test
        void anotherRunBeforeTheLatestSnapshotIsNewData() {
            List<StatsWindow> windows = midfielderWindows();
            Instant beforeSnapshot = windows.get(2).toCapturedAt().minus(Duration.ofDays(1));
            PlayerQuote earlierRun = momentumQuote("1.2000", beforeSnapshot, new UUID(9, 9));

            assertCalculated(calculate(Position.MIDFIELDER, midfielderSeason(), List.of(earlierRun), windows),
                    "0.6097", "1.2395");
        }

        @Test
        void withoutPositionsThereAreNoStats() {
            QuoteResult result = calculate(input(Set.of(), forwardSeason(), List.of(), windows(forwardTop())));

            assertThat(result.status()).isEqualTo(QuoteStatus.NO_STATS);
            assertThat(result.value()).isNull();
        }

        @Test
        void rejectsPreviousQuotesOfAnotherStrategy() {
            ValuationInput input = input(Position.FORWARD, forwardSeason(),
                    List.of(quote("OFFICIAL", "1.1000", MomentumFixtures.PREVIOUS_AT, MomentumFixtures.PREVIOUS_RUN)),
                    windows(forwardTop()));

            assertThatThrownBy(() -> calculate(input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(String.valueOf(PLAYER_ID))
                    .hasMessageContaining("OFFICIAL");
        }

        @Test
        void rejectsAPreviousQuoteWithoutValue() {
            ValuationInput input = input(Position.FORWARD, forwardSeason(), List.of(momentumQuote(null)),
                    windows(forwardTop()));

            assertThatThrownBy(() -> calculate(input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(String.valueOf(PLAYER_ID));
        }

        @Test
        void rejectsDefinitionsOfAnotherType() {
            ValuationInput input = input(Position.FORWARD, forwardSeason(), List.of(), windows(forwardTop()));

            assertThatThrownBy(() -> ALGORITHM.calculate(input, OfficialStrategyV1.DEFINITION))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("OFFICIAL");
        }
    }

    @Nested
    class Determinism {

        @Test
        void sameInputSameResult() {
            ValuationInput input = input(Position.MIDFIELDER, midfielderSeason(), List.of(momentumQuote("1.2000")),
                    midfielderWindows());

            assertThat(calculate(input)).isEqualTo(calculate(input));
        }

        @Test
        void windowOrderDoesNotMatter() {
            List<StatsWindow> shuffled = new ArrayList<>(windows(evenBottom(90), evenTop(), evenNeutral(),
                    evenNeutral().withCards(1, 0), evenBottom(30)));
            List<StatsWindow> ordered = List.copyOf(shuffled);
            Collections.shuffle(shuffled, new Random(42));

            assertThat(calculate(Position.MIDFIELDER, evenSeason(), List.of(), shuffled))
                    .isEqualTo(calculate(Position.MIDFIELDER, evenSeason(), List.of(), ordered));
        }

        @Test
        void contractExample59WithoutWindows() {
            assertThat(calculate(Position.MIDFIELDER, midfielderSeason(), List.of(), List.of()).status())
                    .isEqualTo(QuoteStatus.INSUFFICIENT_HISTORY);
        }

        @Test
        void isPureAndNeedsNoCollaborators() {
            MomentumFormAlgorithm standalone = new MomentumFormAlgorithm();

            assertThat(standalone.calculate(input(Position.FORWARD, forwardSeason(), List.of(),
                    repeated(5, forwardTop())), MOMENTUM).value()).isEqualByComparingTo("1.1500");
        }
    }
}
