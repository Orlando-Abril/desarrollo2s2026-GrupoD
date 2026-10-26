package com.example.demo.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita el recálculo programado de cotizaciones independientemente de la sincronización de WhoScored. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "valuation.schedule.enabled", havingValue = "true")
public class QuoteSchedulingConfig {
}
