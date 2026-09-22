package com.example.demo.adapter.footballdata;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class FootballDataRedisIntegrationTest {
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
    void storesJsonAndExpiresCompetitionValue() throws Exception {
        var serializer = new GenericJacksonJsonRedisSerializer(new ObjectMapper());
        var config = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMillis(250))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
        var manager = RedisCacheManager.builder(connectionFactory).cacheDefaults(config).build();
        manager.afterPropertiesSet();
        var cache = manager.getCache("football-data-competition-teams");
        cache.put("PL", List.of("Arsenal FC"));
        assertThat(cache.get("PL", List.class)).containsExactly("Arsenal FC");
        Thread.sleep(350);
        assertThat(cache.get("PL")).isNull();
    }
}
