package com.example.demo.controller;

import com.example.demo.filter.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

public final class HeaderUsageSamples {
    // CorrelationIdFilter.HEADER in a comment is inert.
    private static final String LITERAL = "CorrelationIdFilter.HEADER";

    @ApiResponse(headers = @Header(name = CorrelationIdFilter.HEADER))
    public void allowed() { }

    public static final class Forbidden {
        private final String value = CorrelationIdFilter.HEADER;
    }
}
