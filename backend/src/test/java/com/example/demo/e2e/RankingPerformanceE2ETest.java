package com.example.demo.e2e;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.PlayerQuote;
import com.example.demo.model.Position;
import com.example.demo.model.QuoteStatus;
import com.example.demo.model.Role;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.E2ETest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@E2ETest
class RankingPerformanceE2ETest {
    private static final Logger log = LoggerFactory.getLogger(RankingPerformanceE2ETest.class);
    private static final int PLAYER_COUNT = 2_500;
    private static final int REQUEST_COUNT = 20;

    @LocalServerPort int port;
    @Autowired PlayerRepository players;
    @Autowired PlayerQuoteRepository quotes;
    @Autowired UserRepository users;
    @Autowired ApiKeyRepository apiKeys;
    @Autowired JdbcTemplate jdbc;

    @Test
    void twentyCompleteHttpRequestsMeetTheTargetAndExplainTheUncachedQuery() {
        List<Player> population = new ArrayList<>(PLAYER_COUNT);
        for (int i = 0; i < PLAYER_COUNT; i++) {
            population.add(Player.builder().externalId("ranking-benchmark-" + i).fullName("Player " + i)
                    .team("Club " + (i % 40)).league(League.PREMIER_LEAGUE)
                    .positions(java.util.Set.of(Position.MIDFIELDER)).marketValue(BigDecimal.ONE).build());
        }
        population = players.saveAllAndFlush(population);
        List<PlayerQuote> current = new ArrayList<>(PLAYER_COUNT);
        for (int i = 0; i < population.size(); i++) {
            current.add(PlayerQuote.builder().player(population.get(i)).strategyCode("OFFICIAL").strategyVersion(1)
                    .value(BigDecimal.valueOf(PLAYER_COUNT - i).setScale(4)).score(BigDecimal.ONE)
                    .status(QuoteStatus.CALCULATED).calculatedAt(Instant.parse("2026-10-01T00:00:00Z"))
                    .runId(UUID.randomUUID()).correlationId("ranking-benchmark").build());
        }
        quotes.saveAll(current);
        String key = AuthFixtures.apiKey(apiKeys, AuthFixtures.user(users, "ranking-benchmark", Role.USER));
        RestClient client = RestClient.create("http://localhost:" + port);

        List<Long> millis = new ArrayList<>(REQUEST_COUNT);
        for (int i = 0; i < REQUEST_COUNT; i++) {
            long started = System.nanoTime();
            ResponseEntity<String> response = client.get().uri("/players/ranking?page=0&size=20")
                    .header("X-API-KEY", key).retrieve().toEntity(String.class);
            millis.add((System.nanoTime() - started) / 1_000_000);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("\"totalElements\":2500");
        }
        log.info("ranking_benchmark players={} requestTimesMs={}", PLAYER_COUNT, millis);
        assertThat(millis.stream().filter(value -> value < 1_000).count()).isGreaterThanOrEqualTo(19);

        List<String> plan = jdbc.queryForList("""
                EXPLAIN (ANALYZE, BUFFERS)
                WITH quote_history AS (
                  SELECT q.player_id, q.strategy_code, q.strategy_version, q.quote_value,
                         row_number() OVER (PARTITION BY q.player_id, q.strategy_code
                           ORDER BY q.calculated_at DESC, q.id DESC) AS quote_order,
                         lead(q.quote_value) OVER (PARTITION BY q.player_id, q.strategy_code
                           ORDER BY q.calculated_at DESC, q.id DESC) AS previous_value
                  FROM player_quotes q WHERE q.strategy_code = 'OFFICIAL'
                ), eligible AS (
                  SELECT p.id AS player_id, q.quote_value AS current_value
                  FROM quote_history q JOIN players p ON p.id = q.player_id WHERE q.quote_order = 1
                ), ranked AS (
                  SELECT row_number() OVER (ORDER BY current_value DESC, player_id ASC), eligible.* FROM eligible
                ) SELECT * FROM ranked ORDER BY current_value DESC, player_id ASC LIMIT 20
                """, String.class);
        log.info("ranking_uncached_explain={}", plan);
        assertThat(plan).anyMatch(line -> line.contains("Execution Time"));
    }
}
