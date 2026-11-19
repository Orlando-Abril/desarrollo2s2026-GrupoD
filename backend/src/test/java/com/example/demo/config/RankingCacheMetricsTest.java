package com.example.demo.config;

import com.example.demo.dto.quote.RankingPageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RankingCacheMetricsTest {
    @Test
    void rankingRedisCacheRecordsMissAndHitWithExistingTtl() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisStringCommands strings = mock(RedisStringCommands.class);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.stringCommands()).thenReturn(strings);
        var properties = new FootballDataProperties(URI.create("https://example.test"), "token",
                Duration.ofSeconds(3), Duration.ofSeconds(10), Duration.ofHours(6));
        var whoScored = new WhoScoredProperties(URI.create("https://example.test"), "agent",
                Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofSeconds(2), Duration.ofHours(24),
                new WhoScoredProperties.BlockRetry(true, Duration.ofSeconds(10)), new EnumMap<>(com.example.demo.model.League.class),
                new WhoScoredProperties.Sync(false, "0 0 4 * * MON", "UTC"), WhoScoredProperties.Client.HTTP,
                new WhoScoredProperties.Browser("/landing", Duration.ofSeconds(1)));
        RedisCacheManager manager = (RedisCacheManager) new CacheConfig().redisCacheManager(factory,
                JsonMapper.builder().build(), properties, whoScored);
        manager.initializeCaches();
        RedisCache cache = (RedisCache) manager.getCache(CacheConfig.QUOTES_RANKING_CACHE);
        RankingPageResponse page = new RankingPageResponse(List.of(), 0, 20, 0, 0);
        ByteBuffer serialized = manager.getCacheConfigurations().get(CacheConfig.QUOTES_RANKING_CACHE)
                .getValueSerializationPair().write(page);
        byte[] bytes = new byte[serialized.remaining()];
        serialized.get(bytes);
        when(strings.get(any(byte[].class))).thenReturn(null, bytes);
        assertThat(cache.get("missing")).isNull();
        assertThat(cache.get("present", RankingPageResponse.class)).isEqualTo(page);
        assertThat(cache.getStatistics().getMisses()).isEqualTo(1);
        assertThat(cache.getStatistics().getHits()).isEqualTo(1);
        assertThat(manager.getCacheConfigurations().get(CacheConfig.QUOTES_RANKING_CACHE)
                .getTtlFunction().getTimeToLive("key", null)).isEqualTo(Duration.ofHours(1));
    }
}
