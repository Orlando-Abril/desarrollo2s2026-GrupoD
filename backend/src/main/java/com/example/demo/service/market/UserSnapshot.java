package com.example.demo.service.market;

import com.example.demo.model.Role;
import com.example.demo.model.User;

import java.math.BigDecimal;

/** Estado de un usuario para la auditoría. Nunca incluye el hash de la contraseña ni la API key (FR-024). */
public record UserSnapshot(Long id, String username, String email, Role role, BigDecimal balance) {

    public static UserSnapshot of(User user) {
        return new UserSnapshot(user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.getBalance());
    }
}
