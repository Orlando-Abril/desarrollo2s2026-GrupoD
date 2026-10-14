package com.example.demo.e2e;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.PlayerQuote;
import com.example.demo.model.QuoteStatus;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.service.valuation.strategy.ValuationStrategyCatalog;
import com.example.demo.support.E2EFixtures;
import com.example.demo.support.E2ETest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Verifica las 4 consultas del historial de cotizaciones contra PostgreSQL real: el manejo de fechas y la
 * precisión de {@code timestamp} pueden diferir de H2 (spec 013, FR-030).
 */
@E2ETest
class PlayerQuoteQueriesE2ETest {

    @Autowired
    private E2EFixtures fixtures;

    @Autowired
    private PlayerQuoteRepository quoteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** El catálogo vive en código: la limpieza de {@code E2EDatabaseCleaner} no lo afecta (spec 013, FR-017). */
    @Autowired
    private ValuationStrategyCatalog catalog;

    private Player premier;
    private Player liga;
    private Player serie;
    private Player bundesliga;

    @BeforeEach
    void setUp() {
        E2EFixtures.FixtureSet players = fixtures.createPlayersFromFiveLeagues();
        premier = players.forLeague(League.PREMIER_LEAGUE);
        liga = players.forLeague(League.LA_LIGA);
        serie = players.forLeague(League.SERIE_A);
        bundesliga = players.forLeague(League.BUNDESLIGA);

        quote(premier, "OFFICIAL", 1, "2026-09-01T12:00:00Z");
        quote(premier, "OFFICIAL", 1, "2026-09-08T12:00:00Z");
        quote(premier, "OFFICIAL", 1, "2026-09-15T12:00:00Z");
        quote(premier, "MOMENTUM", 1, "2026-09-16T12:00:00Z");
    }

    @Test
    void latestQuoteOfAPlayerForAStrategy() {
        assertThat(quoteRepository.findFirstByPlayerIdAndStrategyCodeOrderByCalculatedAtDescIdDesc(
                premier.getId(), "OFFICIAL"))
                .get().extracting(PlayerQuote::getCalculatedAt)
                .isEqualTo(Instant.parse("2026-09-15T12:00:00Z"));
    }

    @Test
    void historyIsInclusiveAscendingAndFilteredByStrategy() {
        assertThat(history(premier, "2026-09-01T12:00:00Z", "2026-09-08T12:00:00Z"))
                .extracting(PlayerQuote::getCalculatedAt)
                .containsExactly(Instant.parse("2026-09-01T12:00:00Z"), Instant.parse("2026-09-08T12:00:00Z"));
    }

    @Test
    void effectiveQuoteOnADateAndBeforeTheFirstOne() {
        assertThat(quoteRepository.findEffectiveOn(premier.getId(), "OFFICIAL", LocalDate.of(2026, 9, 10)))
                .get().extracting(PlayerQuote::getCalculatedAt)
                .isEqualTo(Instant.parse("2026-09-08T12:00:00Z"));
        assertThat(quoteRepository.findEffectiveOn(premier.getId(), "OFFICIAL", LocalDate.of(2026, 8, 31)))
                .isEmpty();
    }

    @Test
    void effectiveQuoteAtTheExactInstantOfAQuoteReturnsIt() {
        assertThat(quoteRepository.findEffectiveAt(premier.getId(), "OFFICIAL", Instant.parse("2026-09-08T12:00:00Z")))
                .get().extracting(PlayerQuote::getCalculatedAt)
                .isEqualTo(Instant.parse("2026-09-08T12:00:00Z"));
    }

    @Test
    void effectiveQuoteOnADateExcludesTheNextDayMidnight() {
        quote(premier, "OFFICIAL", 1, "2026-09-10T23:59:59.999999Z");
        quote(premier, "OFFICIAL", 1, "2026-09-11T00:00:00Z");

        assertThat(quoteRepository.findEffectiveOn(premier.getId(), "OFFICIAL", LocalDate.of(2026, 9, 10)))
                .get().extracting(PlayerQuote::getCalculatedAt)
                .isEqualTo(Instant.parse("2026-09-10T23:59:59.999999Z"));
    }

