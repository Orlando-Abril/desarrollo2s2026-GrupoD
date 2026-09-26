package com.example.demo.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JwtAuthFilterTest {

    private static final String SECRET = "test-only-secret-key-never-use-in-production-0123456789";
    private static final String OTHER_SECRET = "different-test-secret-key-with-at-least-32-bytes";
    private static final String INVALID_TOKEN_BODY =
            "{\"error\":\"unauthorized\",\"message\":\"Token inválido o vencido\"}";

    private JwtUtil jwtUtil;
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtUtil = new JwtUtil(SECRET, 3_600_000);
        filter = new JwtAuthFilter(jwtUtil);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validBearerAuthenticatesWithTokenSubjectAndContinues() throws Exception {
        String token = jwtUtil.generateToken("guada");
        MockHttpServletRequest request = protectedRequest("Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo("guada");
        verify(chain).doFilter(request, response);
    }

    @Test
    void missingAuthorizationContinuesWithoutAuthenticating() throws Exception {
        MockHttpServletRequest request = protectedRequest(null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void basicAuthorizationContinuesWithoutAuthenticating() throws Exception {
        MockHttpServletRequest request = protectedRequest("Basic eHh4Onl5eQ==");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void expiredBearerIsRejected() throws Exception {
        String token = new JwtUtil(SECRET, -1_000).generateToken("guada");

        assertRejected(token);
    }

    @Test
    void bearerSignedWithDifferentSecretIsRejected() throws Exception {
        String token = new JwtUtil(OTHER_SECRET, 3_600_000).generateToken("guada");

        assertRejected(token);
    }

    @Test
    void malformedBearerIsRejected() throws Exception {
        assertRejected("abc");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/auth/register",
            "/auth/login",
            "/swagger-ui/index.html",
            "/v3/api-docs/swagger-config",
            "/actuator/health"
    })
    void publicRoutesSkipJwtValidation(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    private void assertRejected(String token) throws Exception {
        MockHttpServletRequest request = protectedRequest("Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(MediaType.parseMediaType(response.getContentType())
                .isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(response.getContentAsString()).isEqualTo(INVALID_TOKEN_BODY);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, never()).doFilter(request, response);
    }

    private MockHttpServletRequest protectedRequest(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/players");
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request;
    }
}
