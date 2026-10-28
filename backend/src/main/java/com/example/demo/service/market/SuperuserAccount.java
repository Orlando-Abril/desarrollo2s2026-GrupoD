package com.example.demo.service.market;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuración e identidad del superusuario, dueño inicial de todos los tokens (spec 026, FR-001).
 * Se configura por entorno ({@code MARKET_SUPERUSER_*}). La contraseña por defecto vive en
 * {@code market.properties}, no en el código ni en {@code application.properties}.
 */
@Component
public class SuperuserAccount {

    /** Mensaje de la {@link IllegalStateException} de {@link #id()} cuando el superusuario no existe. */
    public static final String MISSING = "superuser_missing";

    private final UserRepository userRepository;
    private final String username;
    private final String email;
    private final String password;
    private final String defaultPassword;

    public SuperuserAccount(UserRepository userRepository,
                            @Value("${market.superuser.username:superuser}") String username,
                            @Value("${market.superuser.email:superuser@futbol-market.local}") String email,
                            @Value("${market.superuser.password}") String password,
                            @Value("${market.superuser.default-password}") String defaultPassword) {
        this.userRepository = userRepository;
        this.username = username;
        this.email = email;
        this.password = password;
        this.defaultPassword = defaultPassword;
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
        return defaultPassword.equals(password);
    }

    public Long id() {
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException(MISSING));
    }
}
