package com.example.demo.config;

import com.example.demo.support.UnitTestProfile;
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
@UnitTestProfile
class QuoteOpenApiTest {

    private static final String OPERATION = "$.paths['/quotes/recalculate'].post";

    @Autowired
    private MockMvc mvc;

    @Test
    void documentsRecalculationWithBothSecuritySchemesAndAllResponses() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath(OPERATION + ".security[*].bearerAuth").exists())
                .andExpect(jsonPath(OPERATION + ".security[*].apiKeyAuth").exists())
                .andExpect(jsonPath(OPERATION + ".requestBody.content").exists())
                .andExpect(jsonPath(OPERATION + ".requestBody.required").doesNotExist())
                .andExpect(jsonPath(OPERATION + ".responses['200']").exists())
                .andExpect(jsonPath(OPERATION + ".responses['400']").exists())
                .andExpect(jsonPath(OPERATION + ".responses['401']").exists())
                .andExpect(jsonPath(OPERATION + ".responses['403']").exists())
                .andExpect(jsonPath(OPERATION + ".responses['409']").exists())
                .andExpect(jsonPath(OPERATION + ".responses['500']").exists())
                .andExpect(jsonPath("$.components.schemas.QuoteRunResponse").exists())
                .andExpect(jsonPath("$.components.schemas.RecalculateRequest").exists());
    }
}
