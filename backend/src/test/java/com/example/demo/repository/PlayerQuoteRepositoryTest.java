package com.example.demo.repository;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.PlayerQuote;
import com.example.demo.model.Position;
import com.example.demo.model.QuoteStatus;
import com.example.demo.support.RankingFixtures;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@UnitTestProfile
class PlayerQuoteRepositoryTest {

    private static final UUID RUN_ID = UUID.fromString("7c1d2a8e-0b7f-4f0e-9a51-2f3c4d5e6f70");

    @Autowired
    private PlayerQuoteRepository quoteRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Player saka;

    @BeforeEach
    void setUp() {
        saka = playerRepository.save(Player.builder().externalId("3180").fullName("Bukayo Saka")
                .team("Arsenal FC").league(League.PREMIER_LEAGUE).marketValue(new BigDecimal("1.00")).build());
    }

    @Test
    void storesAndReadsBackEveryField() {
        PlayerQuote saved = quoteRepository.save(validQuote().build());
        entityManager.flush();
        entityManager.clear();

        PlayerQuote read = quoteRepository.findById(saved.getId()).orElseThrow();

        assertThat(read.getPlayer().getId()).isEqualTo(saka.getId());
        assertThat(read.getStrategyCode()).isEqualTo("OFFICIAL");
        assertThat(read.getStrategyVersion()).isEqualTo(1);
        assertThat(read.getValue()).isEqualByComparingTo("1.2345");
        assertThat(read.getValue().scale()).isEqualTo(4);
        assertThat(read.getValue()).isEqualTo(new BigDecimal("1.2345"));
        assertThat(read.getScore()).isEqualByComparingTo("0.4321");
        assertThat(read.getStatus()).isEqualTo(QuoteStatus.CALCULATED);
        assertThat(read.getCalculatedAt()).isEqualTo(Instant.parse("2026-10-05T12:00:00Z"));
        assertThat(read.getRunId()).isEqualTo(RUN_ID);
        assertThat(read.getCorrelationId()).isEqualTo("corr-1");
    }

    @Test
    void storesNoStatsQuoteWithoutScore() {
        PlayerQuote saved = quoteRepository.save(validQuote().status(QuoteStatus.NO_STATS).score(null).build());
        entityManager.flush();
        entityManager.clear();

        PlayerQuote read = quoteRepository.findById(saved.getId()).orElseThrow();

        assertThat(read.getStatus()).isEqualTo(QuoteStatus.NO_STATS);
        assertThat(read.getScore()).isNull();
    }

    @ParameterizedTest
    @MethodSource("invalidQuotes")
    void rejectsQuoteWithMissingOrInvalidData(UnaryOperator<PlayerQuote.PlayerQuoteBuilder> breakIt) {
        PlayerQuote quote = breakIt.apply(validQuote()).build();

        assertThatThrownBy(() -> {
            quoteRepository.save(quote);
            entityManager.flush();
        }).isInstanceOfAny(jakarta.validation.ConstraintViolationException.class,
                org.springframework.dao.DataIntegrityViolationException.class);
    }

    static Stream<Named<UnaryOperator<PlayerQuote.PlayerQuoteBuilder>>> invalidQuotes() {
        return Stream.of(
                Named.of("sin jugador", b -> b.player(null)),
                Named.of("sin código de estrategia", b -> b.strategyCode(null)),
                Named.of("sin versión de estrategia", b -> b.strategyVersion(null)),
                Named.of("sin valor", b -> b.value(null)),
                Named.of("sin estado", b -> b.status(null)),
                Named.of("sin fecha de cálculo", b -> b.calculatedAt(null)),
                Named.of("sin id de corrida", b -> b.runId(null)),
                Named.of("sin correlation id", b -> b.correlationId(null)),
                Named.of("valor negativo", b -> b.value(new BigDecimal("-0.01"))),
                Named.of("versión 0", b -> b.strategyVersion(0)));
    }

