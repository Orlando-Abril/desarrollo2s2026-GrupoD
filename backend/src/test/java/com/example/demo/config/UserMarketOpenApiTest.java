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
class UserMarketOpenApiTest {

    private static final String PORTFOLIO = "$.paths['/users/{id}/portfolio'].get";
    private static final String TRANSACTIONS = "$.paths['/users/{id}/transactions'].get";
    private static final String ME = "$.paths['/auth/me'].get";

    @Autowired MockMvc mvc;

    @Test
    void documentsPortfolioContractSecurityAndErrors() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath(PORTFOLIO + ".security[*].bearerAuth").exists())
                .andExpect(jsonPath(PORTFOLIO + ".security[*].apiKeyAuth").exists())
                .andExpect(jsonPath(PORTFOLIO + ".parameters[?(@.name == 'id')].schema.minimum").value(1))
                .andExpect(jsonPath(PORTFOLIO + ".responses['200'].headers['X-Correlation-ID']").exists())
                .andExpect(jsonPath(PORTFOLIO + ".responses['200']").exists())
                .andExpect(jsonPath(PORTFOLIO + ".responses['400']").exists())
                .andExpect(jsonPath(PORTFOLIO + ".responses['401']").exists())
                .andExpect(jsonPath(PORTFOLIO + ".responses['403']").exists())
                .andExpect(jsonPath(PORTFOLIO + ".responses['404']").exists())
                .andExpect(jsonPath(PORTFOLIO + ".responses['500']").exists())
                .andExpect(jsonPath("$.components.schemas.PortfolioResponse.required").isArray())
                .andExpect(jsonPath("$.components.schemas.PortfolioPositionResponse.required").isArray());
    }

    @Test
    void documentsTransactionPaginationAndEveryItemAsATrade() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath(TRANSACTIONS + ".description").value(org.hamcrest.Matchers.containsString("calce")))
                .andExpect(jsonPath(TRANSACTIONS + ".security[*].bearerAuth").exists())
                .andExpect(jsonPath(TRANSACTIONS + ".security[*].apiKeyAuth").exists())
                .andExpect(jsonPath(TRANSACTIONS + ".parameters[?(@.name == 'page')].schema.default").value(0))
                .andExpect(jsonPath(TRANSACTIONS + ".parameters[?(@.name == 'size')].schema.default").value(20))
                .andExpect(jsonPath(TRANSACTIONS + ".parameters[?(@.name == 'size')].schema.maximum").value(100))
                .andExpect(jsonPath(TRANSACTIONS + ".responses['200'].headers['X-Correlation-ID']").exists())
                .andExpect(jsonPath(TRANSACTIONS + ".responses['400']").exists())
                .andExpect(jsonPath(TRANSACTIONS + ".responses['401']").exists())
                .andExpect(jsonPath(TRANSACTIONS + ".responses['403']").exists())
                .andExpect(jsonPath(TRANSACTIONS + ".responses['404']").exists())
                .andExpect(jsonPath("$.components.schemas.TransactionPageResponse.required").isArray())
                .andExpect(jsonPath("$.components.schemas.TransactionResponse.required").isArray());
    }

    @Test
    void documentsMeWithBothCredentialSchemes() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath(ME + ".security[*].bearerAuth").exists())
                .andExpect(jsonPath(ME + ".security[*].apiKeyAuth").exists())
                .andExpect(jsonPath(ME + ".responses['200'].headers['X-Correlation-ID']").exists())
                .andExpect(jsonPath(ME + ".responses['200']").exists())
                .andExpect(jsonPath(ME + ".responses['401']").exists())
                .andExpect(jsonPath("$.components.schemas.MeResponse.required.length()").value(5));
    }
}
