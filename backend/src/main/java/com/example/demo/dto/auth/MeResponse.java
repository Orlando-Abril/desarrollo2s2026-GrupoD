package com.example.demo.dto.auth;

import com.example.demo.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Schema(name = "MeResponse", description = "Cuenta representada por la credencial actual")
public record MeResponse(
        @Schema(example = "7", requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(example = "abril", requiredMode = Schema.RequiredMode.REQUIRED) String username,
        @Schema(example = "abril@example.com", format = "email", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
        @Schema(allowableValues = {"USER", "ADMIN"}, requiredMode = Schema.RequiredMode.REQUIRED) Role role,
        @Schema(example = "1000.0000", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal balance) {

    public MeResponse {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(role, "role");
        balance = Objects.requireNonNull(balance, "balance").setScale(4, RoundingMode.HALF_EVEN);
    }
}
