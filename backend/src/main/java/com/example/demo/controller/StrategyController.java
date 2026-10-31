package com.example.demo.controller;

import com.example.demo.dto.strategy.ActiveStrategyResponse;
import com.example.demo.dto.strategy.ChangeActiveStrategyRequest;
import com.example.demo.exception.GlobalExceptionHandler.ErrorResponse;
import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.service.valuation.ActiveStrategyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/strategies")
@Tag(name = "Valuation Strategies", description = "Estrategia activa y alternativa de valuación")
public class StrategyController {

    private final ActiveStrategyService activeStrategyService;

    public StrategyController(ActiveStrategyService activeStrategyService) {
        this.activeStrategyService = activeStrategyService;
    }

    @GetMapping("/active")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    @Operation(summary = "Consulta la estrategia activa, la alternativa y la que fijó el precio de mercado",
            description = "Disponible para cualquier usuario autenticado. pendingRecalculation es true cuando la "
                    + "estrategia activa cambió y el precio de mercado vigente todavía lo fijó la anterior.")
    @ApiResponse(responseCode = "200", description = "Configuración vigente",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = "UUID de trazabilidad"),
            content = @Content(schema = @Schema(implementation = ActiveStrategyResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sin credenciales, o con credenciales inválidas o vencidas",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ActiveStrategyResponse active() {
        return ActiveStrategyResponse.from(activeStrategyService.view(), null);
    }

    @PutMapping("/active")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    @Operation(summary = "Cambia la estrategia activa global (sólo ADMIN)",
            description = "Persiste la elección y la audita (ACTIVE_STRATEGY_CHANGED). No modifica el marketValue "
                    + "ni dispara un recálculo: la usa el próximo recálculo que comience después del cambio. "
                    + "Activar la estrategia que ya está activa responde 200 con changed=false y no audita. "
                    + "Requiere rol ADMIN: por JWT, o por una API key cuyo dueño es ADMIN (una key sin dueño cuenta "
                    + "como ADMIN).")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(schema = @Schema(implementation = ChangeActiveStrategyRequest.class),
                    examples = @ExampleObject(value = "{\"strategy\":\"MOMENTUM\"}")))
    @ApiResponse(responseCode = "200", description = "Configuración resultante",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = "UUID de trazabilidad"),
            content = @Content(schema = @Schema(implementation = ActiveStrategyResponse.class)))
    @ApiResponse(responseCode = "400", description = "Estrategia inexistente o cuerpo inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(
                    value = "{\"error\":\"unknown_strategy\",\"message\":\"Estrategia de valuación desconocida: "
                            + "'FOO'. Códigos válidos: OFFICIAL, MOMENTUM\"}")))
    @ApiResponse(responseCode = "401", description = "Sin credenciales, o con credenciales inválidas o vencidas",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Usuario autenticado sin rol ADMIN",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(
                    value = "{\"error\":\"forbidden\",\"message\":\"No tenés permisos para esta operación\"}")))
    public ActiveStrategyResponse change(@Valid @RequestBody ChangeActiveStrategyRequest request,
                                         Authentication authentication) {
        boolean changed = activeStrategyService.change(request.strategy(), authentication);
        return ActiveStrategyResponse.from(activeStrategyService.view(), changed);
    }
}
