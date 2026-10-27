package com.example.demo.service.market;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuración e identidad del superusuario, dueño inicial de todos los tokens (spec 026, FR-001).
 * Se configura por entorno ({@code MARKET_SUPERUSER_*}); los valores por defecto viven acá y no en
 * {@code application.properties}.
 */
@Component
public class SuperuserAccount {

    /** Mensaje de la {@link IllegalStateException} de {@link #id()} cuando el superusuario no existe. */
    public static final String MISSING = "superuser_missing";

    // Contraseña por defecto sólo para desarrollo y demo; en despliegue se configura con MARKET_SUPERUSER_PASSWORD.
    static final String DEFAULT_PASSWORD = "Superuser.2026";

    private final UserRepository userRepository;
    private final String username;
    private final String email;
    private final String password;

    public SuperuserAccount(UserRepository userRepository,
                            @Value("${market.superuser.username:superuser}") String username,
                            @Value("${market.superuser.email:superuser@futbol-market.local}") String email,
                            @Value("${market.superuser.password:" + DEFAULT_PASSWORD + "}") String password) {
        this.userRepository = userRepository;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    public String username() {
        return username;
    }

    public String email() {
        return email;
    }

    public String password() {
        return password;
    }

    public boolean usesDefaultPassword() {
        return DEFAULT_PASSWORD.equals(password);
    }

    public Long id() {
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException(MISSING));
    }
}
