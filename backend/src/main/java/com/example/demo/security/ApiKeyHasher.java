package com.example.demo.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hash determinístico usado para poder buscar una {@code ApiKey} por igualdad exacta contra
 * {@code keyHash} (ver specs/001-seguridad-infraestructura/contracts/security-infrastructure.md
 * §2). Cualquier componente que genere o rote una ApiKey MUST usar exactamente este algoritmo.
 */
public final class ApiKeyHasher {

    private ApiKeyHasher() {
    }

    public static String sha256Hex(String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}
