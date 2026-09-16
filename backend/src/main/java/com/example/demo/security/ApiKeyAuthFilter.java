package com.example.demo.security;

import com.example.demo.config.SecurityConfig;
import com.example.demo.model.ApiKey;
import com.example.demo.repository.ApiKeyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Autoriza requests de servicio validando el header {@code X-API-KEY} contra el hash
 * persistido en {@code api_keys}. Contrato completo en
 * specs/001-seguridad-infraestructura/contracts/security-infrastructure.md §2.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final ApiKeyRepository apiKeyRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApiKeyAuthFilter(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (String publicRoute : SecurityConfig.PUBLIC_ROUTES) {
            if (PATH_MATCHER.match(publicRoute, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String rawKey = request.getHeader(API_KEY_HEADER);

        if (rawKey == null || rawKey.isBlank()) {
            respondUnauthorized(response, "Falta el header X-API-KEY");
            return;
        }

        Optional<ApiKey> apiKey = apiKeyRepository.findByKeyHash(sha256Hex(rawKey));

        if (apiKey.isEmpty() || !apiKey.get().isActive()) {
            respondUnauthorized(response, "API key inválida o inactiva");
            return;
        }

        String principal = apiKey.get().getOwner() != null
                ? apiKey.get().getOwner().getUsername()
                : apiKey.get().getKeyPrefix();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        filterChain.doFilter(request, response);
    }

    private void respondUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(new ErrorBody("unauthorized", message)));
    }

    private static String sha256Hex(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }

    private record ErrorBody(String error, String message) {
    }
}
