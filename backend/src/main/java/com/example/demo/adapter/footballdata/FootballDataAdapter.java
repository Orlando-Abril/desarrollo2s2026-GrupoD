package com.example.demo.adapter.footballdata;

import com.example.demo.adapter.footballdata.dto.FootballDataResponse;
import com.example.demo.config.CacheConfig;
import com.example.demo.exception.FootballDataException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class FootballDataAdapter {
    private final RestClient restClient;

    public FootballDataAdapter(RestClient footballDataRestClient) {
        this.restClient = footballDataRestClient;
    }

    @Cacheable(cacheNames = CacheConfig.COMPETITION_TEAMS_CACHE,
            key = "#competitionCode", sync = true)
    public FootballDataResponse fetchCompetitionTeams(String competitionCode) {
        try {
            FootballDataResponse response = restClient.get()
                    .uri("/competitions/{code}/teams", competitionCode)
                    .retrieve()
                    .body(FootballDataResponse.class);
            return response == null ? new FootballDataResponse(null) : response;
        } catch (RestClientException ex) {
            throw new FootballDataException("external_source_error",
                    "Football-Data no está disponible", ex);
        }
    }
}
