package com.example.demo.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "football-data")
public record FootballDataProperties(
        @NotNull URI baseUrl,
        @NotBlank String token,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration cacheTtl) {

    @AssertTrue(message = "football-data durations must be positive")
    public boolean hasValidDurations() {
        return connectTimeout != null && !connectTimeout.isNegative() && !connectTimeout.isZero()
                && readTimeout != null && !readTimeout.isNegative() && !readTimeout.isZero()
                && cacheTtl != null && !cacheTtl.isNegative() && !cacheTtl.isZero();
    }
}
