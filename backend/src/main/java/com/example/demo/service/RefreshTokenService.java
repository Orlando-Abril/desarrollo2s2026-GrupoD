package com.example.demo.service;

import com.example.demo.model.RefreshToken;
import com.example.demo.model.User;
import com.example.demo.repository.RefreshTokenRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.ApiKeyHasher;
import com.example.demo.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Emite, rota y revoca los tokens de renovación de sesión. El valor crudo solo existe en la
 * cookie del navegador; en la base queda su SHA-256.
 */
@Service
public class RefreshTokenService {

    private static final int TOKEN_RANDOM_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final Duration lifetime;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               JwtUtil jwtUtil,
                               @Value("${security.refresh.expiration-ms}") long lifetimeMs) {
        this(refreshTokenRepository, userRepository, jwtUtil, lifetimeMs, Clock.systemUTC());
    }

    RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                        UserRepository userRepository,
                        JwtUtil jwtUtil,
                        long lifetimeMs,
                        Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.lifetime = Duration.ofMillis(lifetimeMs);
        this.clock = clock;
    }

    public Duration lifetime() {
        return lifetime;
    }

    /** Crea un token de renovación para un usuario que acaba de loguearse y devuelve el valor crudo. */
    @Transactional
    public String issue(String username) {
        User user = userRepository.findByUsername(username.toLowerCase())
                .orElseThrow(InvalidRefreshTokenException::new);
        return issueFor(user, clock.instant());
    }

    /**
     * Canjea un token de renovación válido por un JWT nuevo. El token usado queda revocado y se
     * entrega otro en su lugar, así una cookie robada deja de servir en cuanto la dueña la usa.
     */
    @Transactional
    public Renewal renew(String rawToken) {
        Instant now = clock.instant();
        RefreshToken current = find(rawToken)
                .filter(token -> token.isUsableAt(now))
                .orElseThrow(InvalidRefreshTokenException::new);
        current.setRevokedAt(now);
        User owner = current.getOwner();
        String nextRawToken = issueFor(owner, now);
        return new Renewal(owner.getUsername(), jwtUtil.generateToken(owner.getUsername()), nextRawToken);
    }

    /** Cierra la sesión: el token deja de poder usarse aunque la cookie siga en algún lado. */
    @Transactional
    public void revoke(String rawToken) {
        Instant now = clock.instant();
        find(rawToken)
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.setRevokedAt(now));
    }

    private Optional<RefreshToken> find(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByTokenHash(ApiKeyHasher.sha256Hex(rawToken));
    }

    private String issueFor(User user, Instant now) {
        byte[] randomBytes = new byte[TOKEN_RANDOM_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(ApiKeyHasher.sha256Hex(rawToken))
                .owner(user)
                .expiresAt(now.plus(lifetime))
                .createdAt(now)
                .build());
        return rawToken;
    }

    public record Renewal(String username, String accessToken, String refreshToken) {
    }

    /** El token no existe, venció o ya se usó. */
    public static class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException() {
            super("La sesión venció. Volvé a ingresar.");
        }
    }
}
