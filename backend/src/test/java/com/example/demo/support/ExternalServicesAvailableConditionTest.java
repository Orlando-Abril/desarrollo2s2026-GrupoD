package com.example.demo.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalServicesAvailableConditionTest {

    @Test
    void loadsEndpointsFromTheE2EApplicationProperties() {
        var properties = ExternalServicesAvailableCondition.e2eProperties();

        assertThat(properties.getProperty("spring.datasource.url")).isNotBlank();
        assertThat(properties.getProperty("spring.data.redis.host")).isNotBlank();
        assertThat(properties.getProperty("spring.data.redis.port")).isNotBlank();
        assertThat(ExternalServicesAvailableCondition.postgresEndpoint(properties).host()).isNotBlank();
        assertThat(ExternalServicesAvailableCondition.redisEndpoint(properties).host()).isNotBlank();
    }

    @Test
    void usesCiDefaultsWhenBothSocketsAreAvailable() {
        List<String> endpoints = new ArrayList<>();
        ExternalServicesAvailableCondition condition = condition(defaultProperties(),
                (host, port, timeout) -> {
                    endpoints.add(host + ":" + port + ":" + timeout);
                    return true;
                });

        ConditionEvaluationResult result = condition.evaluateExecutionCondition(null);

        assertThat(result.isDisabled()).isFalse();
        assertThat(endpoints).containsExactly("localhost:5432:500", "localhost:6379:500");
    }

    @Test
    void usesConfiguredEndpoints() {
        MockEnvironment properties = defaultProperties()
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.internal:5544/custom")
                .withProperty("spring.data.redis.host", "cache.internal")
                .withProperty("spring.data.redis.port", "6388");
        List<String> endpoints = new ArrayList<>();

        ConditionEvaluationResult result = condition(properties, (host, port, timeout) -> {
            endpoints.add(host + ":" + port);
            return true;
        }).evaluateExecutionCondition(null);

        assertThat(result.isDisabled()).isFalse();
        assertThat(endpoints).containsExactly("db.internal:5544", "cache.internal:6388");
    }

    @Test
    void disablesWhenPostgresIsUnavailable() {
        ConditionEvaluationResult result = condition(defaultProperties(),
                (host, port, timeout) -> port != 5432).evaluateExecutionCondition(null);

        assertThat(result.isDisabled()).isTrue();
        assertThat(result.getReason()).hasValueSatisfying(reason ->
                assertThat(reason).contains("PostgreSQL localhost:5432").doesNotContain("Redis"));
    }

    @Test
    void disablesWhenRedisIsUnavailable() {
        ConditionEvaluationResult result = condition(defaultProperties(),
                (host, port, timeout) -> port != 6379).evaluateExecutionCondition(null);

        assertThat(result.isDisabled()).isTrue();
        assertThat(result.getReason()).hasValueSatisfying(reason ->
                assertThat(reason).contains("Redis localhost:6379").doesNotContain("PostgreSQL"));
    }

    @Test
    void reportsAllUnavailableServicesInOneReason() {
        ConditionEvaluationResult result = condition(defaultProperties(),
                (host, port, timeout) -> false).evaluateExecutionCondition(null);

        assertThat(result.isDisabled()).isTrue();
        assertThat(result.getReason()).hasValueSatisfying(reason ->
                assertThat(reason).contains("PostgreSQL localhost:5432", "Redis localhost:6379"));
    }

    @Test
    void invalidConfigurationIsDisabledWithoutLeakingUrlOrPassword() {
        MockEnvironment properties = defaultProperties()
                .withProperty("spring.datasource.url",
                        "jdbc:postgresql://db host:99999/secret_database")
                .withProperty("spring.datasource.password", "super-secret-password")
                .withProperty("spring.data.redis.port", "not-a-port");

        ConditionEvaluationResult result = condition(properties,
                (host, port, timeout) -> true).evaluateExecutionCondition(null);

        assertThat(result.isDisabled()).isTrue();
        assertThat(result.getReason()).hasValueSatisfying(reason -> assertThat(reason)
                .contains("PostgreSQL configuration is invalid", "Redis configuration is invalid")
                .doesNotContain("secret_database", "super-secret-password", "db host"));
    }

    @Test
    void ciTrueEnablesWithoutProbingSockets() {
        for (String value : List.of("true", "TRUE", "TrUe")) {
            ExternalServicesAvailableCondition condition = condition(
                    defaultProperties().withProperty("CI", value),
                    (host, port, timeout) -> {
                        throw new AssertionError("CI must not probe sockets before enabling the test");
                    });

            ConditionEvaluationResult result = condition.evaluateExecutionCondition(null);

            assertThat(result.isDisabled()).isFalse();
            assertThat(result.getReason()).hasValueSatisfying(reason -> assertThat(reason).contains("CI=true"));
        }
    }

    @Test
    void valuesOtherThanTrueRemainLocal() {
        for (String value : List.of("", "false", "1", "yes")) {
            ConditionEvaluationResult result = condition(
                    defaultProperties().withProperty("CI", value),
                    (host, port, timeout) -> false).evaluateExecutionCondition(null);
            assertThat(result.isDisabled()).isTrue();
        }
    }

    private ExternalServicesAvailableCondition condition(
            MockEnvironment properties,
            ExternalServicesAvailableCondition.SocketProbe probe) {
        return new ExternalServicesAvailableCondition(() -> properties, probe);
    }

    private MockEnvironment defaultProperties() {
        return new MockEnvironment()
                .withProperty("spring.datasource.url",
                        "jdbc:postgresql://localhost:5432/desarrollo2_grupod")
                .withProperty("spring.data.redis.host", "localhost")
                .withProperty("spring.data.redis.port", "6379");
    }
}
