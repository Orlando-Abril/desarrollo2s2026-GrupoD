package com.example.demo.adapter.whoscored;

import com.example.demo.adapter.whoscored.dto.WhoScoredPlayerStats;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class WhoScoredRedisCacheIntegrationTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    static LettuceConnectionFactory connectionFactory;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @Test
    void storesTypedPlayerStatsWithTheConfiguredTtl() {
        var serializer = new JacksonJsonRedisSerializer<>(JsonMapper.builder().build(), WhoScoredPlayerStats.class);
        var config = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(5))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
        var manager = RedisCacheManager.builder(connectionFactory).cacheDefaults(config).build();
        manager.afterPropertiesSet();
        var cache = manager.getCache("whoscored-player-stats");
        var stats = new WhoScoredPlayerStats("123761", "Bruno Fernandes", "32", List.of("Man Utd", "Manchester United"),
                450, 3, 1, 19, 13, 5, 0, 0, new BigDecimal("7.39"), Instant.parse("2026-09-25T13:30:00Z"));

        cache.put(42L, stats);

        assertThat(cache.get(42L, WhoScoredPlayerStats.class)).isEqualTo(stats);
        // TTL que Redis le asignó a la entrada (sin esperar a que venza): > 0 y ≤ 5 minutos.
        try (var connection = connectionFactory.getConnection()) {
            Long ttlMillis = connection.keyCommands()
                    .pTtl("whoscored-player-stats::42".getBytes(StandardCharsets.UTF_8));
            assertThat(ttlMillis).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(5).toMillis());
        }
    }
}
