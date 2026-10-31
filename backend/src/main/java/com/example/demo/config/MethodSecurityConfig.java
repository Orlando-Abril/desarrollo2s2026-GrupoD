package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Habilita {@code @PreAuthorize} (spec 031) sin modificar {@link SecurityConfig}, archivo protegido de Entrega 1.
 * El 403 que produce lo responde {@code GlobalExceptionHandler} con el mismo cuerpo que el resto de la API.
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
}