    @Test
    void changesToALoadedQuoteAreNeverWritten() {
        PlayerQuote saved = quoteRepository.save(validQuote().build());
        entityManager.flush();
        entityManager.clear();

        PlayerQuote loaded = quoteRepository.findById(saved.getId()).orElseThrow();
        ReflectionTestUtils.setField(loaded, "value", new BigDecimal("9.9999"));
        entityManager.flush();
        entityManager.clear();

        assertThat(quoteRepository.findById(saved.getId()).orElseThrow().getValue()).isEqualByComparingTo("1.2345");
    }

    @Test
    void repositoryOffersNoDeleteOperation() {
        assertThat(Arrays.stream(PlayerQuoteRepository.class.getMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("delete"));
        assertThat(CrudRepository.class.isAssignableFrom(PlayerQuoteRepository.class)).isFalse();
        assertThat(ListCrudRepository.class.isAssignableFrom(PlayerQuoteRepository.class)).isFalse();
        assertThat(JpaRepository.class.isAssignableFrom(PlayerQuoteRepository.class)).isFalse();
    }

    @Nested
    class Queries {

        private Player bruno;
        private Player rice;
        private Player odegaard;

        @BeforeEach
        void setUpHistory() {
            quote(saka, "OFFICIAL", 1, "2026-09-01T12:00:00Z", "1.0000");
            quote(saka, "OFFICIAL", 1, "2026-09-08T12:00:00Z", "1.1000");
            quote(saka, "OFFICIAL", 1, "2026-09-15T12:00:00Z", "1.2000");
            quote(saka, "MOMENTUM", 1, "2026-09-16T12:00:00Z", "1.3000");
            bruno = player("123761", "Bruno Fernandes", "Manchester United FC");
            rice = player("368046", "Declan Rice", "Arsenal FC");
            odegaard = player("343227", "Martin Ødegaard", "Arsenal FC");
        }

        @Test
        void latestQuoteOfAPlayerForAStrategy() {
            assertThat(quoteRepository.findFirstByPlayerIdAndStrategyCodeOrderByCalculatedAtDescIdDesc(
                    saka.getId(), "OFFICIAL"))
                    .get().extracting(PlayerQuote::getCalculatedAt)
                    .isEqualTo(Instant.parse("2026-09-15T12:00:00Z"));
        }

        @Test
        void historyIsInclusiveAscendingAndFilteredByStrategy() {
            assertThat(history(saka, "OFFICIAL", "2026-09-01T12:00:00Z", "2026-09-08T12:00:00Z"))
                    .extracting(PlayerQuote::getCalculatedAt)
                    .containsExactly(Instant.parse("2026-09-01T12:00:00Z"), Instant.parse("2026-09-08T12:00:00Z"));
        }

        @Test
        void historyWithInvertedRangeIsEmpty() {
            assertThat(history(saka, "OFFICIAL", "2026-09-15T12:00:00Z", "2026-09-01T12:00:00Z")).isEmpty();
        }

        @Test
        void effectiveQuoteOnADateIsTheLastOneUpToThatDay() {
            assertThat(quoteRepository.findEffectiveOn(saka.getId(), "OFFICIAL", LocalDate.of(2026, 9, 10)))
                    .get().extracting(PlayerQuote::getCalculatedAt)
                    .isEqualTo(Instant.parse("2026-09-08T12:00:00Z"));
        }

        @Test
        void effectiveQuoteBeforeTheFirstOneIsEmpty() {
            assertThat(quoteRepository.findEffectiveOn(saka.getId(), "OFFICIAL", LocalDate.of(2026, 8, 31))).isEmpty();
        }

        @Test
        void effectiveQuoteAtTheExactInstantOfAQuoteReturnsIt() {
            assertThat(quoteRepository.findEffectiveAt(saka.getId(), "OFFICIAL", Instant.parse("2026-09-08T12:00:00Z")))
                    .get().extracting(PlayerQuote::getCalculatedAt)
                    .isEqualTo(Instant.parse("2026-09-08T12:00:00Z"));
        }

        @Test
        void effectiveQuoteOnADateExcludesTheNextDayMidnight() {
            quote(saka, "OFFICIAL", 1, "2026-09-10T23:59:59.999999Z", "1.4000");
            quote(saka, "OFFICIAL", 1, "2026-09-11T00:00:00Z", "1.5000");

            assertThat(quoteRepository.findEffectiveOn(saka.getId(), "OFFICIAL", LocalDate.of(2026, 9, 10)))
                    .get().extracting(PlayerQuote::getCalculatedAt)
                    .isEqualTo(Instant.parse("2026-09-10T23:59:59.999999Z"));
        }

        @Test
        void latestQuoteOfEveryPlayerForAStrategy() {
            quote(bruno, "OFFICIAL", 1, "2026-09-01T12:00:00Z", "1.0000");
            quote(bruno, "OFFICIAL", 1, "2026-09-20T12:00:00Z", "0.9000");
            quote(rice, "OFFICIAL", 1, "2026-09-02T12:00:00Z", "1.0500");
            quote(rice, "MOMENTUM", 1, "2026-09-30T12:00:00Z", "1.0700");

            assertThat(quoteRepository.findLatestForAllPlayers("OFFICIAL"))
                    .extracting(q -> q.getPlayer().getId(), PlayerQuote::getCalculatedAt)
                    .containsExactlyInAnyOrder(
                            tuple(saka.getId(), Instant.parse("2026-09-15T12:00:00Z")),
                            tuple(bruno.getId(), Instant.parse("2026-09-20T12:00:00Z")),
                            tuple(rice.getId(), Instant.parse("2026-09-02T12:00:00Z")));
        }

        @Test
        void playerWithoutQuotesHasNoLatestAndNoHistory() {
            assertThat(quoteRepository.findFirstByPlayerIdAndStrategyCodeOrderByCalculatedAtDescIdDesc(
                    odegaard.getId(), "OFFICIAL")).isEmpty();
            assertThat(history(odegaard, "OFFICIAL", "2026-01-01T00:00:00Z", "2026-12-31T23:59:59Z")).isEmpty();
        }

        @Test
        void tieOnCalculatedAtIsResolvedByTheLastRegistered() {
            PlayerQuote first = quote(saka, "OFFICIAL", 1, "2026-09-22T12:00:00Z", "2.0000");
            PlayerQuote second = quote(saka, "OFFICIAL", 1, "2026-09-22T12:00:00Z", "2.1000");
            assertThat(second.getId()).isGreaterThan(first.getId());

            assertThat(quoteRepository.findFirstByPlayerIdAndStrategyCodeOrderByCalculatedAtDescIdDesc(
                    saka.getId(), "OFFICIAL")).get().extracting(PlayerQuote::getId).isEqualTo(second.getId());
            assertThat(quoteRepository.findEffectiveOn(saka.getId(), "OFFICIAL", LocalDate.of(2026, 9, 22)))
                    .get().extracting(PlayerQuote::getId).isEqualTo(second.getId());
            assertThat(quoteRepository.findLatestForAllPlayers("OFFICIAL"))
                    .filteredOn(q -> q.getPlayer().getId().equals(saka.getId()))
                    .singleElement().extracting(PlayerQuote::getId).isEqualTo(second.getId());
        }

        @Test
        void versionsOfTheSameStrategyShareOneHistory() {
            quote(saka, "OFFICIAL", 2, "2026-09-29T12:00:00Z", "1.2500");

            assertThat(history(saka, "OFFICIAL", "2026-09-01T00:00:00Z", "2026-09-30T00:00:00Z"))
                    .extracting(PlayerQuote::getStrategyVersion)
                    .containsExactly(1, 1, 1, 2);
            assertThat(quoteRepository.findFirstByPlayerIdAndStrategyCodeOrderByCalculatedAtDescIdDesc(
                    saka.getId(), "OFFICIAL")).get().extracting(PlayerQuote::getStrategyVersion).isEqualTo(2);
        }

        @Test
        void rankingUsesLatestAndImmediatelyPreviousQuoteWithStableGlobalOrder() {
            Player first = RankingFixtures.player(playerRepository, "First", "A", League.PREMIER_LEAGUE,
                    Position.FORWARD);
            Player second = RankingFixtures.player(playerRepository, "Second", "B", League.PREMIER_LEAGUE,
                    Position.MIDFIELDER);
            Player withoutQuote = RankingFixtures.player(playerRepository, "No quote", "C", League.PREMIER_LEAGUE,
                    Position.DEFENDER);
            RankingFixtures.quote(quoteRepository, first, "RANK", 1, "8.0000", "2026-10-01T00:00:00Z");
            RankingFixtures.quote(quoteRepository, first, "RANK", 2, "15.0000", "2026-10-02T00:00:00Z");
            RankingFixtures.quote(quoteRepository, second, "RANK", 1, "15.0000", "2026-10-02T00:00:00Z");
            entityManager.flush();
            entityManager.clear();

            Page<RankingRowProjection> ranking = quoteRepository.findRanking("RANK", null, null,
                    PageRequest.of(0, 20));

            assertThat(ranking.getContent()).extracting(RankingRowProjection::getRank,
                            RankingRowProjection::getPlayerId, RankingRowProjection::getCurrentValue,
                            RankingRowProjection::getPreviousValue)
                    .containsExactly(
                            tuple(1L, first.getId(), new BigDecimal("15.0000"), new BigDecimal("8.0000")),
                            tuple(2L, second.getId(), new BigDecimal("15.0000"), null));
            assertThat(ranking.getTotalElements()).isEqualTo(2);
            assertThat(withoutQuote.getId()).isNotNull();
        }

        @Test
        void rankingFiltersWithoutDuplicatingMultiPositionPlayersAndPaginatesAfterRanking() {
            for (int i = 0; i < 22; i++) {
                Player player = RankingFixtures.player(playerRepository, "Rank " + i, "A",
                        League.LA_LIGA, Position.FORWARD, Position.MIDFIELDER);
                RankingFixtures.quote(quoteRepository, player, "FILTER", 1,
                        String.format("%d.0000", 100 - i), "2026-10-02T00:00:00Z");
            }
            Player otherLeague = RankingFixtures.player(playerRepository, "Other", "B", League.SERIE_A,
                    Position.FORWARD);
            RankingFixtures.quote(quoteRepository, otherLeague, "FILTER", 1, "999.0000",
                    "2026-10-02T00:00:00Z");
            entityManager.flush();
            entityManager.clear();

            Page<RankingRowProjection> page = quoteRepository.findRanking("FILTER", "LA_LIGA", "FORWARD",
                    PageRequest.of(1, 20));

            assertThat(page.getTotalElements()).isEqualTo(22);
            assertThat(page.getTotalPages()).isEqualTo(2);
            assertThat(page.getContent()).extracting(RankingRowProjection::getRank).containsExactly(21L, 22L);
        }

        private List<PlayerQuote> history(Player player, String code, String from, String to) {
            return quoteRepository.findByPlayerIdAndStrategyCodeAndCalculatedAtBetweenOrderByCalculatedAtAscIdAsc(
                    player.getId(), code, Instant.parse(from), Instant.parse(to));
        }

        private Player player(String externalId, String fullName, String team) {
            return playerRepository.save(Player.builder().externalId(externalId).fullName(fullName)
                    .team(team).league(League.PREMIER_LEAGUE).marketValue(new BigDecimal("1.00")).build());
        }

        private PlayerQuote quote(Player player, String code, int version, String calculatedAt, String value) {
            PlayerQuote saved = quoteRepository.save(validQuote().player(player).strategyCode(code)
                    .strategyVersion(version).calculatedAt(Instant.parse(calculatedAt))
                    .value(new BigDecimal(value)).build());
            entityManager.flush();
            return saved;
        }
    }

    private PlayerQuote.PlayerQuoteBuilder validQuote() {
        return PlayerQuote.builder()
                .player(saka)
                .strategyCode("OFFICIAL")
                .strategyVersion(1)
                .value(new BigDecimal("1.2345"))
                .score(new BigDecimal("0.4321"))
                .status(QuoteStatus.CALCULATED)
                .calculatedAt(Instant.parse("2026-10-05T12:00:00Z"))
                .runId(RUN_ID)
                .correlationId("corr-1");
    }
}
