package com.example.demo.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import com.example.demo.support.UnitTestProfile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
class PlayerOpenApiTest {
    @Autowired MockMvc mvc;

    @Test
    void exposesPlayerContractAndAlternativeSecuritySchemes() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/players'].get.parameters").isArray())
                .andExpect(jsonPath("$.paths['/players'].get.responses['503']").doesNotExist())
                .andExpect(jsonPath("$.components.securitySchemes.apiKeyAuth.name").value("X-API-KEY"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.schemas.PlayerResponse").exists())
                .andExpect(jsonPath("$.paths['/players/sync'].post").exists())
                .andExpect(jsonPath("$.paths['/players'].get.security[*].apiKeyAuth").exists())
                .andExpect(jsonPath("$.paths['/players'].get.security[*].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/players/sync'].post.security[*].apiKeyAuth").exists())
                .andExpect(jsonPath("$.paths['/players/sync'].post.security[*].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/auth/register'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/auth/login'].post.security").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PlayerSyncResponse").exists());
    }

    @Test
    void exposesPlayerDetailContract() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/players/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/players/{id}'].get.parameters[?(@.name == 'id')].schema.format")
                        .value("int64"))
                .andExpect(jsonPath("$.paths['/players/{id}'].get.responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/PlayerDetailResponse"))
                .andExpect(jsonPath("$.paths['/players/{id}'].get.responses['200'].headers['X-Correlation-ID']")
                        .exists())
                .andExpect(jsonPath("$.paths['/players/{id}'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/players/{id}'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/players/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/players/{id}'].get.security[*].apiKeyAuth").exists())
                .andExpect(jsonPath("$.paths['/players/{id}'].get.security[*].bearerAuth").exists())
                .andExpect(jsonPath("$.components.schemas.PlayerDetailResponse.properties.stats.type").value("null"))
                .andExpect(jsonPath("$.components.schemas.PlayerDetailResponse.properties.quote.type").value("null"))
                .andExpect(jsonPath("$.components.schemas.PlayerStatsResponse").exists())
                .andExpect(jsonPath("$.components.schemas.CurrentQuoteResponse").exists());
    }
}
