package com.example.demo.config;

import com.example.demo.model.Role;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtUtil;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
@Transactional
@TestPropertySource(properties = {
        "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
        "management.endpoint.health.probes.enabled=true",
        "management.metrics.tags.application=demo",
        "management.metrics.distribution.percentiles-histogram.http.server.requests=true",
        "management.metrics.distribution.percentiles.http.server.requests=0.5,0.95,0.99"
})
class ActuatorMonitoringTest {
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @Autowired UserRepository users;
    @Autowired ApiKeyRepository apiKeys;

    private String adminToken;
    private String userToken;
    private String adminKey;
    private String userKey;

    @BeforeEach
    void setUp() {
        AuthFixtures.user(users, "metrics-admin", Role.ADMIN);
        adminToken = "Bearer " + jwt.generateToken("metrics-admin");
        userKey = AuthFixtures.apiKey(apiKeys, AuthFixtures.user(users, "metrics-user", Role.USER));
        userToken = "Bearer " + jwt.generateToken("metrics-user");
        adminKey = AuthFixtures.apiKey(apiKeys, null);
    }

    @Test
    void metricsAndPrometheusRequireAdmin() throws Exception {
        for (String path : new String[] {"/actuator/metrics", "/actuator/metrics/http.server.requests",
                "/actuator/prometheus"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, userToken))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("forbidden"));
            mvc.perform(get(path).header("X-API-KEY", userKey)).andExpect(status().isForbidden());
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, adminToken)).andExpect(status().isOk());
            mvc.perform(get(path).header("X-API-KEY", adminKey)).andExpect(status().isOk());
        }
    }

    @Test
    void prometheusExposesHttpLatencyAndErrorSeriesWithApplicationTag() throws Exception {
        mvc.perform(get("/players").header(HttpHeaders.AUTHORIZATION, adminToken)).andExpect(status().isOk());
        mvc.perform(get("/players").param("league", "INVALID")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isBadRequest());

        String text = mvc.perform(get("/actuator/prometheus").header("X-API-KEY", adminKey))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType())
                        .contains("text/plain"))
                .andReturn().getResponse().getContentAsString();

        assertThat(text).contains("http_server_requests_seconds_count", "http_server_requests_seconds_bucket");
        assertThat(text).contains("application=\"demo\"");
        assertThat(text.lines().filter(line -> !line.isBlank() && !line.startsWith("#")))
                .allSatisfy(line -> assertThat(line).contains("application=\"demo\""));
        assertThat(text.lines().filter(line -> line.startsWith("http_server_requests_seconds_count")
                        && line.contains("uri=\"/players\"")))
                .anySatisfy(line -> assertThat(line).contains("status=\"200\""));
        assertThat(text.lines().filter(line -> line.startsWith("http_server_requests_seconds_count")
                        && line.contains("uri=\"/players\"")))
                .anySatisfy(line -> assertThat(line).contains("status=\"400\""));

        mvc.perform(get("/actuator/metrics/http.server.requests")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("http.server.requests"));
    }

    @Test
    void excludedActuatorEndpointsDoNotExposeData() throws Exception {
        for (String path : new String[] {"/actuator/env", "/actuator/beans", "/actuator/heapdump"}) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, adminToken))
                    .andExpect(status().isNotFound());
        }
    }
}
