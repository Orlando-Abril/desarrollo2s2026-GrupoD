package com.example.demo.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "username no puede estar vacío") String username,

        @NotBlank(message = "email no puede estar vacío")
        @Email(message = "email debe tener un formato válido") String email,

        @NotBlank(message = "password no puede estar vacío")
        @Size(min = 8, message = "password debe tener al menos 8 caracteres") String password
) {
}
