package com.example.demo.config;

import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Operaciones del comparador de estrategias documentadas en OpenAPI (spec 031, FR-027). */
@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
class StrategyComparisonOpenApiTest {

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "/quotes/opportunities|get|200,400,401",
            "/quotes/latest|get|200,400,401",
            "/strategies/active|get|200,401",
            "/strategies/active|put|200,400,401,403"})
    void documentsEachOperationWithSecurityAndResponses(String path, String method, String codes) throws Exception {
        String operation = "$.paths['" + path + "']." + method;
        ResultActions docs = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".security[*].bearerAuth").exists())
                .andExpect(jsonPath(operation + ".security[*].apiKeyAuth").exists())
                .andExpect(jsonPath(operation + ".responses.length()").value(codes.split(",").length));
        for (String code : codes.split(",")) {
            docs.andExpect(jsonPath(operation + ".responses['" + code + "']").exists());
        }
    }

    @Test
    void documentsParametersLimitsAndSchemas() throws Exception {
        String opportunities = "$.paths['/quotes/opportunities'].get.parameters";
        String latest = "$.paths['/quotes/latest'].get.parameters";
        String portfolio = "$.paths['/users/{id}/portfolio'].get";
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath(opportunities + "[*].name", hasItem("league")))
                .andExpect(jsonPath(opportunities + "[*].name", hasItem("position")))
                .andExpect(jsonPath(opportunities + "[?(@.name == 'limit')].schema.minimum", hasItem(1)))
                .andExpect(jsonPath(opportunities + "[?(@.name == 'limit')].schema.maximum", hasItem(50)))
                .andExpect(jsonPath(opportunities + "[?(@.name == 'limit')].schema.default", hasItem(10)))
                .andExpect(jsonPath(latest + "[*].name", hasItem("playerIds")))
                .andExpect(jsonPath(latest + "[*].name", hasItem("strategy")))
                .andExpect(jsonPath(portfolio + ".parameters[*].name", hasItem("strategy")))
                .andExpect(jsonPath(portfolio + ".responses['400']").exists())
                .andExpect(jsonPath("$.paths['/strategies/active'].put.requestBody").exists())
                .andExpect(jsonPath("$.components.schemas.StrategyRef").exists())
                .andExpect(jsonPath("$.components.schemas.OpportunitiesResponse").exists())
                .andExpect(jsonPath("$.components.schemas.OpportunityItem").exists())
                .andExpect(jsonPath("$.components.schemas.LatestQuotesResponse").exists())
                .andExpect(jsonPath("$.components.schemas.ActiveStrategyResponse").exists())
                .andExpect(jsonPath("$.components.schemas.ChangeActiveStrategyRequest").exists())
                .andExpect(jsonPath("$.components.schemas.PortfolioStrategyValuation").exists())
                .andExpect(jsonPath("$.components.schemas.PositionStrategyValuation").exists());
    }
}
