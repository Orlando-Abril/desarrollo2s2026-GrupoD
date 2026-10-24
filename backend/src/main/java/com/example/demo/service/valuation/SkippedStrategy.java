package com.example.demo.service.valuation;

import java.util.Objects;

/** Estrategia del catálogo que una corrida no calculó, con el motivo. */
public record SkippedStrategy(String code, int version, String reason) {

    public static final String NO_ALGORITHM = "no_algorithm";

    public SkippedStrategy {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }
}
