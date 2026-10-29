package com.example.demo.config;

import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Los cuatro endpoints de órdenes documentados con sus respuestas y los dos esquemas de seguridad (spec 027, FR-031). */
@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
class OrderOpenApiTest {

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "/orders/buy|post|201,400,401,403,404,422",
            "/orders/sell|post|201,400,401,403,404,422",
            "/orders/{id}|delete|200,400,401,403,404,409",
            "/orders/book|get|200,400,401,404"})
    void documentsEachOrderEndpoint(String path, String method, String codes) throws Exception {
        String operation = "$.paths['" + path + "']." + method;
        ResultActions docs = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".security[*].bearerAuth").exists())
                .andExpect(jsonPath(operation + ".security[*].apiKeyAuth").exists())
                .andExpect(jsonPath(operation + ".responses.length()").value(codes.split(",").length))
                .andExpect(jsonPath("$.components.schemas.OrderRequest").exists())
                .andExpect(jsonPath("$.components.schemas.OrderResponse").exists())
                .andExpect(jsonPath("$.components.schemas.OrderBookResponse").exists());
        for (String code : codes.split(",")) {
            docs.andExpect(jsonPath(operation + ".responses['" + code + "']").exists());
        }
    }
}
