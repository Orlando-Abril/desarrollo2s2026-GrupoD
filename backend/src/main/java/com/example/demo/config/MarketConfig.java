package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Carga {@code market.properties} sin tocar {@code application.properties} (archivo de Entrega 1).
 * En los tests, el {@code market.properties} de {@code src/test/resources} pisa al de main.
 */
@Configuration
@PropertySource("classpath:market.properties")
public class MarketConfig {
}
