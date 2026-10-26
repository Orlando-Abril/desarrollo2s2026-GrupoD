package com.example.demo.controller;

import com.example.demo.dto.quote.QuoteRunResponse;
import com.example.demo.dto.quote.RecalculateRequest;
import com.example.demo.dto.quote.PlayerQuoteResponse;
import com.example.demo.dto.quote.RankingPageResponse;
import com.example.demo.model.League;
import com.example.demo.model.Position;
import com.example.demo.exception.InvalidQuoteQueryException;
import com.example.demo.exception.GlobalExceptionHandler.ErrorResponse;
import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.service.valuation.QuoteCalculationService;
import com.example.demo.service.valuation.QuoteQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Arrays;
import java.util.regex.Pattern;

@RestController
@Tag(name = "Quotes", description = "Cotizaciones de jugadores")
public class QuoteController {

    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final DateTimeFormatter STRICT_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final QuoteCalculationService calculationService;
    private final QuoteQueryService queryService;

    public QuoteController(QuoteCalculationService calculationService, QuoteQueryService queryService) {
        this.calculationService = calculationService;
        this.queryService = queryService;
    }

    @GetMapping("/players/ranking")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    @Operation(summary = "Consulta el ranking vigente de jugadores",
            description = "Usa la última cotización de la estrategia efectiva, ordena por valor descendente e id "
                    + "ascendente y calcula el puesto sobre el conjunto filtrado antes de paginar.")
    @ApiResponse(responseCode = "200", description = "Página del ranking",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = "UUID de trazabilidad"),
            content = @Content(schema = @Schema(implementation = RankingPageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Estrategia, filtro o paginación inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Credenciales ausentes, inválidas o vencidas",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public RankingPageResponse ranking(
            @Parameter(description = "Código exacto; ausente usa la estrategia activa", example = "OFFICIAL")
            @RequestParam(required = false) String strategy,
            @Parameter(description = "Liga exacta del dominio", schema = @Schema(allowableValues = {
                    "PREMIER_LEAGUE", "LA_LIGA", "SERIE_A", "BUNDESLIGA", "LIGUE_1"}))
            @RequestParam(required = false) String league,
            @Parameter(description = "Posición incluida entre las del jugador", schema = @Schema(allowableValues = {
                    "GOALKEEPER", "DEFENDER", "MIDFIELDER", "FORWARD"}))
            @RequestParam(required = false) String position,
            @Parameter(description = "Página basada en cero", schema = @Schema(type = "integer", format = "int32",
                    minimum = "0", defaultValue = "0"))
            @RequestParam(required = false) String page,
            @Parameter(description = "Elementos por página", schema = @Schema(type = "integer", format = "int32",
                    minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(required = false) String size,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        int pageNumber = parseInteger("page", page, 0, Integer.MAX_VALUE, pageable.getPageNumber());
        int pageSize = parseInteger("size", size, 1, 100, pageable.getPageSize());
        League parsedLeague = parseEnum("league", league, League.class);
        Position parsedPosition = parseEnum("position", position, Position.class);
        return queryService.ranking(strategy, parsedLeague, parsedPosition, PageRequest.of(pageNumber, pageSize));
    }

    private static int parseInteger(String name, String raw, int min, int max, int defaultValue) {
        if (raw == null) return defaultValue;
        if (raw.isBlank()) throw new InvalidQuoteQueryException(name + " no puede estar vacío");
        try {
            int value = Integer.parseInt(raw);
            if (value < min || value > max) throw new InvalidQuoteQueryException(
                    name + " debe estar entre " + min + " y " + max);
            return value;
        } catch (NumberFormatException ex) {
            throw new InvalidQuoteQueryException(name + " debe ser un entero");
        }
    }

    private static <E extends Enum<E>> E parseEnum(String name, String raw, Class<E> type) {
        if (raw == null) return null;
        if (raw.isBlank()) throw new InvalidQuoteQueryException(name + " no puede estar vacío");
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException ex) {
            throw new InvalidQuoteQueryException(name + " desconocida: '" + raw + "'. Valores válidos: "
                    + String.join(", ", Arrays.stream(type.getEnumConstants()).map(Enum::name).toList()));
        }
    }

    @GetMapping("/players/{id}/quotes")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    @Operation(summary = "Consulta cotizaciones históricas de un jugador",
            description = "Sin at devuelve una lista cronológica; con at devuelve la última cotización vigente "
                    + "al fin UTC de ese día. at no puede combinarse con from ni to. Sin strategy se usa la activa.")
    @ApiResponse(responseCode = "200", description = "Historial sin at, o cotización vigente con at",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = "UUID de trazabilidad"),
            content = @Content(mediaType = "application/json",
                    schema = @Schema(oneOf = {PlayerQuoteResponse[].class, PlayerQuoteResponse.class})))
    @ApiResponse(responseCode = "400", description = "Identificador, estrategia, fecha o combinación inválida",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                    examples = {
                            @ExampleObject(name = "invalidPlayerId",
                                    value = "{\"error\":\"validation_error\",\"message\":\"El id del jugador debe ser mayor a cero\"}"),
                            @ExampleObject(name = "unknownStrategy",
                                    value = "{\"error\":\"unknown_strategy\",\"message\":\"Estrategia de valuación desconocida\"}")
                    }))
    @ApiResponse(responseCode = "401", description = "Sin credenciales, o con credenciales inválidas o vencidas",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Jugador inexistente o sin cotización vigente hasta at",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                    examples = {
                            @ExampleObject(name = "playerNotFound",
                                    value = "{\"error\":\"player_not_found\",\"message\":\"No existe el jugador 999999\"}"),
                            @ExampleObject(name = "quoteNotFound",
                                    value = "{\"error\":\"quote_not_found\",\"message\":\"No hay cotización vigente\"}")
                    }))
    public Object getPlayerQuotes(
            @Parameter(description = "Identificador interno positivo del jugador",
                    schema = @Schema(type = "integer", format = "int64", minimum = "1"))
            @PathVariable("id") Long playerId,
            @Parameter(description = "Código exacto de estrategia; ausente usa la activa", example = "OFFICIAL")
            @RequestParam(required = false) String strategy,
            @Parameter(description = "Primer día UTC incluido; incompatible con at",
                    schema = @Schema(type = "string", format = "date"))
            @RequestParam(required = false) String from,
            @Parameter(description = "Último día UTC incluido; incompatible con at",
                    schema = @Schema(type = "string", format = "date"))
            @RequestParam(required = false) String to,
            @Parameter(description = "Día UTC al final del cual se busca vigencia; incompatible con from y to",
                    schema = @Schema(type = "string", format = "date"))
            @RequestParam(required = false) String at) {
        if (at != null) {
            if (from != null || to != null) {
                throw new InvalidQuoteQueryException("at no puede combinarse con from ni to");
            }
            return queryService.quoteAt(playerId, strategy, parseDate("at", at));
        }
        return queryService.history(playerId, strategy, parseDate("from", from), parseDate("to", to));
    }

    private static LocalDate parseDate(String parameter, String value) {
        if (value == null) {
            return null;
        }
        if (!ISO_DATE.matcher(value).matches()) {
            throw new InvalidQuoteQueryException(parameter + " debe tener formato YYYY-MM-DD");
        }
        try {
            return LocalDate.parse(value, STRICT_DATE);
        } catch (DateTimeParseException ex) {
            throw new InvalidQuoteQueryException(parameter + " debe ser una fecha válida con formato YYYY-MM-DD");
        }
    }

    @PostMapping("/quotes/recalculate")
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
