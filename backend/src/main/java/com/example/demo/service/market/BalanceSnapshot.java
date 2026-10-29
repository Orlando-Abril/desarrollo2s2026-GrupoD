package com.example.demo.service.market;

import com.example.demo.model.User;

import java.math.BigDecimal;

/** Saldo de un usuario para la auditoría. Nunca incluye email, hash de la contraseña ni API key (FR-024 de la 026). */
public record BalanceSnapshot(Long userId, BigDecimal balance) {

    public static BalanceSnapshot of(User user) {
        return new BalanceSnapshot(user.getId(), user.getBalance());
    }
}
