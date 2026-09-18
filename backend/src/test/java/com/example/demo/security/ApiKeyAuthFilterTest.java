package com.example.demo.security;

import com.example.demo.model.ApiKey;
import com.example.demo.repository.ApiKeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DirtiesContext
class ApiKeyAuthFilterTest {

    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final String PROTECTED_PATH = "/test/protected";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Test
    void sinHeaderXApiKeyDeberiaResponder401() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conHeaderQueNoMatcheaNingunHashDeberiaResponder401() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH).header(API_KEY_HEADER, "no-existe-en-ningun-lado"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conApiKeyInactivaDeberiaResponder401() throws Exception {
        String rawKey = "raw-key-inactiva-123";
        persistirApiKey(rawKey, "inac1234", false);

        mockMvc.perform(get(PROTECTED_PATH).header(API_KEY_HEADER, rawKey))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conApiKeyActivaDeberiaContinuarLaRequest() throws Exception {
        String rawKey = "raw-key-activa-456";
        persistirApiKey(rawKey, "acti5678", true);

        mockMvc.perform(get(PROTECTED_PATH).header(API_KEY_HEADER, rawKey))
                .andExpect(status().isOk());
    }

    private void persistirApiKey(String rawKey, String prefix, boolean active) throws NoSuchAlgorithmException {
        ApiKey apiKey = ApiKey.builder()
                .keyHash(sha256Hex(rawKey))
                .keyPrefix(prefix)
                .active(active)
                .build();
        apiKeyRepository.saveAndFlush(apiKey);
    }

    private static String sha256Hex(String rawKey) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hashBytes);
    }
}
