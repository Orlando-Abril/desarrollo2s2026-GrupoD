package com.example.demo.config;

import com.example.demo.adapter.footballdata.dto.FootballDataResponse;
import com.example.demo.adapter.whoscored.dto.WhoScoredPlayerStats;
import com.example.demo.model.League;
import com.example.demo.model.Position;
import com.example.demo.dto.quote.RankingEntryResponse;
import com.example.demo.dto.quote.RankingPageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CacheConfigTest {
    private SerializationPair<Object> valuePair;
    private RedisCacheManager manager;

    @BeforeEach
    void setUp() {
        FootballDataProperties properties = new FootballDataProperties(URI.create("https://example.test"),
                "token", Duration.ofSeconds(3), Duration.ofSeconds(10), Duration.ofHours(6));
        manager = (RedisCacheManager) new CacheConfig().redisCacheManager(
                mock(RedisConnectionFactory.class), JsonMapper.builder().build(), properties, whoScoredProperties());
        manager.initializeCaches();
        valuePair = manager.getCacheConfigurations()
                .get(CacheConfig.COMPETITION_TEAMS_CACHE)
                .getValueSerializationPair();
    }

    @Test
    void competitionTeamsCacheRoundTripsAsFootballDataResponse() {
        FootballDataResponse original = new FootballDataResponse(List.of(
                new FootballDataResponse.Team("1. FC Köln", List.of(
                        new FootballDataResponse.SquadMember(7015L, "Matthias Köbbing", "Goalkeeper",
                                LocalDate.of(1997, 5, 28), "Germany")))));

        Object cached = valuePair.read(valuePair.write(original));

        assertThat(cached).isInstanceOf(FootballDataResponse.class).isEqualTo(original);
    }

    @Test
    void competitionTeamsCacheReadsEntriesStoredWithoutTypeInformation() {
        String legacyEntry = "{\"teams\":[{\"name\":\"1. FC Köln\",\"squad\":[{\"id\":7015,"
                + "\"name\":\"Matthias Köbbing\",\"position\":\"Goalkeeper\","
                + "\"dateOfBirth\":\"1997-05-28\",\"nationality\":\"Germany\"}]}]}";

        Object cached = valuePair.read(ByteBuffer.wrap(legacyEntry.getBytes(StandardCharsets.UTF_8)));

        assertThat(cached).isInstanceOf(FootballDataResponse.class);
        assertThat(((FootballDataResponse) cached).teams().get(0).squad().get(0).id()).isEqualTo(7015L);
    }

    @Test
    void whoScoredPlayerStatsCacheUsesItsOwnTtl() {
        assertThat(manager.getCacheConfigurations().get(CacheConfig.WHOSCORED_PLAYER_STATS_CACHE)
                .getTtlFunction().getTimeToLive(1L, null))
                .isEqualTo(Duration.ofHours(24));
        assertThat(manager.getCacheConfigurations().get(CacheConfig.COMPETITION_TEAMS_CACHE)
                .getTtlFunction().getTimeToLive("PL", null))
                .isEqualTo(Duration.ofHours(6));
    }

    @Test
    void whoScoredPlayerStatsCacheRoundTripsAsTypedValue() {
        SerializationPair<Object> pair = manager.getCacheConfigurations()
                .get(CacheConfig.WHOSCORED_PLAYER_STATS_CACHE).getValueSerializationPair();
        WhoScoredPlayerStats original = new WhoScoredPlayerStats("369430", "Enzo Fernández", "15",
                List.of("Chelsea"), 25, 0, null, 0, 1, 0, 0, 0, 0, new BigDecimal("6.14"),
                Instant.parse("2026-09-25T13:30:00Z"));

        Object cached = pair.read(pair.write(original));

        assertThat(cached).isInstanceOf(WhoScoredPlayerStats.class).isEqualTo(original);
    }

    @Test
    void whoScoredPlayerStatsCachedBeforeDribblesExistedReadsWithNullDribbles() {
        SerializationPair<Object> pair = manager.getCacheConfigurations()
                .get(CacheConfig.WHOSCORED_PLAYER_STATS_CACHE).getValueSerializationPair();
        String cachedBeforeDribbles = """
                {"whoscoredPlayerId":"369430","name":"Enzo Fernández","whoscoredTeamId":"15","teamNames":["Chelsea"],
                 "minutesPlayed":25,"goals":0,"assists":null,"shots":0,"keyPasses":1,"tackles":0,"yellowCards":0,
                 "redCards":0,"rating":6.14,"fetchedAt":"2026-09-25T13:30:00Z"}""";

        Object cached = pair.read(ByteBuffer.wrap(cachedBeforeDribbles.getBytes(StandardCharsets.UTF_8)));

        assertThat(cached).isInstanceOfSatisfying(WhoScoredPlayerStats.class, stats -> {
            assertThat(stats.dribbles()).isNull();
            assertThat(stats.keyPasses()).isEqualTo(1);
        });
    }

    @Test
    void rankingCacheHasOneHourTtlAndRoundTripsAsTypedPageIncludingNullPercentage() {
        var configuration = manager.getCacheConfigurations().get(CacheConfig.QUOTES_RANKING_CACHE);
        assertThat(configuration.getTtlFunction().getTimeToLive("key", null)).isEqualTo(Duration.ofHours(1));
        RankingPageResponse original = new RankingPageResponse(List.of(new RankingEntryResponse(1,
                new RankingEntryResponse.RankingPlayerResponse(7, "Player", "Club", League.LA_LIGA,
                        Set.of(Position.FORWARD)), new BigDecimal("12.3400"), null, "OFFICIAL", 2)),
                0, 20, 1, 1);

        Object cached = configuration.getValueSerializationPair().read(
                configuration.getValueSerializationPair().write(original));

        assertThat(cached).isInstanceOf(RankingPageResponse.class).isEqualTo(original);
        assertThat(((RankingPageResponse) cached).items().get(0).percentageChange()).isNull();
    }

    private static WhoScoredProperties whoScoredProperties() {
        Map<League, Integer> tournaments = new EnumMap<>(League.class);
        for (League league : League.values()) {
            tournaments.put(league, league.ordinal() + 1);
        }
        return new WhoScoredProperties(URI.create("https://ws.test"), "agent", Duration.ofSeconds(5),
                Duration.ofSeconds(15), Duration.ofSeconds(2), Duration.ofHours(24),
                new WhoScoredProperties.BlockRetry(true, Duration.ofSeconds(10)), tournaments,
                new WhoScoredProperties.Sync(false, "0 0 4 * * MON", "UTC"), WhoScoredProperties.Client.HTTP,
                new WhoScoredProperties.Browser("/landing", Duration.ofSeconds(1)));
    }
}
