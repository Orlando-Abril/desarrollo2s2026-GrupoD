package com.example.demo.adapter.footballdata;

import com.example.demo.adapter.footballdata.dto.FootballDataResponse;
import com.example.demo.exception.FootballDataException;
import com.example.demo.metrics.BusinessMetrics;
import com.example.demo.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FootballDataAdapterTest {
    private MockRestServiceServer server;
    private FootballDataAdapter adapter;
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final BusinessMetrics metrics = new BusinessMetrics(meterRegistry, mock(UserRepository.class));

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://football.test/v4")
                .defaultHeader("X-Auth-Token", "test-token");
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new FootballDataAdapter(builder.build(), metrics);
    }

    @Test
    void fetchesCompetitionTeamsWithTokenAndDeserializesSquad() {
        server.expect(once(), requestTo("https://football.test/v4/competitions/PL/teams"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("X-Auth-Token", "test-token"))
                .andRespond(withSuccess("""
                        {"teams":[{"name":"Arsenal FC","squad":[{"id":3180,"name":"Bukayo Saka", "position":"Offence", "nationality":"England", "dateOfBirth":"2001-09-05"}]}]}
                        """, MediaType.APPLICATION_JSON));
        FootballDataResponse result = adapter.fetchCompetitionTeams("PL");
        assertThat(result.teams()).singleElement().satisfies(team -> {
            assertThat(team.name()).isEqualTo("Arsenal FC");
            assertThat(team.squad()).singleElement().extracting(FootballDataResponse.SquadMember::id)
                    .isEqualTo(3180L);
        });
        server.verify();
    }

    // --- spec 036: métricas de la llamada ------------------------------------------------------------

    @Test
    void successfulCallIsCountedAsSuccess() {
        server.expect(once(), requestTo("https://football.test/v4/competitions/PL/teams"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        adapter.fetchCompetitionTeams("PL");

        assertThat(calls("success")).isEqualTo(1);
        assertThat(calls("error")).isZero();
    }

    @Test
    void httpFailureIsCountedAsErrorAndKeepsTheSameException() {
        server.expect(once(), requestTo("https://football.test/v4/competitions/SA/teams"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.fetchCompetitionTeams("SA")).isInstanceOf(FootballDataException.class);

        assertThat(calls("error")).isEqualTo(1);
        assertThat(calls("success")).isZero();
    }

    @Test
    void unexpectedFailureIsCountedAsErrorAndRethrownUnwrapped() {
        RestClient broken = mock(RestClient.class);
        IllegalStateException failure = new IllegalStateException("boom");
        when(broken.get()).thenThrow(failure);

        assertThatThrownBy(() -> new FootballDataAdapter(broken, metrics).fetchCompetitionTeams("PL"))
                .isSameAs(failure);

        assertThat(calls("error")).isEqualTo(1);
    }

    private double calls(String outcome) {
        return meterRegistry.get("gdmarket.external.calls").tags("source", "football_data", "outcome", outcome)
                .counter().count();
    }

    @Test
    void emptyPayloadProducesEmptyTeams() {
        server.expect(once(), requestTo("https://football.test/v4/competitions/PD/teams"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThat(adapter.fetchCompetitionTeams("PD").teams()).isEmpty();
    }

    @Test
    void httpFailureIsSanitizedAndNotRetried() {
        server.expect(once(), requestTo("https://football.test/v4/competitions/SA/teams"))
                .andRespond(withResourceNotFound());
        assertThatThrownBy(() -> adapter.fetchCompetitionTeams("SA"))
                .isInstanceOf(FootballDataException.class)
                .hasMessage("Football-Data no está disponible");
        server.verify();
    }

    @Test
    void rateLimitIsSanitizedAndNotRetried() {
        server.expect(once(), requestTo("https://football.test/v4/competitions/BL1/teams"))
                .andRespond(withTooManyRequests());
        assertThatThrownBy(() -> adapter.fetchCompetitionTeams("BL1"))
                .isInstanceOf(FootballDataException.class).hasMessageNotContaining("test-token");
        server.verify();
    }

    @Test
    void serverFailureIsSanitizedAndNotRetried() {
        server.expect(once(), requestTo("https://football.test/v4/competitions/FL1/teams"))
                .andRespond(withServerError());
        assertThatThrownBy(() -> adapter.fetchCompetitionTeams("FL1"))
                .isInstanceOf(FootballDataException.class);
        server.verify();
    }
}
