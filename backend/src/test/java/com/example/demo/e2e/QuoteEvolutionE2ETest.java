package com.example.demo.e2e;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.PlayerStats;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.PlayerStatsRepository;
import com.example.demo.support.E2EFixtures;
import com.example.demo.support.E2ETest;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escenario 2 de la consigna (constitución §3.3, spec 021): la cotización de un jugador de cada liga en tres fechas,
 * con OFFICIAL y MOMENTUM, verificada por HTTP como la vería un usuario. Los datos y las direcciones esperadas están
 * en {@code specs/021-e2e-escenario-cotizaciones/data-model.md} y research R4.
 */
@E2ETest
class QuoteEvolutionE2ETest {

    private static final Instant RUN_1 = Instant.parse("2026-10-05T18:00:00Z");
    private static final Instant RUN_2 = Instant.parse("2026-10-12T18:00:00Z");
    private static final Instant RUN_3 = Instant.parse("2026-10-19T18:00:00Z");
    private static final List<Instant> RUNS = List.of(RUN_1, RUN_2, RUN_3);
    private static final List<Instant> FETCHED_AT = List.of(Instant.parse("2026-10-04T10:00:00Z"),
            Instant.parse("2026-10-11T10:00:00Z"), Instant.parse("2026-10-18T10:00:00Z"));
    private static final List<LocalDate> RUN_DATES = List.of(LocalDate.parse("2026-10-05"),
            LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-19"));
    private static final LocalDate BEFORE_FIRST = LocalDate.parse("2026-10-04");
    private static final List<String> STRATEGIES = List.of("OFFICIAL", "MOMENTUM");
    private static final String ADMIN = "e2e-admin";
    private static final BigDecimal MAX_VARIATION = new BigDecimal("0.15");
    private static final BigDecimal ROUNDING = new BigDecimal("0.00005");

    /** Estadísticas acumuladas antes de cada corrida (data-model.md). Bundesliga repite la columna 2 en la 3. */
    private static final Map<League, List<StatsRow>> STATS = Map.of(
            League.PREMIER_LEAGUE, List.of(
                    new StatsRow(900, 4, 2, 20, 10, 10, 5, "7.00"),
                    new StatsRow(990, 5, 2, 22, 11, 11, 5, "7.05"),
                    new StatsRow(1080, 7, 3, 26, 13, 13, 5, "7.40")),
            League.LA_LIGA, List.of(
                    new StatsRow(900, 1, 1, 5, 10, 5, 25, "6.90"),
                    new StatsRow(990, 1, 1, 5, 11, 6, 28, "6.90"),
                    new StatsRow(1080, 1, 1, 6, 12, 6, 31, "6.90")),
            League.SERIE_A, List.of(
                    new StatsRow(900, 0, 0, 0, 5, 0, 5, "6.80"),
                    new StatsRow(990, 0, 0, 0, 6, 0, 6, "6.80"),
                    new StatsRow(1080, 0, 0, 0, 6, 0, 6, "6.80")),
            League.BUNDESLIGA, List.of(
                    new StatsRow(900, 2, 2, 10, 15, 10, 15, "7.00"),
                    new StatsRow(990, 2, 2, 11, 16, 11, 16, "7.00"),
                    new StatsRow(990, 2, 2, 11, 16, 11, 16, "7.00")),
            League.LIGUE_1, List.of(
                    new StatsRow(900, 1, 0, 3, 5, 2, 30, "7.00"),
                    new StatsRow(990, 1, 0, 3, 6, 2, 33, "7.00"),
                    new StatsRow(1080, 1, 1, 4, 6, 3, 36, "7.10")));

    @LocalServerPort
    private int port;

    @Autowired
    private E2EFixtures fixtures;

    @Autowired
    private PlayerStatsRepository statsRepository;

    /** Sólo para {@code marketValue}: el resto se verifica sobre el JSON de las respuestas. */
    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Autowired
    private MutableClock evolutionClock;

    private final Map<League, Player> players = new EnumMap<>(League.class);
    private String apiKey;

    @BeforeEach
    void setUp() {
        E2EFixtures.FixtureSet fixtureSet = fixtures.createPlayersFromFiveLeagues();
        for (League league : League.values()) {
            players.put(league, fixtureSet.forLeague(league));
        }
        evolutionClock.set(RUN_1);
        registerAdmin();
        loadStatsForRun(0);
    }

    @Test
    void evolutionOfOnePlayerPerLeagueAcrossThreeDates() {
        assertThat(clock).as("el reloj de la aplicación es el del test").isSameAs(evolutionClock);

        // Corridas (contracts §2): estadísticas nuevas antes de la segunda y la tercera.
        assertRun(recalculateOn(RUN_1), RUN_1, true);
        assertMarketValueFollowsOfficial();
        loadStatsForRun(1);
        assertRun(recalculateOn(RUN_2), RUN_2, false);
        assertMarketValueFollowsOfficial();
        Map<String, Object> momentumRankingBeforeLastRun = ranking("MOMENTUM").getBody();
        loadStatsForRun(2);
        assertRun(recalculateOn(RUN_3), RUN_3, false);
        assertMarketValueFollowsOfficial();

        Map<League, Map<String, List<Map<String, Object>>>> histories = histories();
        assertHistories(histories);
        assertEffectiveQuotes(histories);
        assertEvolution(histories);
        assertRankings(histories, momentumRankingBeforeLastRun);
    }

    // ---- Corridas (contracts §2) ----

    private void assertRun(Map<String, Object> run, Instant at, boolean first) {
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(Instant.parse((String) run.get("calculatedAt"))).as("calculatedAt %s", at).isEqualTo(at);
        softly.assertThat(run.get("playersProcessed")).as("playersProcessed %s", at).isEqualTo(5);
        softly.assertThat(run.get("playersFailed")).as("playersFailed %s", at).isEqualTo(0);
        softly.assertThat(list(run.get("skippedStrategies"))).as("skippedStrategies %s", at).isEmpty();
        softly.assertThat(run.get("activeStrategy")).as("activeStrategy %s", at).isEqualTo("OFFICIAL");
        softly.assertThat(run.get("marketValueUpdated")).as("marketValueUpdated %s", at).isEqualTo(true);

        Map<String, Map<String, Object>> counts = new HashMap<>();
        for (Object item : list(run.get("strategies"))) {
            Map<String, Object> strategy = map(item);
            counts.put((String) strategy.get("code"), strategy);
        }
        softly.assertThat(counts).as("strategies %s", at).containsOnlyKeys(STRATEGIES);
        for (String code : STRATEGIES) {
            Map<String, Object> strategy = counts.getOrDefault(code, Map.of());
            softly.assertThat(strategy.get("version")).as("%s version %s", code, at).isEqualTo(1);
            boolean insufficient = first && code.equals("MOMENTUM");
            softly.assertThat(strategy.get("calculated")).as("%s calculated %s", code, at)
                    .isEqualTo(insufficient ? 0 : 5);
            softly.assertThat(strategy.get("insufficientHistory")).as("%s insufficientHistory %s", code, at)
                    .isEqualTo(insufficient ? 5 : 0);
        }
        softly.assertAll();
    }

    /** FR-018: después de cada corrida, el valor de mercado es la última cotización de la estrategia activa. */
    private void assertMarketValueFollowsOfficial() {
        SoftAssertions softly = new SoftAssertions();
        players.forEach((league, player) -> {
            List<Map<String, Object>> official = history(player, "OFFICIAL");
            BigDecimal marketValue = playerRepository.findById(player.getId()).orElseThrow().getMarketValue();
            softly.assertThat(marketValue).as("%s marketValue", league)
                    .isEqualByComparingTo(decimal(official.get(official.size() - 1).get("value")));
        });
        softly.assertAll();
    }

    // ---- Historial y vigente por fecha (contracts §3 y §4) ----

    private Map<League, Map<String, List<Map<String, Object>>>> histories() {
        Map<League, Map<String, List<Map<String, Object>>>> histories = new EnumMap<>(League.class);
        players.forEach((league, player) -> {
            Map<String, List<Map<String, Object>>> byStrategy = new HashMap<>();
            for (String strategy : STRATEGIES) {
                byStrategy.put(strategy, history(player, strategy));
            }
            histories.put(league, byStrategy);
        });
        return histories;
    }

    private void assertHistories(Map<League, Map<String, List<Map<String, Object>>>> histories) {
        SoftAssertions softly = new SoftAssertions();
        histories.forEach((league, byStrategy) -> byStrategy.forEach((strategy, quotes) -> {
            String label = league + " " + strategy;
            softly.assertThat(quotes).as(label).hasSize(3);
            if (quotes.size() != 3) {
                return;
            }
            softly.assertThat(quotes).extracting(quote -> Instant.parse((String) quote.get("calculatedAt")))
                    .as("%s calculatedAt", label).containsExactlyElementsOf(RUNS);
            softly.assertThat(quotes).extracting(quote -> quote.get("strategy")).as("%s strategy", label)
                    .containsOnly(strategy);
            softly.assertThat(quotes).extracting(quote -> quote.get("version")).as("%s version", label)
                    .containsOnly(1);
            if (strategy.equals("OFFICIAL")) {
                softly.assertThat(quotes).extracting(quote -> quote.get("status")).as("%s status", label)
                        .containsOnly("CALCULATED");
            } else {
                softly.assertThat(quotes).extracting(quote -> quote.get("status")).as("%s status", label)
                        .containsExactly("INSUFFICIENT_HISTORY", "CALCULATED", "CALCULATED");
                softly.assertThat(decimal(quotes.get(0).get("value"))).as("%s valor inicial", label)
                        .isEqualByComparingTo("1.0000");
            }
        }));
        softly.assertAll();
    }

    private void assertEffectiveQuotes(Map<League, Map<String, List<Map<String, Object>>>> histories) {
        SoftAssertions softly = new SoftAssertions();
        histories.forEach((league, byStrategy) -> byStrategy.forEach((strategy, quotes) -> {
            for (int i = 0; i < RUN_DATES.size(); i++) {
                LocalDate date = RUN_DATES.get(i);
                String label = league + " " + strategy + " " + date;
                ResponseEntity<Map<String, Object>> response = effectiveAt(players.get(league), strategy, date);
                softly.assertThat(response.getStatusCode()).as(label).isEqualTo(HttpStatus.OK);
                Map<String, Object> effective = response.getBody();
                Map<String, Object> expected = quotes.get(i);
                softly.assertThat(effective).as(label).isNotNull();
                if (effective == null) {
                    continue;
                }
                softly.assertThat(decimal(effective.get("value"))).as("%s value", label)
                        .isEqualByComparingTo(decimal(expected.get("value")));
                softly.assertThat(effective.get("status")).as("%s status", label).isEqualTo(expected.get("status"));
                softly.assertThat(effective.get("version")).as("%s version", label).isEqualTo(expected.get("version"));
                softly.assertThat(Instant.parse((String) effective.get("calculatedAt"))).as("%s calculatedAt", label)
                        .isEqualTo(Instant.parse((String) expected.get("calculatedAt")));
            }
        }));
        for (String strategy : STRATEGIES) {
            ResponseEntity<Map<String, Object>> before =
                    effectiveAt(players.get(League.PREMIER_LEAGUE), strategy, BEFORE_FIRST);
            softly.assertThat(before.getStatusCode()).as("%s %s", strategy, BEFORE_FIRST)
                    .isEqualTo(HttpStatus.NOT_FOUND);
            softly.assertThat(before.getBody()).as("%s %s", strategy, BEFORE_FIRST)
                    .containsEntry("error", "quote_not_found");
        }
        softly.assertAll();
    }

    // ---- Evolución (contracts §5) ----

    private void assertEvolution(Map<League, Map<String, List<Map<String, Object>>>> histories) {
        SoftAssertions softly = new SoftAssertions();
        List<BigDecimal> premierOfficial = values(histories.get(League.PREMIER_LEAGUE).get("OFFICIAL"));
        List<BigDecimal> premierMomentum = values(histories.get(League.PREMIER_LEAGUE).get("MOMENTUM"));
        softly.assertThat(premierOfficial.get(2)).as("PREMIER_LEAGUE OFFICIAL sube")
                .isGreaterThan(premierOfficial.get(1));
        softly.assertThat(premierMomentum.get(2)).as("PREMIER_LEAGUE MOMENTUM sube")
                .isGreaterThan(premierMomentum.get(1));

        // La primera ventana de Bundesliga está por debajo de su promedio: con una muy buena, la falta de
        // minutos no alcanzaría para que MOMENTUM baje (research R4).
        List<BigDecimal> bundesligaMomentum = values(histories.get(League.BUNDESLIGA).get("MOMENTUM"));
        softly.assertThat(bundesligaMomentum.get(2)).as("BUNDESLIGA MOMENTUM baja")
                .isLessThan(bundesligaMomentum.get(1));

        histories.forEach((league, byStrategy) -> {
            List<BigDecimal> momentum = values(byStrategy.get("MOMENTUM"));
            for (int i = 1; i < momentum.size(); i++) {
                BigDecimal previous = momentum.get(i - 1);
                BigDecimal limit = previous.multiply(MAX_VARIATION, MathContext.DECIMAL64).add(ROUNDING);
                softly.assertThat(momentum.get(i).subtract(previous).abs()).as("%s MOMENTUM ±15%% %s", league,
                        RUN_DATES.get(i)).isLessThanOrEqualTo(limit);
            }
        });
        softly.assertAll();
    }

    // ---- Ranking (contracts §6) ----

    private void assertRankings(Map<League, Map<String, List<Map<String, Object>>>> histories,
                                Map<String, Object> momentumBeforeLastRun) {
        SoftAssertions softly = new SoftAssertions();
        Map<String, List<Map<String, Object>>> rankings = new HashMap<>();
        for (String strategy : STRATEGIES) {
            Map<String, Object> page = ranking(strategy).getBody();
            List<Map<String, Object>> items = list(page.get("items")).stream().map(QuoteEvolutionE2ETest::map).toList();
            rankings.put(strategy, items);

            List<Map.Entry<Long, BigDecimal>> expected = new ArrayList<>();
            histories.forEach((league, byStrategy) -> {
                List<BigDecimal> values = values(byStrategy.get(strategy));
                expected.add(Map.entry(players.get(league).getId(), values.get(values.size() - 1)));
            });
            expected.sort(Comparator.<Map.Entry<Long, BigDecimal>, BigDecimal>comparing(Map.Entry::getValue)
                    .reversed().thenComparing(Map.Entry::getKey));

            softly.assertThat(((Number) page.get("totalElements")).longValue()).as("%s totalElements", strategy)
                    .isEqualTo(5);
            softly.assertThat(items).extracting(item -> ((Number) map(item.get("player")).get("id")).longValue())
                    .as("%s orden", strategy).containsExactlyElementsOf(expected.stream().map(Map.Entry::getKey).toList());
            softly.assertThat(items).extracting(item -> ((Number) item.get("rank")).intValue()).as("%s rank", strategy)
                    .containsExactly(1, 2, 3, 4, 5);
            softly.assertThat(items).extracting(item -> item.get("strategy")).as("%s strategy", strategy)
                    .containsOnly(strategy);
            softly.assertThat(items).extracting(item -> item.get("version")).as("%s version", strategy)
                    .containsOnly(1);
            for (int k = 0; k < Math.min(items.size(), expected.size()); k++) {
                softly.assertThat(decimal(items.get(k).get("value"))).as("%s valor puesto %d", strategy, k + 1)
                        .isEqualByComparingTo(expected.get(k).getValue());
            }
        }

        List<Map<String, Object>> active = list(ranking(null).getBody().get("items")).stream()
                .map(QuoteEvolutionE2ETest::map).toList();
        softly.assertThat(active).as("ranking sin strategy = OFFICIAL").isEqualTo(rankings.get("OFFICIAL"));

        // FR-017: el ranking MOMENTUM consultado antes de la corrida 3 no se devuelve desde la caché.
        Long premierId = players.get(League.PREMIER_LEAGUE).getId();
        List<BigDecimal> premierMomentum = values(histories.get(League.PREMIER_LEAGUE).get("MOMENTUM"));
        softly.assertThat(valueOf(list(momentumBeforeLastRun.get("items")), premierId)).as("ranking previo")
                .isEqualByComparingTo(premierMomentum.get(1));
        softly.assertThat(valueOf(rankings.get("MOMENTUM"), premierId)).as("ranking final")
                .isEqualByComparingTo(premierMomentum.get(2));
        softly.assertAll();
    }

    private static BigDecimal valueOf(List<?> items, Long playerId) {
        return items.stream().map(QuoteEvolutionE2ETest::map)
                .filter(item -> ((Number) map(item.get("player")).get("id")).longValue() == playerId)
                .map(item -> decimal(item.get("value")))
                .findFirst().orElseThrow();
    }

    // ---- Preparación ----

    /** Como el quickstart de 016: registro por HTTP y rol ADMIN por base; se usa la API key emitida. */
    private void registerAdmin() {
        ResponseEntity<Map<String, Object>> response = client().post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", ADMIN, "email", ADMIN + "@example.com", "password", "e2e-admin-pass"))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        apiKey = (String) response.getBody().get("apiKey");
        assertThat(apiKey).isNotBlank();
        assertThat(jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE username = ?", ADMIN)).isEqualTo(1);
    }

    private void loadStatsForRun(int run) {
        players.forEach((league, player) -> loadStats(player, STATS.get(league).get(run), FETCHED_AT.get(run)));
    }

    private void loadStats(Player player, StatsRow row, Instant fetchedAt) {
        PlayerStats stats = statsRepository.findById(player.getId()).orElseGet(() -> new PlayerStats(player));
        stats.setMinutesPlayed(row.minutes());
        stats.setGoals(row.goals());
        stats.setAssists(row.assists());
        stats.setShots(row.shots());
        stats.setKeyPasses(row.keyPasses());
        stats.setDribbles(row.dribbles());
        stats.setTackles(row.tackles());
        stats.setYellowCards(0);
        stats.setRedCards(0);
        stats.setRating(new BigDecimal(row.rating()));
        stats.setFetchedAt(fetchedAt);
        statsRepository.saveAndFlush(stats);
    }

    // ---- HTTP ----

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private Map<String, Object> recalculateOn(Instant at) {
        evolutionClock.set(at);
        ResponseEntity<Map<String, Object>> response = client().post().uri("/quotes/recalculate")
                .header("X-API-KEY", apiKey)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).as("recálculo %s", at).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private List<Map<String, Object>> history(Player player, String strategy) {
        return client().get().uri("/players/{id}/quotes?strategy={strategy}", player.getId(), strategy)
                .header("X-API-KEY", apiKey)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private ResponseEntity<Map<String, Object>> effectiveAt(Player player, String strategy, LocalDate at) {
        return client().get().uri("/players/{id}/quotes?strategy={strategy}&at={at}", player.getId(), strategy, at)
                .header("X-API-KEY", apiKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                })
                .toEntity(new ParameterizedTypeReference<>() {
                });
    }

    private ResponseEntity<Map<String, Object>> ranking(String strategy) {
        String uri = strategy == null ? "/players/ranking" : "/players/ranking?strategy=" + strategy;
        ResponseEntity<Map<String, Object>> response = client().get().uri(uri)
                .header("X-API-KEY", apiKey)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).as("ranking %s", strategy).isEqualTo(HttpStatus.OK);
        return response;
    }

    private static List<BigDecimal> values(List<Map<String, Object>> quotes) {
        return quotes.stream().map(quote -> decimal(quote.get("value"))).toList();
    }

    private static BigDecimal decimal(Object value) {
        return new BigDecimal(value.toString());
    }

    private static List<?> list(Object value) {
        return (List<?>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private record StatsRow(int minutes, int goals, int assists, int shots, int keyPasses, int dribbles,
                            int tackles, String rating) {
    }

    /** Reloj de la aplicación durante el test: cada corrida cae en el instante que fija {@link #set}. */
    static final class MutableClock extends Clock {

        private volatile Instant instant = RUN_1;

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        /** Delega en este reloj, así un {@link #set} posterior también afecta al reloj con otra zona. */
        @Override
        public Clock withZone(ZoneId zone) {
            MutableClock source = this;
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return zone;
                }

                @Override
                public Clock withZone(ZoneId otherZone) {
                    return source.withZone(otherZone);
                }

                @Override
                public Instant instant() {
                    return source.instant();
                }
            };
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    /** Bean con otro nombre que {@code clock}: el override por nombre está deshabilitado (research R1). */
    @TestConfiguration
    static class EvolutionClockConfig {

        @Bean
        @Primary
        MutableClock evolutionClock() {
            return new MutableClock();
        }
    }
}
