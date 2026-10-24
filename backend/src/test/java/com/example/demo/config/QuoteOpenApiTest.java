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
    private static final String HISTORY = "$.paths['/players/{id}/quotes'].get";

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

    @Test
    void documentsQuoteHistoryContractAndBothSecurityAlternatives() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath(HISTORY + ".security[*].bearerAuth").exists())
                .andExpect(jsonPath(HISTORY + ".security[*].apiKeyAuth").exists())
                .andExpect(jsonPath(HISTORY + ".parameters.length()").value(5))
                .andExpect(jsonPath(HISTORY + ".parameters[?(@.name == 'id')].schema.minimum").value(1))
                .andExpect(jsonPath(HISTORY + ".parameters[?(@.name == 'strategy')]").exists())
                .andExpect(jsonPath(HISTORY + ".parameters[?(@.name == 'from')]").exists())
                .andExpect(jsonPath(HISTORY + ".parameters[?(@.name == 'to')]").exists())
                .andExpect(jsonPath(HISTORY + ".parameters[?(@.name == 'at')]").exists())
                .andExpect(jsonPath(HISTORY + ".responses['200'].content['application/json'].schema.oneOf.length()").value(2))
                .andExpect(jsonPath(HISTORY + ".responses['200'].headers['X-Correlation-ID']").exists())
                .andExpect(jsonPath(HISTORY + ".responses['400']").exists())
                .andExpect(jsonPath(HISTORY + ".responses['401']").exists())
                .andExpect(jsonPath(HISTORY + ".responses['404']").exists())
                .andExpect(jsonPath(HISTORY + ".responses['400'].content['application/json'].examples.invalidPlayerId").exists())
                .andExpect(jsonPath("$.components.schemas.PlayerQuoteResponse").exists())
                .andExpect(jsonPath("$.components.schemas.PlayerQuoteResponse.required.length()").value(6));
    }
}
