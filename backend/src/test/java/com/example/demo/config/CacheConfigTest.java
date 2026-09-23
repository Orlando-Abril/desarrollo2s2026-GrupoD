package com.example.demo.config;

import com.example.demo.adapter.footballdata.dto.FootballDataResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CacheConfigTest {
    private SerializationPair<Object> valuePair;

    @BeforeEach
    void setUp() {
        FootballDataProperties properties = new FootballDataProperties(URI.create("https://example.test"),
                "token", Duration.ofSeconds(3), Duration.ofSeconds(10), Duration.ofHours(6),
                "0 0 */6 * * *", Duration.ofMinutes(5), true);
        RedisCacheManager manager = (RedisCacheManager) new CacheConfig().redisCacheManager(
                mock(RedisConnectionFactory.class), JsonMapper.builder().build(), properties);
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
}
