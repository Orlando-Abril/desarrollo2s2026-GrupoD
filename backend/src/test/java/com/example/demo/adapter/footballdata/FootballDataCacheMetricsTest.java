package com.example.demo.adapter.footballdata;

import com.example.demo.config.CacheConfig;
import com.example.demo.metrics.BusinessMetrics;
import com.example.demo.metrics.MeteredCacheResolver;
import com.example.demo.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Spec 036: el proxy de {@code @Cacheable} usa {@link MeteredCacheResolver} y la segunda consulta sale de la caché.
 * Contexto mínimo: el {@code RestClient} real de {@code FootballDataConfig} no se puede reemplazar sin habilitar
 * bean overriding, así que se arma uno enlazado a {@link MockRestServiceServer}.
 */
@SpringJUnitConfig(FootballDataCacheMetricsTest.Config.class)
class FootballDataCacheMetricsTest {

    @Configuration
    @EnableCaching
    static class Config {

        final RestClient.Builder builder = RestClient.builder().baseUrl("https://football.test/v4");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        @Bean
        MockRestServiceServer server() {
            return server;
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheConfig.COMPETITION_TEAMS_CACHE);
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        BusinessMetrics businessMetrics(MeterRegistry meterRegistry) {
            return new BusinessMetrics(meterRegistry, mock(UserRepository.class));
        }

        @Bean(MeteredCacheResolver.FOOTBALL_DATA)
        MeteredCacheResolver footballDataCacheResolver(CacheManager cacheManager, BusinessMetrics metrics) {
            return new MeteredCacheResolver(cacheManager, metrics);
        }

        @Bean
        FootballDataAdapter footballDataAdapter(BusinessMetrics metrics) {
            return new FootballDataAdapter(builder.build(), metrics);
        }
    }

    @Autowired FootballDataAdapter adapter;
    @Autowired MockRestServiceServer server;
    @Autowired MeterRegistry meterRegistry;

    @Test
    void secondCallComesFromTheCacheWithoutAnHttpRequest() {
        server.expect(once(), requestTo("https://football.test/v4/competitions/PL/teams"))
                .andRespond(withSuccess("{\"teams\":[]}", MediaType.APPLICATION_JSON));

        adapter.fetchCompetitionTeams("PL");
        adapter.fetchCompetitionTeams("PL");

        server.verify();
        assertThat(calls("success")).isEqualTo(1);
        assertThat(calls("cache")).isEqualTo(1);
        assertThat(calls("error")).isZero();
    }

    private double calls(String outcome) {
        return meterRegistry.get("gdmarket.external.calls").tags("source", "football_data", "outcome", outcome)
                .counter().count();
    }
}
