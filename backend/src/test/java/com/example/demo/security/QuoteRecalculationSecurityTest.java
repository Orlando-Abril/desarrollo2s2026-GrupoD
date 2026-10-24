package com.example.demo.security;

import com.example.demo.model.Role;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.valuation.QuoteCalculationService;
import com.example.demo.service.valuation.QuoteRunResult;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sólo ADMIN (JWT o API key de un ADMIN o sin dueño) puede recalcular; el resto de la API no cambia. */
@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
@Transactional
class QuoteRecalculationSecurityTest {

    private static final String PATH = "/quotes/recalculate";
    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final String FORBIDDEN_BODY =
            "{\"error\":\"forbidden\",\"message\":\"No tenés permisos para esta operación\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @MockitoBean
    private QuoteCalculationService service;

    private String userKey;
    private String adminKey;

    @BeforeEach
    void setUp() {
        userKey = AuthFixtures.apiKey(apiKeyRepository, AuthFixtures.user(userRepository, "guada", Role.USER));
        adminKey = AuthFixtures.apiKey(apiKeyRepository, AuthFixtures.user(userRepository, "abril", Role.ADMIN));
        when(service.recalculate(any())).thenReturn(new QuoteRunResult(UUID.randomUUID(), Instant.now(), null,
                List.of(), List.of(), 0, 0, "OFFICIAL", true));
    }

    @Test
    void withoutCredentialsIs401() throws Exception {
        mockMvc.perform(post(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(service);
    }

    @Test
    void invalidTokenIs401() throws Exception {
        mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void invalidApiKeyIs401() throws Exception {
        mockMvc.perform(post(PATH).header(API_KEY_HEADER, "does-not-exist"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void userJwtIs403() throws Exception {
        mockMvc.perform(bearer(post(PATH), "guada"))
                .andExpect(status().isForbidden())
                .andExpect(content().json(FORBIDDEN_BODY, true));
        verifyNoInteractions(service);
    }

    @Test
    void jwtOfAnUnknownUserIs403() throws Exception {
        mockMvc.perform(bearer(post(PATH), "fantasma")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void adminJwtIs200() throws Exception {
        mockMvc.perform(bearer(post(PATH), "abril")).andExpect(status().isOk());
    }

    @Test
    void adminApiKeyIs200() throws Exception {
        mockMvc.perform(post(PATH).header(API_KEY_HEADER, adminKey)).andExpect(status().isOk());
    }

    @Test
    void serviceApiKeyWithoutOwnerIs200() throws Exception {
        String serviceKey = AuthFixtures.apiKey(apiKeyRepository, null);

        mockMvc.perform(post(PATH).header(API_KEY_HEADER, serviceKey)).andExpect(status().isOk());
    }

    @Test
    void userApiKeyIs403() throws Exception {
        mockMvc.perform(post(PATH).header(API_KEY_HEADER, userKey))
                .andExpect(status().isForbidden())
                .andExpect(content().json(FORBIDDEN_BODY, true));
        verifyNoInteractions(service);
    }

    @Test
    void otherEndpointsKeepTheirBehaviourForUsers() throws Exception {
        mockMvc.perform(bearer(get("/players"), "guada")).andExpect(status().isOk());
        mockMvc.perform(bearer(get("/players"), "fantasma")).andExpect(status().isOk());
        mockMvc.perform(get("/players").header(API_KEY_HEADER, userKey)).andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String username) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUtil.generateToken(username));
    }
}
