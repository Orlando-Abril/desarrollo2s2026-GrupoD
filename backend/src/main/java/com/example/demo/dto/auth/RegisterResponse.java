package com.example.demo.dto.auth;

import java.math.BigDecimal;

public record RegisterResponse(
        Long id,
        String username,
        String email,
        BigDecimal balance,
        String apiKey
) {
}
