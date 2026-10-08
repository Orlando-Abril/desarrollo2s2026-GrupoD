package com.example.demo.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ExternalServicesAvailableCondition implements ExecutionCondition {

    static final String DEFAULT_DB_URL = "jdbc:postgresql://localhost:5432/desarrollo2_grupod";
    static final String DEFAULT_REDIS_HOST = "localhost";
    static final int DEFAULT_REDIS_PORT = 6379;
    static final int CONNECT_TIMEOUT_MS = 500;

    private final Supplier<Map<String, String>> environmentSupplier;
    private final SocketProbe socketProbe;

    public ExternalServicesAvailableCondition() {
        this(System::getenv, ExternalServicesAvailableCondition::canConnect);
    }

    ExternalServicesAvailableCondition(Supplier<Map<String, String>> environmentSupplier,
                                       SocketProbe socketProbe) {
        this.environmentSupplier = environmentSupplier;
        this.socketProbe = socketProbe;
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        Map<String, String> environment = environmentSupplier.get();
        if ("true".equalsIgnoreCase(environment.getOrDefault("CI", "").trim())) {
            return ConditionEvaluationResult.enabled(
                    "CI=true: PostgreSQL and Redis are mandatory for end-to-end tests");
        }

        List<String> unavailable = new ArrayList<>();
        inspectPostgres(environment, unavailable);
        inspectRedis(environment, unavailable);

        if (unavailable.isEmpty()) {
            return ConditionEvaluationResult.enabled("PostgreSQL and Redis sockets are available");
        }
        return ConditionEvaluationResult.disabled("E2E skipped: " + String.join("; ", unavailable));
    }

    private void inspectPostgres(Map<String, String> environment, List<String> unavailable) {
        try {
            Endpoint endpoint = postgresEndpoint(environment);
            if (!socketProbe.isAvailable(endpoint.host(), endpoint.port(), CONNECT_TIMEOUT_MS)) {
                unavailable.add("PostgreSQL " + endpoint.displayName() + " is unavailable");
            }
        } catch (IllegalArgumentException exception) {
            unavailable.add("PostgreSQL configuration is invalid");
        }
    }

    private void inspectRedis(Map<String, String> environment, List<String> unavailable) {
        try {
            Endpoint endpoint = redisEndpoint(environment);
            if (!socketProbe.isAvailable(endpoint.host(), endpoint.port(), CONNECT_TIMEOUT_MS)) {
                unavailable.add("Redis " + endpoint.displayName() + " is unavailable");
            }
        } catch (IllegalArgumentException exception) {
            unavailable.add("Redis configuration is invalid");
        }
    }

    static Endpoint postgresEndpoint(Map<String, String> environment) {
        String jdbcUrl = environment.getOrDefault("E2E_DB_URL", DEFAULT_DB_URL);
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:")) {
            throw new IllegalArgumentException("Invalid JDBC URL");
        }
        URI uri;
        try {
            uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid JDBC URL", exception);
        }
        if (!"postgresql".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Invalid PostgreSQL endpoint");
        }
        int port = uri.getPort() == -1 ? 5432 : validPort(uri.getPort());
        return new Endpoint(uri.getHost(), port);
    }

    static Endpoint redisEndpoint(Map<String, String> environment) {
        String host = environment.getOrDefault("REDIS_HOST", DEFAULT_REDIS_HOST);
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Invalid Redis host");
        }
        String rawPort = environment.get("REDIS_PORT");
        int port;
        try {
            port = rawPort == null ? DEFAULT_REDIS_PORT : Integer.parseInt(rawPort);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid Redis port", exception);
        }
        return new Endpoint(host, validPort(port));
    }

    private static int validPort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port out of range");
        }
        return port;
    }

    private static boolean canConnect(String host, int port, int timeoutMillis) {
        // TCP reachability is intentionally the only local precondition. An open socket does
        // not validate the service protocol, database existence, or credentials; those errors
        // must surface while Spring creates the E2E context instead of becoming false skips.
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            return false;
        }
    }

    @FunctionalInterface
    interface SocketProbe {
        boolean isAvailable(String host, int port, int timeoutMillis);
    }

    record Endpoint(String host, int port) {
        String displayName() {
            return host + ":" + port;
        }
    }
}
