package com.example.demo.config;

import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.model.ApiKey;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.ApiKeyHasher;
import com.example.demo.security.JwtUtil;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
@TestPropertySource(properties = {
        "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
        "management.endpoint.health.show-details=when-authorized",
        "management.endpoint.health.roles=ADMIN",
        "management.endpoint.health.probes.enabled=true"
})
@Transactional
class HealthEndpointTest {
    private static final String HEALTH = "/actuator/health";
    private static final String SECRET = "test-only-secret-key-never-use-in-production-0123456789";

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;
    @Autowired UserRepository users;
    @Autowired ApiKeyRepository apiKeys;

    private String adminKey;
    private String userKey;

    @BeforeEach
    void setUp() {
        User admin = AuthFixtures.user(users, "health-admin", Role.ADMIN);
        User user = AuthFixtures.user(users, "health-user", Role.USER);
        adminKey = AuthFixtures.apiKey(apiKeys, admin);
        userKey = AuthFixtures.apiKey(apiKeys, user);
    }

    @Test
    void anonymousHealthAndProbesArePublicWithoutDetails() throws Exception {
        for (String path : new String[] {HEALTH, HEALTH + "/liveness", HEALTH + "/readiness"}) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(header().exists(CorrelationIdFilter.HEADER))
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components").doesNotExist());
        }
    }

    @Test
    void adminJwtAndApiKeySeeDependencies() throws Exception {
        mvc.perform(get(HEALTH).header(HttpHeaders.AUTHORIZATION, bearer("health-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.redis.status").value("UP"))
                .andExpect(jsonPath("$.components.diskSpace.status").value("UP"));
        mvc.perform(get(HEALTH).header("X-API-KEY", adminKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.redis.status").value("UP"))
                .andExpect(jsonPath("$.components.diskSpace.status").value("UP"));
    }

    @Test
    void userJwtAndApiKeyDoNotSeeDependencies() throws Exception {
        mvc.perform(get(HEALTH).header(HttpHeaders.AUTHORIZATION, bearer("health-user")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get(HEALTH).header("X-API-KEY", userKey))
                .andExpect(status().isOk()).andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void invalidOrExpiredBearerIsUnauthorizedOnAllHealthRoutes() throws Exception {
        String expired = new JwtUtil(SECRET, -1_000).generateToken("health-admin");
        for (String path : new String[] {HEALTH, HEALTH + "/liveness", HEALTH + "/readiness"}) {
            for (String token : new String[] {"invalid-token", expired}) {
                mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.error").value("unauthorized"));
            }
        }
    }

    @Test
    void nonexistentOrInactiveApiKeyIsUnauthorizedOnAllHealthRoutes() throws Exception {
        String inactive = AuthFixtures.apiKey(apiKeys, null);
        ApiKey record = apiKeys.findByKeyHash(ApiKeyHasher.sha256Hex(inactive)).orElseThrow();
        record.setActive(false);
        apiKeys.save(record);
        for (String path : new String[] {HEALTH, HEALTH + "/liveness", HEALTH + "/readiness"}) {
            for (String key : new String[] {"missing-key", inactive}) {
                mvc.perform(get(path).header("X-API-KEY", key))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.error").value("unauthorized"));
            }
        }
    }

    private String bearer(String username) {
        return "Bearer " + jwtUtil.generateToken(username);
    }

    @TestConfiguration
    static class RedisHealthForTest {
        @Bean
        HealthIndicator redisHealthIndicator() {
            return () -> Health.up().build();
        }
    }
}
