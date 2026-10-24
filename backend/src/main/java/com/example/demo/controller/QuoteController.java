package com.example.demo.controller;

import com.example.demo.dto.quote.QuoteRunResponse;
import com.example.demo.dto.quote.RecalculateRequest;
import com.example.demo.exception.GlobalExceptionHandler.ErrorResponse;
import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.service.valuation.QuoteCalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/quotes")
@Tag(name = "Quotes", description = "Cotizaciones de jugadores")
public class QuoteController {

    private final QuoteCalculationService calculationService;

    public QuoteController(QuoteCalculationService calculationService) {
        this.calculationService = calculationService;
    }

    @PostMapping("/recalculate")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    @Operation(summary = "Ejecuta una corrida de recálculo de cotizaciones",
            description = "Toma un snapshot de estadísticas y calcula una cotización por jugador y estrategia con los "
                    + "datos ya persistidos, sin consultar fuentes externas. Si la estrategia activa se calculó, "
                    + "actualiza el precio de mercado. Corre de forma sincrónica y responde cuando termina. "
                    + "Requiere rol ADMIN: por JWT, o por una API key cuyo dueño es ADMIN (una key sin dueño cuenta "
                    + "como ADMIN).")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false,
            description = "Sin cuerpo o sin strategy se calculan todas las estrategias del catálogo",
            content = @Content(schema = @Schema(implementation = RecalculateRequest.class), examples = {
                    @ExampleObject(name = "todas", value = "{}"),
                    @ExampleObject(name = "official", value = "{\"strategy\":\"OFFICIAL\"}")}))
    @ApiResponse(responseCode = "200", description = "Corrida terminada; puede incluir jugadores fallidos y estrategias salteadas",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = "UUID de trazabilidad"),
            content = @Content(schema = @Schema(implementation = QuoteRunResponse.class)))
    @ApiResponse(responseCode = "400", description = "Código de estrategia inexistente o cuerpo inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(
                    value = "{\"error\":\"unknown_strategy\",\"message\":\"Estrategia de valuación desconocida: 'FOO'. "
                            + "Códigos válidos: OFFICIAL, MOMENTUM\"}")))
    @ApiResponse(responseCode = "401", description = "Sin credenciales, o con credenciales inválidas o vencidas",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(
                    value = "{\"error\":\"unauthorized\",\"message\":\"Falta el header X-API-KEY\"}")))
    @ApiResponse(responseCode = "403", description = "Usuario autenticado sin rol ADMIN",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(
                    value = "{\"error\":\"forbidden\",\"message\":\"No tenés permisos para esta operación\"}")))
    @ApiResponse(responseCode = "409", description = "Ya hay una corrida en curso",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(
                    value = "{\"error\":\"recalculation_in_progress\",\"message\":\"Ya hay un recálculo de cotizaciones en curso\"}")))
    @ApiResponse(responseCode = "500", description = "La corrida no pudo completarse (por ejemplo, falló el snapshot)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(
                    value = "{\"error\":\"recalculation_failed\",\"message\":\"No se pudo completar el recálculo\"}")))
    public QuoteRunResponse recalculate(@RequestBody(required = false) RecalculateRequest request) {
        return QuoteRunResponse.from(calculationService.recalculate(
                Optional.ofNullable(request).map(RecalculateRequest::strategy)));
    }
}
