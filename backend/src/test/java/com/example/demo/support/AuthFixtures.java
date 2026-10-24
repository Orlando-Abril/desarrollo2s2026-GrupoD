package com.example.demo.support;

import com.example.demo.model.ApiKey;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.ApiKeyHasher;

import java.util.UUID;

/** Usuarios y API keys persistidos para probar autorización por rol (feature 016). */
public final class AuthFixtures {

    private AuthFixtures() {
    }

    public static User user(UserRepository users, String username, Role role) {
        return users.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .passwordHash("not-used")
                .role(role)
                .build());
    }

    /** Crea una API key activa para {@code owner} (null = key de servicio sin dueño) y devuelve el valor crudo. */
    public static String apiKey(ApiKeyRepository apiKeys, User owner) {
        String rawKey = "test-key-" + UUID.randomUUID();
        apiKeys.save(ApiKey.builder()
                .keyHash(ApiKeyHasher.sha256Hex(rawKey))
                .keyPrefix(rawKey.substring(0, 8))
                .owner(owner)
                .active(true)
                .build());
        return rawKey;
    }
}
