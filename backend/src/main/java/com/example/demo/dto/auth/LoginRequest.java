package com.example.demo.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "username no puede estar vacío") String username,
        @NotBlank(message = "password no puede estar vacío") String password
) {
}
