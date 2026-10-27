# Research: Detalle de un jugador

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

## D1. Composición de la lectura

**Decision**: Implementar `PlayerDetailService` con una transacción de sólo lectura y acceso directo, en orden, a `PlayerRepository.findById`, `PlayerStatsRepository.findById` y `PlayerQuoteRepository.findFirstByPlayerIdAndStrategyCodeOrderByCalculatedAtDescIdDesc`, resolviendo antes el código mediante `ValuationStrategyCatalog.active()`.

**Rationale**: Es la responsabilidad solicitada, usa consultas existentes, conserva el desempate fecha/id del historial y limita la feature a datos locales. Buscar primero el jugador permite producir 404 antes de consultar datos complementarios.

**Alternatives considered**: Reutilizar `QuoteQueryService`, descartado porque agrega responsabilidades de historial, fechas, ranking y caché; crear una consulta SQL agregada, descartado porque duplica reglas y no es necesario para tres lecturas por PK/índice.

## D2. Forma y ubicación de los DTOs

**Decision**: Crear tres records públicos en `dto/player`: `PlayerDetailResponse(PlayerResponse player, PlayerStatsResponse stats, CurrentQuoteResponse quote)`, más los dos tipos anidados en JSON. `stats` y `quote` son campos requeridos pero nullable; cada tipo tiene un esquema OpenAPI estable y una factory desde su entidad.

**Rationale**: Reutiliza `PlayerResponse` sin modificarlo, distingue sección ausente de campos omitidos y da a OpenAPI nombres explícitos. El frontend obtiene una estructura previsible `player`/`stats`/`quote`.

**Alternatives considered**: Aplanar todos los campos, que colisiona semánticamente y dificulta la nulabilidad; modificar `PlayerResponse`, prohibido; reutilizar `PlayerQuoteResponse`, descartado porque el usuario pidió `CurrentQuoteResponse` dentro del área de jugador y el nombre histórico no expresa la semántica vigente.

## D3. Mapping 404 compartido

**Decision**: Crear un `PlayerDetailExceptionHandler` nuevo con `assignableTypes = PlayerController.class` y `@Order(Ordered.HIGHEST_PRECEDENCE)`. Mantener exactamente el 404 `ErrorResponse("player_not_found", ex.getMessage())` para `PlayerNotFoundException` y mapear `InvalidQuoteQueryException` a 400 `ErrorResponse("validation_error", ex.getMessage())` para cero y negativos. No modificar `QuoteExceptionHandler`, que conserva los mismos mappings para `QuoteController`.

**Rationale**: El mapping actual no alcanza a `PlayerController` porque el advice está restringido a `QuoteController`. Un advice focalizado reproduce el contrato sin tocar `GlobalExceptionHandler`, sin ampliar los handlers de quotes y sin alterar su comportamiento existente.

**Alternatives considered**: Agregar `PlayerController` al alcance completo de `QuoteExceptionHandler`, descartado porque también cambiaría quién maneja type mismatches y otros errores del listado; mover el mapping existente, descartado para mantener `QuoteExceptionHandler` intacto; anotar la excepción con estado, insuficiente para conservar el body `player_not_found`; capturarla en el controller, descartado por mezclar traducción de errores y flujo normal.

## D4. Conversión y semántica de `id`

**Decision**: Declarar `@PathVariable Long id` y delegar a Spring el texto no numérico y el overflow de `Long`, que el `GlobalExceptionHandler` existente traduce a 400 `validation_error`. Rechazar además cero y negativos con 400 y `El id del jugador debe ser mayor a cero`; sólo un `Long` positivo sin jugador produce 404 `player_not_found`.

**Rationale**: Alinea el detalle con `GET /players/{id}/quotes` y separa de forma determinista ids mal formados/no positivos de jugadores positivos inexistentes, sin modificar el handler protegido.

**Alternatives considered**: Recibir `String` y parsear manualmente, redundante; tratar cero o negativos como jugador inexistente, descartado por inconsistencia con el endpoint de historial.

## D5. Coexistencia de mappings

**Decision**: Mantener `GET /{id}` bajo el `@RequestMapping("/players")` existente. Verificar en un `@SpringBootTest` MockMvc que `/players/ranking` sigue seleccionando el mapping literal de `QuoteController`, `/players/{id}/quotes` sigue seleccionando el mapping de tres segmentos y `/players/{id}` selecciona el nuevo método.

**Rationale**: Spring MVC prioriza patrones más específicos, pero una prueba con el contexto real detecta mappings ambiguos, errores de conversión accidentales y regresiones de shape.

**Alternatives considered**: Mover ranking o historial, descartado porque cambiaría contratos existentes; regex en `{id}`, innecesaria y menos clara que la conversión tipada más la prueba de rutas.

## D6. Seguridad y OpenAPI

**Decision**: Reutilizar la regla `anyRequest().authenticated()` y las dos alternativas existentes. Documentar 200/400/401/404, correlación, el parámetro int64 y los tres esquemas mediante anotaciones; ampliar `PlayerOpenApiTest` para inspeccionar `/v3/api-docs`.

**Rationale**: No se necesita modificar `SecurityConfig`; API key y JWT ya son filtros alternativos. Probar el JSON generado evita confundir anotaciones presentes con contrato realmente publicado.

**Alternatives considered**: Agregar un matcher de seguridad específico, innecesario y más frágil; documentar sólo a nivel de clase, insuficiente para errores, nulabilidad y response schema.

## D7. Datos locales, caché y esquema

**Decision**: No agregar tablas, columnas, índices ni caché. La operación lee por las claves de `players` y `player_stats` y por la consulta indexada existente `(player_id, strategy_code, calculated_at)` de `player_quotes`. No se inyectan adapters ni clientes externos.

**Rationale**: La escala prevista y el objetivo de menos de un segundo se cubren con tres consultas indexadas. La infraestructura Redis constitucional continúa disponible para los casos que ya la usan, pero no es necesaria para garantizar resiliencia de una lectura totalmente local.

**Alternatives considered**: Cachear el agregado, descartado por invalidación cruzada de jugador/stats/quotes fuera del alcance; agregar un join/proyección nativa, descartado por complejidad sin evidencia de necesidad.

## D8. Estrategia de pruebas

**Decision**: Crear `PlayerDetailControllerTest` con contexto real, MockMvc, `@UnitTestProfile` y datos H2 persistidos. Cubrir respuestas completas/nulas, selección activa, 400/401/404, JWT/API key y coexistencia de rutas. Ampliar `PlayerOpenApiTest` y adaptar sólo la construcción del controller en `PlayerControllerTest`.

**Rationale**: Una prueba integrada cubre controller, service, repositories, handler, seguridad y routing. El perfil local evita servicios reales y hace deterministas las fixtures.

**Alternatives considered**: Sólo `standaloneSetup`, que no prueba seguridad, advice discovery ni colisiones entre controllers; nuevo test de repositorio, redundante con la cobertura existente de `PlayerQuoteRepository` y `PlayerStatsRepository`.
