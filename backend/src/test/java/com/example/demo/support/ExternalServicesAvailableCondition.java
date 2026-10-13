package com.example.demo.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ExternalServicesAvailableCondition implements ExecutionCondition {

    static final int CONNECT_TIMEOUT_MS = 500;

    private final Supplier<PropertyResolver> propertiesSupplier;
    private final SocketProbe socketProbe;

    public ExternalServicesAvailableCondition() {
        this(ExternalServicesAvailableCondition::e2eProperties,
                ExternalServicesAvailableCondition::canConnect);
    }

    ExternalServicesAvailableCondition(Supplier<PropertyResolver> propertiesSupplier,
                                       SocketProbe socketProbe) {
        this.propertiesSupplier = propertiesSupplier;
        this.socketProbe = socketProbe;
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        PropertyResolver properties = propertiesSupplier.get();
        if ("true".equalsIgnoreCase(properties.getProperty("CI", "").trim())) {
            return ConditionEvaluationResult.enabled(
                    "CI=true: PostgreSQL and Redis are mandatory for end-to-end tests");
        }

        List<String> unavailable = new ArrayList<>();
        inspectPostgres(properties, unavailable);
        inspectRedis(properties, unavailable);

        if (unavailable.isEmpty()) {
            return ConditionEvaluationResult.enabled("PostgreSQL and Redis sockets are available");
        }
        return ConditionEvaluationResult.disabled("E2E skipped: " + String.join("; ", unavailable));
    }

    private void inspectPostgres(PropertyResolver properties, List<String> unavailable) {
        try {
            Endpoint endpoint = postgresEndpoint(properties);
            if (!socketProbe.isAvailable(endpoint.host(), endpoint.port(), CONNECT_TIMEOUT_MS)) {
                unavailable.add("PostgreSQL " + endpoint.displayName() + " is unavailable");
            }
        } catch (IllegalArgumentException exception) {
            unavailable.add("PostgreSQL configuration is invalid");
        }
    }

    private void inspectRedis(PropertyResolver properties, List<String> unavailable) {
        try {
            Endpoint endpoint = redisEndpoint(properties);
            if (!socketProbe.isAvailable(endpoint.host(), endpoint.port(), CONNECT_TIMEOUT_MS)) {
                unavailable.add("Redis " + endpoint.displayName() + " is unavailable");
            }
        } catch (IllegalArgumentException exception) {
            unavailable.add("Redis configuration is invalid");
        }
    }

    static Endpoint postgresEndpoint(PropertyResolver properties) {
        String jdbcUrl = properties.getProperty("spring.datasource.url");
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

    static Endpoint redisEndpoint(PropertyResolver properties) {
        String host = properties.getProperty("spring.data.redis.host");
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Invalid Redis host");
        }
        String rawPort = properties.getProperty("spring.data.redis.port");
        int port;
        try {
            port = Integer.parseInt(rawPort);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid Redis port", exception);
        }
        return new Endpoint(host, validPort(port));
    }

    static PropertyResolver e2eProperties() {
        ConfigurableEnvironment environment = new StandardEnvironment();
        try {
            environment.getPropertySources().addLast(
                    new ResourcePropertySource("classpath:application-e2e.properties"));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load E2E test configuration", exception);
        }
        return environment;
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
