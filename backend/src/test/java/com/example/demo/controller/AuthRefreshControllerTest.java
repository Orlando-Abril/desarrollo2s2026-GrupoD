package com.example.demo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DirtiesContext
class AuthRefreshControllerTest {

    private static final String PASSWORD = "unPasswordSeguro123";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loginDejaUnaCookieHttpOnlyLimitadaAAuth() throws Exception {
        registrar("abril");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "abril", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/auth")));
    }

    @Test
    void refreshConLaCookieDelLoginDevuelveUnJwtQueSirveParaLaApi() throws Exception {
        registrar("abril");
        Cookie cookie = loguear("Abril");

        MvcResult result = mockMvc.perform(post("/auth/refresh").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("abril"))
                .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("abril"));
    }

    @Test
    void refreshRotaLaCookieYLaAnteriorDejaDeServir() throws Exception {
        registrar("abril");
        Cookie primera = loguear("abril");

        MvcResult result = mockMvc.perform(post("/auth/refresh").cookie(primera))
                .andExpect(status().isOk())
                .andReturn();
        Cookie segunda = result.getResponse().getCookie("refresh_token");
        assertThat(segunda).isNotNull();
        assertThat(segunda.getValue()).isNotEqualTo(primera.getValue());

        mockMvc.perform(post("/auth/refresh").cookie(primera))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("session_expired"));
        mockMvc.perform(post("/auth/refresh").cookie(segunda))
                .andExpect(status().isOk());
    }

    @Test
    void refreshSinCookieResponde401YBorraLaCookie() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("session_expired"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    @Test
    void refreshConUnaCookieInventadaResponde401() throws Exception {
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", "inventada")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevocaLaCookieYLaBorraDelNavegador() throws Exception {
        registrar("abril");
        Cookie cookie = loguear("abril");

        mockMvc.perform(post("/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        mockMvc.perform(post("/auth/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutSinCookieIgualResponde204() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent());
    }

    @Test
    void refreshConUnJwtVencidoEnElHeaderNoLoRechazaElFiltro() throws Exception {
        registrar("abril");
        Cookie cookie = loguear("abril");

        mockMvc.perform(post("/auth/refresh").cookie(cookie)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-vencido"))
                .andExpect(status().isOk());
    }

    private void registrar(String username) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "email", username + "@example.com",
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
    }

    private Cookie loguear(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private String json(Map<String, String> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
