package com.example.demo.security;

import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JwtOrApiKeyAuthenticationIntegrationTest {

    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final String SECRET = "test-only-secret-key-never-use-in-production-0123456789";
    private static final String OTHER_SECRET = "different-test-secret-key-with-at-least-32-bytes";
    private static final String PASSWORD = "password-segura";
    private static final String INVALID_TOKEN_BODY =
            "{\"error\":\"unauthorized\",\"message\":\"Token inválido o vencido\"}";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        apiKeyRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void validBearerAllowsPlayers() throws Exception {
        mockMvc.perform(get("/players")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtUtil.generateToken("guada"))))
                .andExpect(status().isOk());
    }

    @Test
    void registerLoginAndUseReturnedTokenAllowsPlayers() throws Exception {
        Credentials credentials = newCredentials();
        register(credentials, null).andExpect(status().isCreated());
        String token = login(credentials, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(token).get("token").asText();

        mockMvc.perform(get("/players").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void expiredBearerIsRejected() throws Exception {
        String token = new JwtUtil(SECRET, -1_000).generateToken("guada");

        assertInvalidBearer(token, null);
    }

    @Test
    void bearerWithInvalidSignatureIsRejected() throws Exception {
        String token = new JwtUtil(OTHER_SECRET, 3_600_000).generateToken("guada");

        assertInvalidBearer(token, null);
    }

    @Test
    void malformedBearerIsRejected() throws Exception {
        assertInvalidBearer("abc", null);
    }

    @Test
    void invalidBearerDoesNotFallBackToValidApiKey() throws Exception {
        Credentials credentials = newCredentials();
        String apiKey = registeredApiKey(credentials);

        assertInvalidBearer("abc", apiKey);
    }

    @Test
    void validApiKeyWithoutAuthorizationAllowsPlayers() throws Exception {
        String apiKey = registeredApiKey(newCredentials());

        mockMvc.perform(get("/players").header(API_KEY_HEADER, apiKey))
                .andExpect(status().isOk());
    }

    @Test
    void noCredentialKeepsCurrentApiKeyError() throws Exception {
        mockMvc.perform(get("/players"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("unauthorized"))
                .andExpect(jsonPath("$.message").value("Falta el header X-API-KEY"));
    }

    @Test
    void basicAuthorizationWithValidApiKeyAllowsPlayers() throws Exception {
        String apiKey = registeredApiKey(newCredentials());

        mockMvc.perform(get("/players")
                        .header(HttpHeaders.AUTHORIZATION, "Basic eHh4Onl5eQ==")
                        .header(API_KEY_HEADER, apiKey))
                .andExpect(status().isOk());
    }

    @Test
    void basicAuthorizationWithoutApiKeyKeepsCurrentApiKeyError() throws Exception {
        mockMvc.perform(get("/players")
                        .header(HttpHeaders.AUTHORIZATION, "Basic eHh4Onl5eQ=="))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Falta el header X-API-KEY"));
    }

    @Test
    void validBearerWinsEvenWhenApiKeyIsInvalid() throws Exception {
        mockMvc.perform(get("/players")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtUtil.generateToken("guada")))
                        .header(API_KEY_HEADER, "invalid-api-key"))
                .andExpect(status().isOk());
    }

    @Test
    void registerAndLoginRemainPublicWithoutCredentials() throws Exception {
        Credentials credentials = newCredentials();

        register(credentials, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKey").isNotEmpty());
        login(credentials, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void invalidBearerDoesNotInterceptPublicRegisterOrLogin() throws Exception {
        Credentials credentials = newCredentials();

        register(credentials, "Bearer abc").andExpect(status().isCreated());
        login(credentials, "Bearer abc").andExpect(status().isOk());
    }

    private void assertInvalidBearer(String token, String apiKey) throws Exception {
        var request = get("/players").header(HttpHeaders.AUTHORIZATION, bearer(token));
        if (apiKey != null) {
            request.header(API_KEY_HEADER, apiKey);
        }

        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(INVALID_TOKEN_BODY, true));
    }

    private String registeredApiKey(Credentials credentials) throws Exception {
        MvcResult result = register(credentials, null).andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("apiKey").asText();
    }

    private org.springframework.test.web.servlet.ResultActions register(
            Credentials credentials, String authorization) throws Exception {
        var request = post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterBody(
                        credentials.username(), credentials.email(), credentials.password())));
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(request);
    }

    private org.springframework.test.web.servlet.ResultActions login(
            Credentials credentials, String authorization) throws Exception {
        var request = post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginBody(
                        credentials.username(), credentials.password())));
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(request);
    }

    private Credentials newCredentials() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        return new Credentials("user" + suffix, "user" + suffix + "@example.com", PASSWORD);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Credentials(String username, String email, String password) {
    }

    private record RegisterBody(String username, String email, String password) {
    }

    private record LoginBody(String username, String password) {
    }
}
