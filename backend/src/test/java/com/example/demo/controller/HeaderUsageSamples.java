package com.example.demo.controller;

import com.example.demo.filter.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

public final class HeaderUsageSamples {
    // CorrelationIdFilter.HEADER in a comment is inert.
    static final String LITERAL = "CorrelationIdFilter.HEADER";

    @ApiResponse(headers = @Header(name = CorrelationIdFilter.HEADER))
    public void allowed() {
        // Intentionally empty: this fixture tests only the annotation.
    }

    public static final class Forbidden {
        final String value = CorrelationIdFilter.HEADER;
    }
}
