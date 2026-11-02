package com.example.demo.service;

import com.example.demo.model.RefreshToken;
import com.example.demo.model.User;
import com.example.demo.repository.RefreshTokenRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.ApiKeyHasher;
import com.example.demo.security.JwtUtil;
import com.example.demo.service.RefreshTokenService.InvalidRefreshTokenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private static final Duration LIFETIME = Duration.ofDays(7);

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private JwtUtil jwtUtil;

    private final User abril = User.builder().username("abril").build();

    private RefreshTokenService service() {
        return new RefreshTokenService(refreshTokenRepository, userRepository, jwtUtil,
                LIFETIME.toMillis(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void issueGuardaSoloElHashConVencimientoYDevuelveElValorCrudo() {
        when(userRepository.findByUsername("abril")).thenReturn(Optional.of(abril));

        String raw = service().issue("Abril");

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(ApiKeyHasher.sha256Hex(raw)).isNotEqualTo(raw);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(LIFETIME));
        assertThat(saved.getValue().getOwner()).isSameAs(abril);
    }

    @Test
    void renewRevocaElTokenUsadoYEntregaOtroConUnJwtNuevo() {
        RefreshToken current = token(NOW.plusSeconds(60), null);
        when(refreshTokenRepository.findByTokenHash(ApiKeyHasher.sha256Hex("crudo"))).thenReturn(Optional.of(current));
        when(jwtUtil.generateToken("abril")).thenReturn("jwt-nuevo");

        RefreshTokenService.Renewal renewal = service().renew("crudo");

        assertThat(current.getRevokedAt()).isEqualTo(NOW);
        assertThat(renewal.username()).isEqualTo("abril");
        assertThat(renewal.accessToken()).isEqualTo("jwt-nuevo");
        assertThat(renewal.refreshToken()).isNotBlank().isNotEqualTo("crudo");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void renewRechazaUnTokenVencido() {
        RefreshToken vencido = token(NOW, null);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(vencido));

        assertThatThrownBy(() -> service().renew("crudo")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(jwtUtil, never()).generateToken(any());
    }

    @Test
    void renewRechazaUnTokenYaUsado() {
        RefreshToken usado = token(NOW.plusSeconds(60), NOW.minusSeconds(1));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(usado));

        assertThatThrownBy(() -> service().renew("crudo")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void renewSinCookieNoConsultaLaBase() {
        assertThatThrownBy(() -> service().renew(null)).isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> service().renew(" ")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void revokeMarcaElTokenYNoFallaSiNoExiste() {
        RefreshToken current = token(NOW.plusSeconds(60), null);
        when(refreshTokenRepository.findByTokenHash(ApiKeyHasher.sha256Hex("crudo"))).thenReturn(Optional.of(current));

        service().revoke("crudo");
        service().revoke(null);

        assertThat(current.getRevokedAt()).isEqualTo(NOW);
    }

    private RefreshToken token(Instant expiresAt, Instant revokedAt) {
        return RefreshToken.builder().owner(abril).tokenHash("hash").expiresAt(expiresAt).revokedAt(revokedAt).build();
    }
}
