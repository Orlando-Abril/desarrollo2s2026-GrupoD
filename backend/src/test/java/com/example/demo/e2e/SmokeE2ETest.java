package com.example.demo.e2e;

import com.example.demo.dto.auth.LoginRequest;
import com.example.demo.dto.auth.LoginResponse;
import com.example.demo.dto.auth.RegisterRequest;
import com.example.demo.dto.auth.RegisterResponse;
import com.example.demo.support.E2ETest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@E2ETest
class SmokeE2ETest {

    private static final String USERNAME = "e2e-smoke-user";
    private static final String PASSWORD = "e2e-smoke-password";

    private final RestClient client;

    SmokeE2ETest(@LocalServerPort int port) {
        this.client = RestClient.create("http://localhost:" + port);
    }

    @Test
    void completesTheCriticalHttpFlow() {
        ResponseEntity<RegisterResponse> register = client.post()
                .uri("/auth/register")
                .body(new RegisterRequest(USERNAME, "e2e-smoke@example.com", PASSWORD))
                .retrieve()
                .toEntity(RegisterResponse.class);

        assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(register.getBody()).isNotNull();
        assertThat(register.getBody().apiKey()).isNotBlank();

        ResponseEntity<LoginResponse> login = client.post()
                .uri("/auth/login")
                .body(new LoginRequest(USERNAME, PASSWORD))
                .retrieve()
                .toEntity(LoginResponse.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).isNotNull();
        assertThat(login.getBody().token()).isNotBlank();
        assertThat(login.getBody().tokenType()).isEqualTo("Bearer");

        ResponseEntity<List<Map<String, Object>>> players = client.get()
                .uri("/players")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.getBody().token())
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() { });

        assertThat(players.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(players.getBody()).isNotNull();
        assertThat(players.getHeaders().getFirst("X-Correlation-ID")).isNotBlank();

        ResponseEntity<Map<String, Object>> health = client.get()
                .uri("/actuator/health")
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() { });

        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).containsEntry("status", "UP");
    }
}
