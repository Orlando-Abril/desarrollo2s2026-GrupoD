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
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

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
        assertThat(body.get("skippedStrategies")).asInstanceOf(LIST)
                .singleElement().satisfies(skipped -> assertThat(skipped).asInstanceOf(MAP)
                        .containsEntry("code", "MOMENTUM"));

        List<Map<String, Object>> quotes = jdbcTemplate.queryForList(
                "select player_id, strategy_code, quote_value, score, status, calculated_at, correlation_id "
                        + "from player_quotes where run_id = ?", runId);
        assertThat(quotes).hasSize(5).allSatisfy(quote -> {
            assertThat(quote.get("strategy_code")).isEqualTo("OFFICIAL");
            assertThat(quote.get("correlation_id")).isEqualTo(CORRELATION_ID);
        });
        assertThat(quotes).extracting(quote -> ((Timestamp) quote.get("calculated_at")).toInstant()).containsOnly(
                ((Timestamp) quotes.get(0).get("calculated_at")).toInstant());

        for (Player player : withStats) {
            assertThat(quoteOf(quotes, player).get("status")).isEqualTo("CALCULATED");
        }
        assertThat(quoteOf(quotes, withPreviousQuote))
                .containsEntry("status", "NO_STATS")
                .containsEntry("score", null);
        assertThat((BigDecimal) quoteOf(quotes, withPreviousQuote).get("quote_value")).isEqualByComparingTo("1.0450");
        assertThat(quoteOf(quotes, withoutAnything)).containsEntry("status", "NO_STATS");
        assertThat((BigDecimal) quoteOf(quotes, withoutAnything).get("quote_value")).isEqualByComparingTo("1.0000");

        for (Map<String, Object> quote : quotes) {
            BigDecimal marketValue = playerRepository.findById(((Number) quote.get("player_id")).longValue())
                    .orElseThrow().getMarketValue();
            assertThat(marketValue).isEqualByComparingTo((BigDecimal) quote.get("quote_value"));
            assertThat(marketValue.scale()).isEqualTo(4);
        }

        Integer snapshots = jdbcTemplate.queryForObject(
                "select count(*) from player_stats_snapshots where run_id = ?", Integer.class, runId);
        assertThat(snapshots).isEqualTo(withStats.size());
    }

    private static Map<String, Object> quoteOf(List<Map<String, Object>> quotes, Player player) {
        return quotes.stream()
                .filter(quote -> ((Number) quote.get("player_id")).longValue() == player.getId())
                .findFirst()
                .orElseThrow();
    }
}
