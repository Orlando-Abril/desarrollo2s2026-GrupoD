package com.example.demo.e2e;

import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.PlayerQuote;
import com.example.demo.model.PlayerStats;
import com.example.demo.model.QuoteStatus;
import com.example.demo.model.Role;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.PlayerStatsRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.E2EFixtures;
import com.example.demo.support.E2ETest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

/** Corrida completa contra PostgreSQL real con las fuentes externas apuntando a un puerto cerrado. */
@E2ETest
class QuoteRecalculationE2ETest {

    private static final String CORRELATION_ID = "0b6f4a3c-6a52-4d8e-9f0e-3c7d2b1a9e44";

    @LocalServerPort
    private int port;

    @Autowired
    private E2EFixtures fixtures;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerStatsRepository statsRepository;

    @Autowired
    private PlayerQuoteRepository quoteRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<Player> withStats;
    private Player withPreviousQuote;
    private Player withoutAnything;
    private String adminKey;

    @BeforeEach
    void setUp() {
        E2EFixtures.FixtureSet players = fixtures.createPlayersFromFiveLeagues();
        withStats = List.of(players.forLeague(League.LA_LIGA), players.forLeague(League.BUNDESLIGA),
                players.forLeague(League.LIGUE_1));
        withPreviousQuote = players.forLeague(League.SERIE_A);
        withoutAnything = players.forLeague(League.PREMIER_LEAGUE);

        int goals = 1;
        for (Player player : withStats) {
            PlayerStats stats = new PlayerStats(player);
            stats.setMinutesPlayed(900);
            stats.setGoals(goals++);
            stats.setTackles(10);
            stats.setRating(new BigDecimal("7.10"));
            stats.setFetchedAt(Instant.parse("2026-10-20T10:00:00Z"));
            statsRepository.saveAndFlush(stats);
        }
        quoteRepository.save(PlayerQuote.builder()
                .player(withPreviousQuote)
                .strategyCode("OFFICIAL")
                .strategyVersion(1)
                .value(new BigDecimal("1.0450"))
                .score(new BigDecimal("0.4500"))
                .status(QuoteStatus.CALCULATED)
                .calculatedAt(Instant.parse("2026-10-16T18:00:00Z"))
                .runId(UUID.randomUUID())
                .correlationId("previous")
                .build());
        adminKey = AuthFixtures.apiKey(apiKeyRepository, AuthFixtures.user(userRepository, "admin", Role.ADMIN));
    }

    @Test
    void manualRunStoresQuotesMarketValueAndSnapshots() {
        ResponseEntity<Map<String, Object>> response = RestClient.create("http://localhost:" + port)
                .post().uri("/quotes/recalculate")
                .header("X-API-KEY", adminKey)
                .header(CorrelationIdFilter.HEADER, CORRELATION_ID)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        UUID runId = UUID.fromString((String) body.get("runId"));
        assertThat(body.get("marketValueUpdated")).isEqualTo(true);
        assertThat(body.get("playersProcessed")).isEqualTo(5);
        assertThat(body.get("playersFailed")).isEqualTo(0);
        assertThat(body.get("skippedStrategies")).asInstanceOf(LIST).isEmpty();

        List<Map<String, Object>> quotes = jdbcTemplate.queryForList(
                "select player_id, strategy_code, quote_value, score, status, calculated_at, correlation_id "
                        + "from player_quotes where run_id = ?", runId);
        assertThat(quotes).hasSize(10)
                .allSatisfy(quote -> assertThat(quote.get("correlation_id")).isEqualTo(CORRELATION_ID));
        List<Map<String, Object>> official = ofStrategy(quotes, "OFFICIAL");
        List<Map<String, Object>> momentum = ofStrategy(quotes, "MOMENTUM");
        assertThat(official).hasSize(5);
        // Primera corrida: cada jugador tiene a lo sumo un snapshot, así que MOMENTUM no tiene ventanas.
        assertThat(momentum).hasSize(5).allSatisfy(quote -> {
            assertThat(quote.get("status")).isEqualTo("INSUFFICIENT_HISTORY");
            assertThat((BigDecimal) quote.get("quote_value")).isEqualByComparingTo("1.0000");
        });
        assertThat(quotes).extracting(quote -> ((Timestamp) quote.get("calculated_at")).toInstant()).containsOnly(
                ((Timestamp) quotes.get(0).get("calculated_at")).toInstant());

        for (Player player : withStats) {
            assertThat(quoteOf(official, player).get("status")).isEqualTo("CALCULATED");
        }
        assertThat(quoteOf(official, withPreviousQuote))
                .containsEntry("status", "NO_STATS")
                .containsEntry("score", null);
        assertThat((BigDecimal) quoteOf(official, withPreviousQuote).get("quote_value")).isEqualByComparingTo("1.0450");
        assertThat(quoteOf(official, withoutAnything)).containsEntry("status", "NO_STATS");
        assertThat((BigDecimal) quoteOf(official, withoutAnything).get("quote_value")).isEqualByComparingTo("1.0000");

        // El valor de mercado sigue a la estrategia activa (OFFICIAL), no a MOMENTUM.
        for (Map<String, Object> quote : official) {
            BigDecimal marketValue = playerRepository.findById(((Number) quote.get("player_id")).longValue())
                    .orElseThrow().getMarketValue();
            assertThat(marketValue).isEqualByComparingTo((BigDecimal) quote.get("quote_value"));
            assertThat(marketValue.scale()).isEqualTo(4);
        }

        Integer snapshots = jdbcTemplate.queryForObject(
                "select count(*) from player_stats_snapshots where run_id = ?", Integer.class, runId);
        assertThat(snapshots).isEqualTo(withStats.size());
    }

