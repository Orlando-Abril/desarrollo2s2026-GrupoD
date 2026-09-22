package com.example.demo.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    @Bean
    Timer footballDataLatency(MeterRegistry registry) {
        return Timer.builder("football.data.request")
                .description("Football-Data request latency")
                .publishPercentileHistogram()
                .register(registry);
    }

    @Bean
    Timer catalogSynchronizationDuration(MeterRegistry registry) {
        return Timer.builder("catalog.synchronization")
                .description("Catalog synchronization duration")
                .publishPercentileHistogram()
                .register(registry);
    }
}
