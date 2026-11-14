package com.example.demo.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class PublicRouteFilterTest {
    private final JwtAuthFilter jwt = new JwtAuthFilter(null, null);
    private final ApiKeyAuthFilter apiKey = new ApiKeyAuthFilter(null);

    @ParameterizedTest
    @ValueSource(strings = {
            "/auth/register", "/auth/login", "/auth/refresh", "/auth/logout",
            "/swagger-ui/index.html", "/v3/api-docs/actuator", "/error", "/actuator/info"
    })
    void allOtherPublicRoutesStillSkipBothFilters(String path) {
        assertSkipped(path, null, null, true, true);
        assertSkipped(path, "Bearer invalid-token", null, true, true);
        assertSkipped(path, null, "nonexistent-key", true, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    void healthOnlyRunsFilterForItsPresentedCredential(String path) {
        assertSkipped(path, null, null, true, true);
        assertSkipped(path, "Bearer invalid-token", null, false, true);
        assertSkipped(path, null, "nonexistent-key", true, false);
    }

    private void assertSkipped(String path, String authorization, String key,
                               boolean jwtSkipped, boolean apiKeySkipped) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (authorization != null) request.addHeader("Authorization", authorization);
        if (key != null) request.addHeader("X-API-KEY", key);
        assertThat(jwt.shouldNotFilter(request)).isEqualTo(jwtSkipped);
        assertThat(apiKey.shouldNotFilter(request)).isEqualTo(apiKeySkipped);
    }
}
