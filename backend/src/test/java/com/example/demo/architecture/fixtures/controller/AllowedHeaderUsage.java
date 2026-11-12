package com.example.demo.controller;

import com.example.demo.filter.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

public final class AllowedHeaderUsage {
    // CorrelationIdFilter.HEADER is allowed in this comment.
    private static final String TEXT = "CorrelationIdFilter.HEADER";

    @ApiResponse(headers = @Header(name = CorrelationIdFilter.HEADER))
    public void documented() { }
}
