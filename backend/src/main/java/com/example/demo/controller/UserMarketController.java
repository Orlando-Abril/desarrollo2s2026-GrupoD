package com.example.demo.controller;

import com.example.demo.dto.market.PortfolioResponse;
import com.example.demo.dto.market.TransactionPageResponse;
import com.example.demo.service.market.PortfolioService;
import com.example.demo.exception.GlobalExceptionHandler.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/users")
@Validated
@Tag(name = "User Market", description = "Portfolio e historial financiero por cuenta")
public class UserMarketController {

    private final PortfolioService portfolioService;

    public UserMarketController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/{id}/portfolio")
    @Operation(summary = "Consultar el portfolio valuado",
            description = "Devuelve saldo, reservas, posiciones, costo promedio y valuación vigente de la cuenta. "
                    + "Sin strategy la respuesta no incluye strategyValuation. Con strategy, cada posición y los "
                    + "totales agregan strategyValuation, valuado con la última cotización de esa estrategia "
                    + "cualquiera sea su estado; si un jugador no tiene cotización, sus campos y los totales de "
                    + "strategyValuation son null. La estrategia se valida antes que el acceso a la cuenta.",
            security = {@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "apiKeyAuth")})
    @ApiResponse(responseCode = "200", description = "Portfolio actual",
            headers = @Header(name = "X-Correlation-ID", ref = "#/components/headers/CorrelationId"),
            content = @Content(schema = @Schema(implementation = PortfolioResponse.class)))
    @ApiResponse(responseCode = "400", description = "Id inválido o estrategia inexistente (unknown_strategy)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(
                    name = "unknownStrategy", value = "{\"error\":\"unknown_strategy\",\"message\":"
                    + "\"Estrategia de valuación desconocida: 'FOO'. Códigos válidos: OFFICIAL, MOMENTUM\"}")))
    @ApiResponse(responseCode = "401", description = "Credencial ausente o inválida",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "La cuenta no pertenece al usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Usuario inexistente",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "500", description = "Datos financieros inconsistentes",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public PortfolioResponse portfolio(
            @Parameter(description = "Id de la cuenta", required = true,
                    schema = @Schema(minimum = "1")) @PathVariable @Min(1) Long id,
            @Parameter(description = "Código exacto de estrategia para la valuación comparativa (spec 031)",
                    example = "MOMENTUM")
            @RequestParam(required = false) String strategy,
            Authentication authentication) {
        if (strategy == null) {
            return portfolioService.portfolio(id, authentication);
        }
        return portfolioService.portfolio(id, authentication, Optional.of(strategy));
    }

    @GetMapping("/{id}/transactions")
    @Operation(summary = "Consultar el historial de operaciones",
            description = "Devuelve cada calce ejecutado individual como COMPRA o VENTA, nunca órdenes abiertas o completas.",
            security = {@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "apiKeyAuth")})
    @ApiResponse(responseCode = "200", description = "Página de operaciones",
            headers = @Header(name = "X-Correlation-ID", ref = "#/components/headers/CorrelationId"),
            content = @Content(schema = @Schema(implementation = TransactionPageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Id o paginación inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Credencial ausente o inválida",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "La cuenta no pertenece al usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Usuario inexistente",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TransactionPageResponse transactions(
                                                @Parameter(description = "Id de la cuenta", required = true,
                                                        schema = @Schema(minimum = "1"))
                                                @PathVariable @Min(1) Long id,
                                                @Parameter(description = "Página base cero",
                                                        schema = @Schema(type = "integer", format = "int32",
                                                                defaultValue = "0", minimum = "0"))
                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                @Parameter(description = "Elementos por página",
                                                        schema = @Schema(type = "integer", format = "int32",
                                                                defaultValue = "20", minimum = "1",
                                                                maximum = "100"))
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                                Authentication authentication) {
        return portfolioService.transactions(id, authentication, page, size);
    }
}