    @Test
    void momentumIsCalculatedOnceThePlayerStatsChange() {
        Player tracked = withStats.get(0);
        recalculate();
        updateStats(tracked);

        Map<String, Object> second = momentumQuote(recalculate(), tracked);

        assertThat(second).containsEntry("status", "CALCULATED");
        BigDecimal value = (BigDecimal) second.get("quote_value");
        assertThat(value).isGreaterThan(BigDecimal.ONE);
        assertThat(value.subtract(BigDecimal.ONE).abs()).isLessThanOrEqualTo(new BigDecimal("0.15"));
    }

    @Test
    void runsWithoutNewStatsKeepTheMomentumValue() {
        Player tracked = withStats.get(0);
        recalculate();
        updateStats(tracked);
        BigDecimal calculated = (BigDecimal) momentumQuote(recalculate(), tracked).get("quote_value");

        for (int run = 0; run < 3; run++) {
            UUID runId = recalculate();

            Integer snapshots = jdbcTemplate.queryForObject(
                    "select count(*) from player_stats_snapshots where run_id = ? and player_id = ?", Integer.class,
                    runId, tracked.getId());
            assertThat(snapshots).isZero();
            Map<String, Object> repeated = momentumQuote(runId, tracked);
            assertThat(repeated).containsEntry("status", "CALCULATED");
            assertThat((BigDecimal) repeated.get("quote_value")).isEqualByComparingTo(calculated);
        }
    }

    /** Simula una sincronización: un partido más con 2 goles y otro {@code fetchedAt}, así hay snapshot nuevo. */
    private void updateStats(Player player) {
        PlayerStats stats = statsRepository.findById(player.getId()).orElseThrow();
        stats.setMinutesPlayed(990);
        stats.setGoals(stats.getGoals() + 2);
        stats.setTackles(12);
        stats.setRating(new BigDecimal("7.40"));
        stats.setFetchedAt(Instant.parse("2026-10-27T10:00:00Z"));
        statsRepository.saveAndFlush(stats);
    }

    private UUID recalculate() {
        Map<String, Object> body = RestClient.create("http://localhost:" + port)
                .post().uri("/quotes/recalculate")
                .header("X-API-KEY", adminKey)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        assertThat(body).isNotNull();
        return UUID.fromString((String) body.get("runId"));
    }

    private Map<String, Object> momentumQuote(UUID runId, Player player) {
        return jdbcTemplate.queryForMap("select quote_value, status from player_quotes "
                + "where run_id = ? and player_id = ? and strategy_code = 'MOMENTUM'", runId, player.getId());
    }

    private static List<Map<String, Object>> ofStrategy(List<Map<String, Object>> quotes, String strategy) {
        return quotes.stream().filter(quote -> strategy.equals(quote.get("strategy_code"))).toList();
    }

    private static Map<String, Object> quoteOf(List<Map<String, Object>> quotes, Player player) {
        return quotes.stream()
                .filter(quote -> ((Number) quote.get("player_id")).longValue() == player.getId())
                .findFirst()
                .orElseThrow();
    }
}
