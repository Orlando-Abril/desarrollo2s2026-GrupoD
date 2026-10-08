package com.example.demo.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalServicesAvailableConditionTest {

    @Test
    void usesCiDefaultsWhenBothSocketsAreAvailable() {
        List<String> endpoints = new ArrayList<>();
        ExternalServicesAvailableCondition condition = condition(Map.of(),
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
        Map<String, String> environment = Map.of(
                "E2E_DB_URL", "jdbc:postgresql://db.internal:5544/custom",
                "REDIS_HOST", "cache.internal",
                "REDIS_PORT", "6388");
        List<String> endpoints = new ArrayList<>();

        ConditionEvaluationResult result = condition(environment, (host, port, timeout) -> {
            endpoints.add(host + ":" + port);
            return true;
        }).evaluateExecutionCondition(null);

        assertThat(result.isDisabled()).isFalse();
        assertThat(endpoints).containsExactly("db.internal:5544", "cache.internal:6388");
    }

    @Test
    void disablesWhenPostgresIsUnavailable() {
        ConditionEvaluationResult result = condition(Map.of(),
                (host, port, timeout) -> port != 5432).evaluateExecutionCondition(null);

        assertThat(result.isDisabled()).isTrue();
        assertThat(result.getReason()).hasValueSatisfying(reason ->
                assertThat(reason).contains("PostgreSQL localhost:5432").doesNotContain("Redis"));
    }

    @Test
    void disablesWhenRedisIsUnavailable() {
        ConditionEvaluationResult result = condition(Map.of(),
                (host, port, timeout) -> port != 6379).evaluateExecutionCondition(null);

        assertThat(result.isDisabled()).isTrue();
        assertThat(result.getReason()).hasValueSatisfying(reason ->
                assertThat(reason).contains("Redis localhost:6379").doesNotContain("PostgreSQL"));
    }

    @Test
    void reportsAllUnavailableServicesInOneReason() {
        ConditionEvaluationResult result = condition(Map.of(),
                (host, port, timeout) -> false).evaluateExecutionCondition(null);

        assertThat(result.isDisabled()).isTrue();
        assertThat(result.getReason()).hasValueSatisfying(reason ->
                assertThat(reason).contains("PostgreSQL localhost:5432", "Redis localhost:6379"));
    }

    @Test
    void invalidConfigurationIsDisabledWithoutLeakingUrlOrPassword() {
        Map<String, String> environment = new HashMap<>();
        environment.put("E2E_DB_URL", "jdbc:postgresql://db host:99999/secret_database");
        environment.put("E2E_DB_PASSWORD", "super-secret-password");
        environment.put("REDIS_PORT", "not-a-port");

        ConditionEvaluationResult result = condition(environment,
                (host, port, timeout) -> true).evaluateExecutionCondition(null);

        assertThat(result.isDisabled()).isTrue();
        assertThat(result.getReason()).hasValueSatisfying(reason -> assertThat(reason)
                .contains("PostgreSQL configuration is invalid", "Redis configuration is invalid")
                .doesNotContain("secret_database", "super-secret-password", "db host"));
    }

    @Test
    void ciTrueEnablesWithoutProbingSockets() {
        for (String value : List.of("true", "TRUE", "TrUe")) {
            ExternalServicesAvailableCondition condition = condition(Map.of("CI", value),
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
            ConditionEvaluationResult result = condition(Map.of("CI", value),
                    (host, port, timeout) -> false).evaluateExecutionCondition(null);
            assertThat(result.isDisabled()).isTrue();
        }
    }

    private ExternalServicesAvailableCondition condition(
            Map<String, String> environment,
            ExternalServicesAvailableCondition.SocketProbe probe) {
        return new ExternalServicesAvailableCondition(() -> environment, probe);
    }
}
