package com.example.demo.dto.auth;

/** Respuesta de POST /auth/refresh: un JWT nuevo y el usuario al que pertenece la sesión. */
public record SessionResponse(String token, String tokenType, String username) {

    public SessionResponse(String token, String username) {
        this(token, "Bearer", username);
    }
}
