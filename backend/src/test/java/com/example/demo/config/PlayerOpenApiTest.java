package com.example.demo.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlayerOpenApiTest {
    @Autowired MockMvc mvc;

    @Test
    void exposesPlayerContractAndApiKeySecurity() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/players'].get.parameters").isArray())
                .andExpect(jsonPath("$.paths['/players'].get.responses['503']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.apiKeyAuth.name").value("X-API-KEY"))
                .andExpect(jsonPath("$.components.schemas.PlayerResponse").exists());
    }
}
