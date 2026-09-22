package com.example.demo.adapter.footballdata;

import com.example.demo.adapter.footballdata.dto.FootballDataResponse;
import com.example.demo.config.CacheConfig;
import com.example.demo.exception.FootballDataException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class FootballDataAdapter {
    private final RestClient restClient;
    private final Timer requestTimer;
    private final Counter errorCounter;

    public FootballDataAdapter(RestClient footballDataRestClient,
                               @Qualifier("footballDataLatency") Timer requestTimer,
                               MeterRegistry registry) {
        this.restClient = footballDataRestClient;
        this.requestTimer = requestTimer;
        this.errorCounter = registry.counter("football.data.errors");
    }

    @Cacheable(cacheNames = CacheConfig.COMPETITION_TEAMS_CACHE,
            key = "#competitionCode", sync = true)
    public FootballDataResponse fetchCompetitionTeams(String competitionCode) {
        try {
            FootballDataResponse response = requestTimer.record(() -> restClient.get()
                    .uri("/competitions/{code}/teams", competitionCode)
                    .retrieve()
                    .body(FootballDataResponse.class));
            return response == null ? new FootballDataResponse(null) : response;
        } catch (RestClientException ex) {
            errorCounter.increment();
            throw new FootballDataException("external_source_error",
                    "Football-Data no está disponible", ex);
        }
    }
}
