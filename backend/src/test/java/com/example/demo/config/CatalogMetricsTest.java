package com.example.demo.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogMetricsTest {
    @Test
    void registersExternalAndSynchronizationTimers() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObservabilityConfig config = new ObservabilityConfig();
        config.footballDataLatency(registry).record(() -> { });
        config.catalogSynchronizationDuration(registry).record(() -> { });
        assertThat(registry.get("football.data.request").timer().count()).isOne();
        assertThat(registry.get("catalog.synchronization").timer().count()).isOne();
    }
}
