package com.example.demo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.security.JwtUtil;
import com.example.demo.security.ApiKeyHasher;
import com.example.demo.model.ApiKey;
import org.springframework.http.HttpHeaders;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DirtiesContext
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired private UserRepository userRepository;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private ApiKeyRepository apiKeyRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String registerPayload(String username, String email, String password) throws Exception {
        return objectMapper.writeValueAsString(new RegisterPayload(username, email, password));
    }

    private void register(String username, String email, String password) throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(username, email, password)));
    }

    @Test
    void registerConDatosValidosDeberiaResponder201ConApiKeyYSaldoInicial() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload("abril", "abril@example.com", "unPasswordSeguro123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("abril"))
                .andExpect(jsonPath("$.email").value("abril@example.com"))
                .andExpect(jsonPath("$.balance").value(1000.00))
                .andExpect(jsonPath("$.apiKey", not(emptyOrNullString())));
    }

    @Test
    void registerConUsernameDuplicadoDeberiaResponder409() throws Exception {
        register("abril", "abril@example.com", "unPasswordSeguro123");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload("abril", "otro@example.com", "otroPasswordSeguro1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("duplicate_user"));
    }

    @Test
    void registerConEmailDuplicadoDeberiaResponder409() throws Exception {
        register("abril", "abril@example.com", "unPasswordSeguro123");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload("otrouser", "abril@example.com", "otroPasswordSeguro1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("duplicate_user"));
    }

    @Test
    void registerConMismoUsernameEnDistintoCasingDeberiaResponder409() throws Exception {
        register("abril", "abril@example.com", "unPasswordSeguro123");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload("Abril", "otro@example.com", "otroPasswordSeguro1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("duplicate_user"));
    }

    @Test
    void registerConPasswordCortoDeberiaResponder400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload("nuevo", "nuevo@example.com", "corto")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
    }

    @Test
    void registerConEmailInvalidoDeberiaResponder400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload("nuevo", "no-es-un-email", "unPasswordSeguro123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
    }

    private String loginPayload(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(new LoginPayload(username, password));
    }

    @Test
    void loginConCredencialesCorrectasDeberiaResponder200ConUnJwt() throws Exception {
        register("abril", "abril@example.com", "unPasswordSeguro123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("abril", "unPasswordSeguro123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void loginConUsernameEnDistintoCasingDeberiaEncontrarLaCuentaYResponder200() throws Exception {
        register("abril", "abril@example.com", "unPasswordSeguro123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("Abril", "unPasswordSeguro123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())));
    }

    @Test
    void loginConPasswordIncorrectoDeberiaResponder401() throws Exception {
        register("abril", "abril@example.com", "unPasswordSeguro123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("abril", "password-incorrecto")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    void loginConUsernameInexistenteDeberiaResponder401ConElMismoShape() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("noexiste", "cualquierPassword123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    void loginConCamposVaciosDeberiaResponder400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
    }

    @Test
    void meReturnsTheAuthenticatedAccount() throws Exception {
        User user = userRepository.save(User.builder().username("current-user").email("current@example.com")
                .passwordHash("hash").role(Role.ADMIN).build());
        String token = jwtUtil.generateToken(user.getUsername());

        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.username").value("current-user"))
                .andExpect(jsonPath("$.email").value("current@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.balance").value(1000.0000));
    }

    @Test
    void meWithoutCredentialsReturns401() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meAcceptsAnApiKeyOwnedByAUser() throws Exception {
        String registration = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload("api-owner", "api-owner@example.com", "unPasswordSeguro123")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String rawApiKey = objectMapper.readTree(registration).get("apiKey").asText();

        mockMvc.perform(get("/auth/me").header("X-API-KEY", rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("api-owner"));
    }

    @Test
    void meRejectsAServiceApiKeyWithoutOwner() throws Exception {
        String rawApiKey = "sk_service_portfolio_test";
        apiKeyRepository.save(ApiKey.builder().keyHash(ApiKeyHasher.sha256Hex(rawApiKey))
                .keyPrefix("svc_portfolio").build());

        mockMvc.perform(get("/auth/me").header("X-API-KEY", rawApiKey))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("authenticated_user_not_found"));
    }

    private record RegisterPayload(String username, String email, String password) {
    }

    private record LoginPayload(String username, String password) {
    }
}