    @Test
    void latestQuoteOfEveryPlayerForAStrategy() {
        quote(liga, "OFFICIAL", 1, "2026-09-01T12:00:00Z");
        quote(liga, "OFFICIAL", 1, "2026-09-20T12:00:00Z");
        quote(serie, "OFFICIAL", 1, "2026-09-02T12:00:00Z");
        quote(serie, "MOMENTUM", 1, "2026-09-30T12:00:00Z");

        assertThat(quoteRepository.findLatestForAllPlayers("OFFICIAL"))
                .extracting(q -> q.getPlayer().getId(), PlayerQuote::getCalculatedAt)
                .containsExactlyInAnyOrder(
                        tuple(premier.getId(), Instant.parse("2026-09-15T12:00:00Z")),
                        tuple(liga.getId(), Instant.parse("2026-09-20T12:00:00Z")),
                        tuple(serie.getId(), Instant.parse("2026-09-02T12:00:00Z")));
    }

    @Test
    void playerWithoutQuotesHasNoLatestAndNoHistory() {
        assertThat(quoteRepository.findFirstByPlayerIdAndStrategyCodeOrderByCalculatedAtDescIdDesc(
                bundesliga.getId(), "OFFICIAL")).isEmpty();
        assertThat(history(bundesliga, "2026-01-01T00:00:00Z", "2026-12-31T23:59:59Z")).isEmpty();
    }

    @Test
    void tieOnCalculatedAtIsResolvedByTheLastRegistered() {
        quote(premier, "OFFICIAL", 1, "2026-09-22T12:00:00Z");
        PlayerQuote second = quote(premier, "OFFICIAL", 1, "2026-09-22T12:00:00Z");

        assertThat(quoteRepository.findFirstByPlayerIdAndStrategyCodeOrderByCalculatedAtDescIdDesc(
                premier.getId(), "OFFICIAL")).get().extracting(PlayerQuote::getId).isEqualTo(second.getId());
        assertThat(quoteRepository.findEffectiveOn(premier.getId(), "OFFICIAL", LocalDate.of(2026, 9, 22)))
                .get().extracting(PlayerQuote::getId).isEqualTo(second.getId());
        assertThat(quoteRepository.findLatestForAllPlayers("OFFICIAL"))
                .filteredOn(q -> q.getPlayer().getId().equals(premier.getId()))
                .singleElement().extracting(PlayerQuote::getId).isEqualTo(second.getId());
    }

    @Test
    void strategyCatalogDoesNotDependOnSeededData() {
        assertThat(catalog.all()).hasSize(2);
        assertThat(catalog.active().code()).isEqualTo("OFFICIAL");
    }

    @Test
    void historyIndexesExist() {
        List<String> indexes = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where tablename = 'player_quotes'", String.class);

        assertThat(indexes).contains("idx_player_quotes_player_strategy_time", "idx_player_quotes_strategy_time");
    }

    private List<PlayerQuote> history(Player player, String from, String to) {
        return quoteRepository.findByPlayerIdAndStrategyCodeAndCalculatedAtBetweenOrderByCalculatedAtAscIdAsc(
                player.getId(), "OFFICIAL", Instant.parse(from), Instant.parse(to));
    }

    private PlayerQuote quote(Player player, String code, int version, String calculatedAt) {
        return quoteRepository.save(PlayerQuote.builder()
                .player(player)
                .strategyCode(code)
                .strategyVersion(version)
                .value(new BigDecimal("1.0000"))
                .score(new BigDecimal("0.5000"))
                .status(QuoteStatus.CALCULATED)
                .calculatedAt(Instant.parse(calculatedAt))
                .runId(UUID.randomUUID())
                .correlationId("e2e-quotes")
                .build());
    }
}
