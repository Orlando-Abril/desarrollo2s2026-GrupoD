package com.example.demo.config;

import com.example.demo.adapter.footballdata.dto.FootballDataResponse;
import com.example.demo.adapter.whoscored.dto.WhoScoredPlayerStats;
import com.example.demo.model.League;
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
                List.of("Chelsea"), 25, 0, null, 0, 1, 0, 0, 0, new BigDecimal("6.14"),
                Instant.parse("2026-09-25T13:30:00Z"));

        Object cached = pair.read(pair.write(original));

        assertThat(cached).isInstanceOf(WhoScoredPlayerStats.class).isEqualTo(original);
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
