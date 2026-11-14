package com.example.demo.config;

import com.example.demo.support.UnitTestProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
@TestPropertySource(properties = {
        "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
        "management.endpoint.health.probes.enabled=true",
        "springdoc.show-actuator=true"
})
class ActuatorOpenApiTest {
    @Autowired MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void swaggerOffersActuatorGroupAndFourFamilies() throws Exception {
        JsonNode config = read("/v3/api-docs/swagger-config");
        assertThat(config.path("urls").toString()).contains("actuator");

        JsonNode api = read("/v3/api-docs/actuator");
        Set<String> paths = iterableKeys(api.path("paths"));
        assertThat(paths).contains("/actuator/health", "/actuator/health/liveness",
                "/actuator/health/readiness", "/actuator/info", "/actuator/metrics",
                "/actuator/prometheus");
        assertThat(paths).allSatisfy(path -> assertThat(path).startsWith("/actuator/"));
    }

    @Test
    void protectedOperationsOfferBearerOrApiKeyIndependently() throws Exception {
        JsonNode api = read("/v3/api-docs/actuator");
        JsonNode schemes = api.path("components").path("securitySchemes");
        assertThat(schemes.path("bearerAuth").path("scheme").asText()).isEqualTo("bearer");
        assertThat(schemes.path("apiKeyAuth").path("name").asText()).isEqualTo("X-API-KEY");
        for (String path : new String[] {"/actuator/metrics", "/actuator/prometheus"}) {
            JsonNode security = api.path("paths").path(path).path("get").path("security");
            assertThat(security.isArray()).isTrue();
            assertThat(security.size()).isEqualTo(2);
            assertThat(security.get(0).size()).isEqualTo(1);
            assertThat(security.get(1).size()).isEqualTo(1);
            assertThat(security.get(0).has("bearerAuth") || security.get(1).has("bearerAuth")).isTrue();
            assertThat(security.get(0).has("apiKeyAuth") || security.get(1).has("apiKeyAuth")).isTrue();
        }
        for (String path : new String[] {"/actuator/health", "/actuator/info"}) {
            JsonNode security = api.path("paths").path(path).path("get").path("security");
            assertThat(security.isMissingNode() || security.isEmpty()).isTrue();
        }
    }

    @Test
    void operationsDocumentResponseCodesAndSchemas() throws Exception {
        JsonNode api = read("/v3/api-docs/actuator");
        for (String path : new String[] {"/actuator/health", "/actuator/health/liveness",
                "/actuator/health/readiness"}) {
            assertResponses(api, path, "200", "401", "503");
            assertJsonSchema(api, path, "200");
            assertJsonSchema(api, path, "401");
            assertJsonSchema(api, path, "503");
            assertThat(schema(api, path, "200").path("properties").has("status")).isTrue();
            assertThat(schema(api, path, "503").path("properties").has("status")).isTrue();
            assertErrorSchema(api, path, "401");
        }
        assertResponses(api, "/actuator/info", "200");
        assertJsonSchema(api, "/actuator/info", "200");
        JsonNode build = schema(api, "/actuator/info", "200").path("properties").path("build")
                .path("properties");
        assertThat(iterableKeys(build)).contains("name", "version", "time");
        assertResponses(api, "/actuator/metrics", "200", "401", "403");
        for (String code : new String[] {"200", "401", "403"}) {
            assertJsonSchema(api, "/actuator/metrics", code);
        }
        assertThat(schema(api, "/actuator/metrics", "200").path("properties").has("names")).isTrue();
        assertErrorSchema(api, "/actuator/metrics", "401");
        assertErrorSchema(api, "/actuator/metrics", "403");
        assertResponses(api, "/actuator/prometheus", "200", "401", "403");
        JsonNode prometheusContent = response(api, "/actuator/prometheus", "200").path("content");
        assertThat(prometheusContent.has("text/plain") || prometheusContent.has("text/plain;version=0.0.4"))
                .isTrue();
        assertThat(prometheusContent.path("text/plain").path("schema").path("type").asText())
                .isEqualTo("string");
        assertJsonSchema(api, "/actuator/prometheus", "401");
        assertJsonSchema(api, "/actuator/prometheus", "403");
        assertErrorSchema(api, "/actuator/prometheus", "401");
        assertErrorSchema(api, "/actuator/prometheus", "403");
    }

    private void assertResponses(JsonNode api, String path, String... codes) {
        JsonNode responses = api.path("paths").path(path).path("get").path("responses");
        for (String code : codes) assertThat(responses.has(code)).as(path + " " + code).isTrue();
    }

    private void assertJsonSchema(JsonNode api, String path, String code) {
        JsonNode schema = schema(api, path, code);
        assertThat(schema.isMissingNode() || schema.isEmpty()).as(path + " " + code + " JSON schema").isFalse();
    }

    private void assertErrorSchema(JsonNode api, String path, String code) {
        assertThat(iterableKeys(schema(api, path, code).path("properties")))
                .as(path + " " + code + " error fields").contains("error", "message");
    }

    private JsonNode schema(JsonNode api, String path, String code) {
        return response(api, path, code).path("content").path("application/json").path("schema");
    }

    private JsonNode response(JsonNode api, String path, String code) {
        return api.path("paths").path(path).path("get").path("responses").path(code);
    }

    private Set<String> iterableKeys(JsonNode node) {
        Set<String> result = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private JsonNode read(String path) throws Exception {
        String body = mvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }
}
