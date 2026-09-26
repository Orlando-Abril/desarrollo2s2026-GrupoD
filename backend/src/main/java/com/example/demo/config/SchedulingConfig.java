package com.example.demo.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita {@code @Scheduled} sólo si {@code whoscored.sync.enabled=true} (por defecto {@code false}).
 * El TaskScheduler por defecto tiene un solo hilo: una ejecución no se superpone con la anterior (FR-027).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "whoscored.sync.enabled", havingValue = "true")
public class SchedulingConfig {
}
