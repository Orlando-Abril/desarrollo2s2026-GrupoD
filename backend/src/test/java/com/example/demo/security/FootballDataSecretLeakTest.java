package com.example.demo.security;

import com.example.demo.adapter.footballdata.FootballDataAdapter;
import com.example.demo.exception.FootballDataException;
import com.example.demo.metrics.BusinessMetrics;
import com.example.demo.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class FootballDataSecretLeakTest {
    @Test
    void errorsNeverExposeTokenOrExternalPayload() {
        String token = "super-secret-football-token";
        RestClient.Builder builder = RestClient.builder().baseUrl("https://football.test/v4")
                .defaultHeader("X-Auth-Token", token);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> { }).andRespond(withServerError().body("sensitive upstream payload"));
        var adapter = new FootballDataAdapter(builder.build(),
                new BusinessMetrics(new SimpleMeterRegistry(), mock(UserRepository.class)));
        Throwable failure = catchThrowable(() -> adapter.fetchCompetitionTeams("PL"));
        assertThat(failure).isInstanceOf(FootballDataException.class);
        assertThat(failure.getMessage()).doesNotContain(token).doesNotContain("sensitive upstream payload");
    }
}
