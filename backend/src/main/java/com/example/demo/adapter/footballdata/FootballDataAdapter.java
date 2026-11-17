package com.example.demo.adapter.footballdata;

import com.example.demo.adapter.footballdata.dto.FootballDataResponse;
import com.example.demo.config.CacheConfig;
import com.example.demo.exception.FootballDataException;
import com.example.demo.metrics.BusinessMetrics;
import com.example.demo.metrics.ExternalCallOutcome;
import com.example.demo.metrics.ExternalSource;
import com.example.demo.metrics.MeteredCacheResolver;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class FootballDataAdapter {
    private final RestClient restClient;
    private final BusinessMetrics metrics;

    public FootballDataAdapter(RestClient footballDataRestClient, BusinessMetrics metrics) {
        this.restClient = footballDataRestClient;
        this.metrics = metrics;
    }

    /** Spec 036: el cuerpo sólo corre cuando la caché no tiene el valor; los aciertos los cuenta el resolvedor. */
    @Cacheable(cacheNames = CacheConfig.COMPETITION_TEAMS_CACHE, cacheResolver = MeteredCacheResolver.FOOTBALL_DATA,
            key = "#competitionCode", sync = true)
    public FootballDataResponse fetchCompetitionTeams(String competitionCode) {
        try {
            FootballDataResponse response = restClient.get()
                    .uri("/competitions/{code}/teams", competitionCode)
                    .retrieve()
                    .body(FootballDataResponse.class);
            metrics.externalCall(ExternalSource.FOOTBALL_DATA, ExternalCallOutcome.SUCCESS);
            return response == null ? new FootballDataResponse(null) : response;
        } catch (RestClientException ex) {
            metrics.externalCall(ExternalSource.FOOTBALL_DATA, ExternalCallOutcome.ERROR);
            throw new FootballDataException("external_source_error",
                    "Football-Data no está disponible", ex);
        } catch (RuntimeException ex) {
            metrics.externalCall(ExternalSource.FOOTBALL_DATA, ExternalCallOutcome.ERROR);
            throw ex;
        }
    }
}
