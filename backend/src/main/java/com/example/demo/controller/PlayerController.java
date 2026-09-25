package com.example.demo.controller;

import com.example.demo.dto.player.PlayerResponse;
import com.example.demo.dto.player.PlayerSyncResponse;
import com.example.demo.exception.GlobalExceptionHandler.ErrorResponse;
import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.model.League;
import com.example.demo.model.Position;
import com.example.demo.service.PlayerCatalogQueryService;
import com.example.demo.service.PlayerCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/players")
@Validated
@Tag(name = "Players", description = "Catálogo local sincronizado desde Football-Data.org")
@SecurityRequirement(name = "apiKeyAuth")
public class PlayerController {
    private final PlayerCatalogQueryService queryService;
    private final PlayerCatalogService catalogService;

    public PlayerController(PlayerCatalogQueryService queryService, PlayerCatalogService catalogService) {
        this.queryService = queryService;
        this.catalogService = catalogService;
    }

    @PostMapping("/sync")
    @Operation(summary = "Cargar o actualizar el catálogo",
            description = "Ejecuta una carga síncrona de las 5 ligas desde Football-Data.org: crea los jugadores "
                    + "nuevos y actualiza los existentes por externalId, sin borrar ninguno. Las fallas por liga "
                    + "o por jugador no interrumpen la carga y se informan en el resumen.")
    @ApiResponse(responseCode = "200", description = "Carga ejecutada; el resultado viaja en status",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = "UUID de trazabilidad"),
            content = @Content(schema = @Schema(implementation = PlayerSyncResponse.class), examples = {
                    @ExampleObject(name = "completed",
                            value = "{\"status\":\"COMPLETED\",\"processed\":2649,\"failedLeagues\":0,\"failedPlayers\":0}"),
                    @ExampleObject(name = "partial",
                            value = "{\"status\":\"PARTIAL_FAILURE\",\"processed\":2110,\"failedLeagues\":1,\"failedPlayers\":0}"),
                    @ExampleObject(name = "failed",
                            value = "{\"status\":\"FAILED\",\"processed\":0,\"failedLeagues\":5,\"failedPlayers\":0}")}))
    @ApiResponse(responseCode = "401", description = "API key ausente, inválida o revocada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public PlayerSyncResponse sync() {
        PlayerCatalogService.SyncResult result = catalogService.synchronizeCatalog();
        return new PlayerSyncResponse(result.status(), result.processed(), result.failedLeagues(),
                result.failedPlayers());
    }

    @GetMapping
    @Operation(summary = "Listar jugadores", description = "Combina los filtros presentes con AND y nunca llama a la fuente externa. "
            + "Devuelve una lista vacía si todavía no se cargó el catálogo.")
    @ApiResponse(responseCode = "200", description = "Lista filtrada, posiblemente vacía",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = "UUID de trazabilidad"),
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = PlayerResponse.class))))
    @ApiResponse(responseCode = "400", description = "Filtro inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "API key ausente, inválida o revocada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<PlayerResponse> list(
            @Parameter(description = "Liga exacta del dominio") @RequestParam(required = false) League league,
            @Parameter(description = "Equipo exacto, sin distinguir mayúsculas")
            @RequestParam(required = false) @Pattern(regexp = ".*\\S.*", message = "team no puede estar vacío") String team,
            @Parameter(description = "Posición incluida en el conjunto del jugador") @RequestParam(required = false) Position position) {
        return queryService.findPlayers(league, team, position);
    }
}
