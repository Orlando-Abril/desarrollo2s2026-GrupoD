package com.example.demo.controller;

import com.example.demo.dto.market.OrderBookResponse;
import com.example.demo.dto.market.OrderRequest;
import com.example.demo.dto.market.OrderResponse;
import com.example.demo.exception.GlobalExceptionHandler.ErrorResponse;
import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.service.market.OrderResult;
import com.example.demo.service.market.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/orders")
@Validated
@Tag(name = "Órdenes", description = "Libro de órdenes LIMIT y MARKET con calce al crear la orden")
@SecurityRequirement(name = "apiKeyAuth")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private static final String VALIDATION_DESCRIPTION = "validation_error (datos inválidos, LIMIT sin precio o MARKET "
            + "con precio) o price_out_of_band (precio límite fuera de ±20% de la cotización vigente)";

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/buy")
    @Operation(summary = "Comprar tokens",
            description = "Crea una orden de compra a nombre del usuario autenticado y la calza en el momento contra "
                    + "las ventas del libro: primero el mejor precio y, a igual precio, la más vieja. Cada calce se "
                    + "ejecuta al precio de la orden que ya estaba en el libro. Lo que una MARKET no llena se cancela; "
                    + "lo que una LIMIT no llena queda en el libro reservando créditos.")
    @ApiResponse(responseCode = "201", description = "Orden creada; el resultado del calce viaja en status y trades",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = "UUID de trazabilidad"),
            content = @Content(schema = @Schema(implementation = OrderResponse.class)))
    @ApiResponse(responseCode = "400", description = VALIDATION_DESCRIPTION,
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Credenciales ausentes o inválidas",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "superuser_not_allowed o user_required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "player_not_found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "insufficient_balance o player_not_quoted",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<OrderResponse> buy(@Valid @RequestBody OrderRequest request, Authentication authentication) {
        return created(orderService.placeBuy(authentication.getName(), request));
    }

    @PostMapping("/sell")
    @Operation(summary = "Vender tokens",
            description = "Crea una orden de venta a nombre del usuario autenticado y la calza en el momento contra "
                    + "las compras del libro: primero el mejor precio y, a igual precio, la más vieja. Cada calce se "
                    + "ejecuta al precio de la orden que ya estaba en el libro. El superusuario no compra: sin "
                    + "compradores, lo que una MARKET no llena se cancela y lo que una LIMIT no llena queda en el "
                    + "libro reservando tokens.")
    @ApiResponse(responseCode = "201", description = "Orden creada; el resultado del calce viaja en status y trades",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = "UUID de trazabilidad"),
            content = @Content(schema = @Schema(implementation = OrderResponse.class)))
    @ApiResponse(responseCode = "400", description = VALIDATION_DESCRIPTION,
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Credenciales ausentes o inválidas",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "superuser_not_allowed o user_required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "player_not_found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "insufficient_tokens o player_not_quoted",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<OrderResponse> sell(@Valid @RequestBody OrderRequest request, Authentication authentication) {
        return created(orderService.placeSell(authentication.getName(), request));
    }

    @GetMapping("/book")
    @Operation(summary = "Ver el libro de un jugador",
            description = "Precios de venta (de menor a mayor) y de compra (de mayor a menor) con la cantidad pendiente "
                    + "total en cada uno, y la cotización vigente. No identifica a los dueños de las órdenes.")
    @ApiResponse(responseCode = "200", description = "Libro agregado por precio; lados vacíos como []",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = "UUID de trazabilidad"),
            content = @Content(schema = @Schema(implementation = OrderBookResponse.class)))
    @ApiResponse(responseCode = "400", description = "validation_error: falta playerId o no es numérico",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Credenciales ausentes o inválidas",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "player_not_found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public OrderBookResponse book(@Parameter(description = "Id del jugador", example = "42")
                                  @RequestParam Long playerId) {
        return orderService.book(playerId);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancelar una orden propia",
            description = "Cancela una orden abierta o parcialmente ejecutada del usuario autenticado, la saca del "
                    + "libro y libera los créditos o tokens que tenía reservados. Lo ya ejecutado se conserva.")
    @ApiResponse(responseCode = "200", description = "Orden cancelada; trades va vacío y averagePrice resume lo ejecutado",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = "UUID de trazabilidad"),
            content = @Content(schema = @Schema(implementation = OrderResponse.class)))
    @ApiResponse(responseCode = "400", description = "validation_error: id inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Credenciales ausentes o inválidas",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403",
            description = "order_forbidden (orden de otro usuario), superuser_not_allowed o user_required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "order_not_found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "order_closed: la orden ya está ejecutada o cancelada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public OrderResponse cancel(@PathVariable Long id, Authentication authentication) {
        OrderResult result = orderService.cancel(authentication.getName(), id);
        return OrderResponse.of(result.order(), result.trades(), List.of());
    }

    private static ResponseEntity<OrderResponse> created(OrderResult result) {
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.of(result.order(), result.trades()));
    }
}
