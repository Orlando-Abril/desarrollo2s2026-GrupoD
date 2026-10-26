package com.example.demo.config;

import com.example.demo.adapter.footballdata.dto.FootballDataResponse;
import com.example.demo.adapter.whoscored.dto.WhoScoredPlayerStats;
import com.example.demo.dto.quote.RankingPageResponse;
import tools.jackson.databind.ObjectMapper;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.util.Map;
import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String COMPETITION_TEAMS_CACHE = "football-data-competition-teams";
    public static final String WHOSCORED_PLAYER_STATS_CACHE = "whoscored-player-stats";
    public static final String QUOTES_RANKING_CACHE = "quotes-ranking";

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
    CacheManager redisCacheManager(RedisConnectionFactory connectionFactory,
                                   ObjectMapper objectMapper,
                                   FootballDataProperties properties,
                                   WhoScoredProperties whoScoredProperties) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJacksonJsonRedisSerializer(objectMapper)));
        // Serializador tipado: sin información de tipo, Jackson deserializa como LinkedHashMap
        // y el cache hit rompe con ClassCastException al castear a FootballDataResponse.
        RedisCacheConfiguration competitionConfig = defaults
                .entryTtl(properties.cacheTtl())
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new JacksonJsonRedisSerializer<>(objectMapper, FootballDataResponse.class)));
        RedisCacheConfiguration playerStatsConfig = defaults
                .entryTtl(whoScoredProperties.cacheTtl())
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new JacksonJsonRedisSerializer<>(objectMapper, WhoScoredPlayerStats.class)));
        RedisCacheConfiguration rankingConfig = defaults.entryTtl(Duration.ofHours(1))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new JacksonJsonRedisSerializer<>(objectMapper, RankingPageResponse.class)));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(Map.of(
                        COMPETITION_TEAMS_CACHE, competitionConfig,
                        WHOSCORED_PLAYER_STATS_CACHE, playerStatsConfig,
                        QUOTES_RANKING_CACHE, rankingConfig))
                .build();
    }
}
