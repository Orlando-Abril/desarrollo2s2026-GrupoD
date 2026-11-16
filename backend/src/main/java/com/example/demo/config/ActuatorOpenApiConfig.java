package com.example.demo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActuatorOpenApiConfig {
    private static final String JSON = "application/json";
    private static final String BEARER_AUTH = "bearerAuth";
    private static final String API_KEY_AUTH = "apiKeyAuth";

    /**
     * Al existir un {@link GroupedOpenApi}, Swagger UI lista sólo los grupos declarados: sin este grupo la API
     * principal deja de aparecer en el selector y la UI abre directamente en Actuator.
     */
    @Bean
    GroupedOpenApi mainApi() {
        return GroupedOpenApi.builder()
                .group("api")
                .displayName("API")
                .pathsToMatch("/**")
                .pathsToExclude("/actuator/**")
                .build();
    }

    @Bean
    GroupedOpenApi actuatorOpenApi() {
        return GroupedOpenApi.builder()
                .group("actuator")
                .pathsToMatch("/actuator/**")
                .addOpenApiCustomizer(openApi -> {
                    ensureSecuritySchemes(openApi.getComponents() == null
                            ? components(openApi) : openApi.getComponents());
                    Paths paths = new Paths();
                    for (String path : new String[] {"/actuator/health", "/actuator/health/liveness",
                            "/actuator/health/readiness"}) {
                        paths.addPathItem(path, new PathItem().get(healthOperation(path)));
                    }
                    paths.addPathItem("/actuator/info", new PathItem().get(infoOperation()));
                    paths.addPathItem("/actuator/metrics", new PathItem().get(metricsOperation(false)));
                    paths.addPathItem("/actuator/metrics/{requiredMetricName}",
                            new PathItem().get(metricsOperation(true)));
                    paths.addPathItem("/actuator/prometheus", new PathItem().get(prometheusOperation()));
                    openApi.setPaths(paths);
                })
                .build();
    }

    private Components components(io.swagger.v3.oas.models.OpenAPI openApi) {
        Components components = new Components();
        openApi.setComponents(components);
        return components;
    }

    private void ensureSecuritySchemes(Components components) {
        if (components.getSecuritySchemes() == null || !components.getSecuritySchemes().containsKey(BEARER_AUTH)) {
            components.addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"));
        }
        if (components.getSecuritySchemes() == null || !components.getSecuritySchemes().containsKey(API_KEY_AUTH)) {
            components.addSecuritySchemes(API_KEY_AUTH, new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-API-KEY"));
        }
    }

    private Operation healthOperation(String path) {
        return new Operation().summary(path.equals("/actuator/health") ? "Estado de salud" : "Estado de "
                + path.substring(path.lastIndexOf('/') + 1))
                .responses(new ApiResponses()
                        .addApiResponse("200", jsonResponse("Estado disponible", healthSchema()))
                        .addApiResponse("401", jsonResponse("Credencial presentada inválida", errorSchema()))
                        .addApiResponse("503", jsonResponse("Estado no disponible", healthSchema())));
    }

    private Operation infoOperation() {
        ObjectSchema build = new ObjectSchema();
        build.addProperty("name", new StringSchema());
        build.addProperty("version", new StringSchema());
        build.addProperty("time", new StringSchema().format("date-time"));
        ObjectSchema info = new ObjectSchema();
        info.addProperty("build", build);
        return new Operation().summary("Identidad del build")
                .responses(new ApiResponses().addApiResponse("200", jsonResponse("Información del build", info)));
    }

    private Operation metricsOperation(boolean named) {
        Operation operation = new Operation().summary(named ? "Mediciones de una métrica" : "Nombres de métricas")
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_AUTH));
        if (named) {
            operation.addParametersItem(new Parameter().in("path").name("requiredMetricName")
                    .required(true).schema(new StringSchema()));
        }
        ObjectSchema body = new ObjectSchema();
        if (named) {
            body.addProperty("name", new StringSchema());
            body.addProperty("measurements", new ArraySchema().items(new ObjectSchema()
                    .addProperty("statistic", new StringSchema())
                    .addProperty("value", new Schema<>().type("number"))));
            body.addProperty("availableTags", new ArraySchema().items(new ObjectSchema()));
        } else {
            body.addProperty("names", new ArraySchema().items(new StringSchema()));
        }
        return operation.responses(protectedResponses(jsonResponse("Métricas", body)));
    }

    private Operation prometheusOperation() {
        ApiResponse success = new ApiResponse().description("Series en formato Prometheus")
                .content(new Content().addMediaType("text/plain",
                        new MediaType().schema(new StringSchema())));
        return new Operation().summary("Exposición Prometheus")
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_AUTH))
                .responses(protectedResponses(success));
    }

    private ApiResponses protectedResponses(ApiResponse success) {
        return new ApiResponses()
                .addApiResponse("200", success)
                .addApiResponse("401", jsonResponse("No autenticado", errorSchema()))
                .addApiResponse("403", jsonResponse("Rol insuficiente", errorSchema()));
    }

    private ApiResponse jsonResponse(String description, Schema<?> schema) {
        return new ApiResponse().description(description)
                .content(new Content().addMediaType(JSON, new MediaType().schema(schema)));
    }

    private ObjectSchema healthSchema() {
        ObjectSchema health = new ObjectSchema();
        health.addProperty("status", new StringSchema());
        health.addProperty("components", new ObjectSchema()
                .addProperty("db", new ObjectSchema())
                .addProperty("redis", new ObjectSchema())
                .addProperty("diskSpace", new ObjectSchema()));
        return health;
    }

    private ObjectSchema errorSchema() {
        ObjectSchema error = new ObjectSchema();
        error.addProperty("error", new StringSchema());
        error.addProperty("message", new StringSchema());
        return error;
    }
}
